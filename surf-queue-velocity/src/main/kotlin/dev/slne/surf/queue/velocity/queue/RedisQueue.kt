package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.queue.velocity.queue.display.QueueDisplay
import dev.slne.surf.redis.libs.redisson.api.BatchOptions
import dev.slne.surf.redis.libs.redisson.api.RLock
import dev.slne.surf.redis.libs.redisson.api.RMap
import dev.slne.surf.redis.libs.redisson.api.RScoredSortedSet
import dev.slne.surf.redis.libs.redisson.client.codec.StringCodec
import dev.slne.surf.surfapi.core.api.util.logger
import it.unimi.dsi.fastutil.objects.Object2IntMap
import kotlinx.coroutines.future.await
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes

class RedisQueue(val serverName: String) {
    private val scoredSet: RScoredSortedSet<String> = redisApi.redisson.getScoredSortedSet(
        RedisInstance.namespaced("queue:$serverName:entries"),
        StringCodec.INSTANCE
    )

    private val metaMap: RMap<String, QueueEntry> = redisApi.redisson.getMap(
        RedisInstance.namespaced("queue:$serverName:meta"),
    )

    private val lastSeenMap: RMap<String, Long> = redisApi.redisson.getMap(
        RedisInstance.namespaced("queue:$serverName:lastseen"),
    )

    private val transferLock: RLock =
        redisApi.redisson.getLock(RedisInstance.namespaced("queue:$serverName:transfer-lock"))

    private val epochMsAtomic =
        redisApi.redisson.getBucket<Long>(RedisInstance.namespaced("queue:$serverName:epoch-ms"))

    private val epochMs: Long

    private val tickCount = AtomicLong(0)

    val display = QueueDisplay(this)

    init {
        epochMsAtomic.setIfAbsent(Instant.now().toEpochMilli())
        epochMs = epochMsAtomic.get()
    }

    companion object {
        private val log = logger()

        val GRACE_PERIOD_MS = 1.minutes.inWholeMilliseconds
        const val LOCK_LEASE_SECONDS = 30L

        private fun fixPriority(uuid: UUID, priority: Int): Int {
            return if (priority <= RedisQueueScorePacker.MAX_PRIORITY) {
                priority
            } else {
                log.atWarning()
                    .log(
                        "Priority %d for %s exceeds max representable priority, capping to %d",
                        priority,
                        uuid,
                        RedisQueueScorePacker.MAX_PRIORITY
                    )

                RedisQueueScorePacker.MAX_PRIORITY
            }
        }

        private fun getInMemoryAtomicBatchOptions(): BatchOptions {
            return BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC)
        }
    }

    suspend fun enqueue(uuid: UUID, priority: Int) {
        val priority = fixPriority(uuid, priority)
        val now = Instant.now().toEpochMilli()
        val uuidString = uuid.toString()

        val score = RedisQueueScorePacker.pack(
            priority,
            now - epochMs,
            0
        ) // TODO: set sequence if it happens to enqueue multiple times in the same ms


        val meta = QueueEntry(uuid, now, priority)

        val batch = redisApi.redisson.createBatch(getInMemoryAtomicBatchOptions())

        batch.getScoredSortedSet<String>(scoredSet.name, scoredSet.codec)
            .addAsync(score, uuidString)
        batch.getMap<String, QueueEntry>(metaMap.name, metaMap.codec)
            .putAsync(uuidString, meta)

        batch.executeAsync().await()

        log.atInfo()
            .log("Enqueued %s in queue %s with priority %d", uuid, serverName, priority)
    }

    suspend fun dequeue(uuid: UUID) {
        val uuidString = uuid.toString()

        val batch = redisApi.redisson.createBatch(getInMemoryAtomicBatchOptions())

        batch.getScoredSortedSet<String>(scoredSet.name, scoredSet.codec)
            .removeAsync(uuidString)
        batch.getMap<String, QueueEntry>(metaMap.name, metaMap.codec)
            .removeAsync(uuidString)
        batch.getMap<String, Long>(lastSeenMap.name, lastSeenMap.codec)
            .removeAsync(uuidString)

        batch.executeAsync().await()
    }

    suspend fun isQueued(uuid: UUID): Boolean {
        return scoredSet.containsAsync(uuid.toString()).await()
    }

    suspend fun getPosition(uuid: UUID): Int? {
        val rank = scoredSet.rankAsync(uuid.toString()).await()
        return rank?.plus(1)
    }

    suspend fun getAllUuidsWithPosition(): Collection<Object2IntMap.Entry<UUID>> {
        return scoredSet.entryRangeAsync(0, -1)
            .await()
            .mapIndexedNotNull { index, entry ->
                val uuid = runCatching { UUID.fromString(entry.value) }.getOrNull()
                uuid?.let { Object2IntMap.entry(it, index + 1) }
            }
    }

    suspend fun size(): Int {
        return scoredSet.sizeAsync().await()
    }

    suspend fun processTransfers(
        maxTransfers: Int = 5,
        tryTransfer: suspend (QueueEntry) -> TransferAction
    ): Int {
        val threadId = Thread.currentThread().threadId()
        val acquired = transferLock.tryLockAsync(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS, threadId).await()
        if (!acquired) return 0

        try {
            return doProcessTransfers(maxTransfers, tryTransfer) { transferLock.isHeldByThreadAsync(threadId).await() }
        } finally {
            try {
                transferLock.unlockAsync().await()
            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Failed to unlock transfer lock for %s", serverName)
            }
        }
    }

    private suspend fun doProcessTransfers(
        maxTransfers: Int,
        tryTransfer: suspend (QueueEntry) -> TransferAction,
        isLocked: suspend () -> Boolean
    ): Int {
        var transferred = 0
        while (transferred < maxTransfers) {
            val topEntries = scoredSet.entryRangeAsync(0, 0).await()
            val topEntry = topEntries.firstOrNull() ?: break

            val uuidString = topEntry.value
            val uuid = try {
                UUID.fromString(uuidString)
            } catch (e: Exception) {
                // Corrupted entry - remove and continue
                scoredSet.removeAsync(uuidString).await()
                metaMap.removeAsync(uuidString).await()
                lastSeenMap.removeAsync(uuidString).await()
                continue
            }

            val entry = metaMap.getAsync(uuidString).await()
            if (entry == null) {
                // No metadata - remove and continue
                scoredSet.removeAsync(uuidString).await()
                lastSeenMap.removeAsync(uuidString).await()
                continue
            }

            try {
                when (val result = tryTransfer(entry)) {
                    TransferAction.DONE -> {
                        dequeue(uuid)
                        transferred++
                        log.atInfo()
                            .log("Transferred %s to %s", uuid, serverName)
                    }

                    TransferAction.PLAYER_NOT_FOUND -> handlePlayerNotFound(uuidString, uuid, entry)
                    TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER -> handlePlayerNotFound(uuidString, uuid, entry)

                    TransferAction.PLAYER_ALREADY_ON_SERVER -> {
                        dequeue(uuid)
                        log.atInfo()
                            .log("Player %s is already on server %s", uuid, serverName)
                    }

                    TransferAction.PLUGIN_CANCELLED_TRANSFER -> {
                        skipEntry(uuidString, entry)
                        log.atInfo()
                            .log("Plugin cancelled transfer for %s", uuid)
                    }

                    TransferAction.PLAYER_KICKED_FROM_SERVER -> {
                        skipEntry(uuidString, entry)
                        log.atInfo()
                            .log("Player %s kicked from server %s", uuid, serverName)
                    }

                    TransferAction.SERVER_FULL -> {
                        log.atInfo()
                            .log("Server %s is full, stopping transfers", serverName)
                        break
                    }

                    TransferAction.PLAYER_ALREADY_CONNECTING -> {
                        skipEntry(uuidString, entry)
                        log.atInfo()
                            .log("Player %s is already connecting to server %s", uuid, serverName)
                    }

                    TransferAction.SERVER_NOT_FOUND -> {
                        log.atInfo()
                            .log("Server %s not found, skipping", serverName)
                        break
                    }

                    TransferAction.ERROR -> {
                        log.atWarning()
                            .log("Error transferring %s, skipping", uuid)
                        skipEntry(uuidString, entry)
                    }
                }
            } catch (_: AbortException) {
                break
            }

            // check if we are still in the lock
            if (!isLocked()) break
        }

        return transferred
    }

    private suspend fun handlePlayerNotFound(uuidString: String, uuid: UUID, entry: QueueEntry) {
        val now = Instant.now().toEpochMilli()
        val lastSeen = lastSeenMap.getAsync(uuidString).await()

        if (lastSeen == null) {
            // First time noticing the player is gone - apply grace period
            lastSeenMap.putAsync(uuidString, now).await()
            skipEntry(uuidString, entry)
            log.atInfo()
                .log(
                    "Player %s not found, starting %dms grace period",
                    uuid,
                    GRACE_PERIOD_MS
                )
        } else if (now - lastSeen < GRACE_PERIOD_MS) {
            // Still in grace period - skip for now
            skipEntry(uuidString, entry)
            log.atFine()
                .log(
                    "Player %s still in grace period (%dms remaining)",
                    uuid,
                    GRACE_PERIOD_MS - (now - lastSeen)
                )
        } else {
            // Grace period expired - remove from queue
            dequeue(uuid)
            log.atInfo()
                .log(
                    "Player %s removed from queue %s (offline > %dms)",
                    uuid,
                    serverName,
                    GRACE_PERIOD_MS
                )
        }
    }

    private suspend fun skipEntry(uuidString: String, meta: QueueEntry) { // TODO: Max retries
        val scores = scoredSet.entryRangeAsync(0, 1).await()
        if (scores.size < 2) {
            throw AbortException() // No next entry to skip behind, so we can't skip - abort further processing to avoid busy loop
        }

        val nextScoreRaw = scores.last().score
        val nextScore = RedisQueueScorePacker.unpack(nextScoreRaw)

        var nextSequence = nextScore.sequence + 1
        if (nextSequence > RedisQueueScorePacker.MAX_SEQUENCE) {
            nextSequence = nextScore.sequence
        }

        val newScore = RedisQueueScorePacker.pack(
            meta.priority,
            if (nextSequence == nextScore.sequence) nextScore.deltaMs + 1 else nextScore.deltaMs,
            nextSequence
        )

        scoredSet.addAsync(newScore, uuidString).await()
    }

    suspend fun markPlayerReconnected(uuid: UUID) {
        lastSeenMap.removeAsync(uuid.toString()).await()
    }

    fun getTickCount() = tickCount.get()

    suspend fun tickSecond() {
        val ticks = tickCount.incrementAndGet()
        if (ticks % 30 == 0L) {
            try {
                cleanupExpiredEntries()
            } catch (e: Exception) {
                log.atWarning()
                    .withCause(e)
                    .log("Failed to cleanup expired entries for queue %s", serverName)
            }
        }

        try {
            display.tick()
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to tick display for queue %s", serverName)
        }
    }

    suspend fun cleanupExpiredEntries() {
        val now = Instant.now().toEpochMilli()
        val allLastSeen = lastSeenMap.readAllMapAsync().await() ?: return

        try {
            for ((uuidString, lastSeenTime) in allLastSeen) {
                if (now - lastSeenTime >= GRACE_PERIOD_MS) {
                    try {
                        val uuid = UUID.fromString(uuidString)
                        dequeue(uuid)
                        log.atInfo()
                            .log("Cleanup: removed expired entry %s from queue %s", uuid, serverName)
                    } catch (_: Exception) {
                        lastSeenMap.removeAsync(uuidString).await()
                        scoredSet.removeAsync(uuidString).await()
                        metaMap.removeAsync(uuidString).await()
                    }
                }
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to cleanup expired entries for queue %s", serverName)
        }
    }

    suspend fun delete() {
        scoredSet.deleteAsync().await()
        metaMap.deleteAsync().await()
        lastSeenMap.deleteAsync().await()
    }

    enum class TransferAction {
        DONE,
        PLAYER_NOT_FOUND,
        PLAYER_NOT_CONNECTED_TO_A_SERVER,
        PLAYER_ALREADY_ON_SERVER,
        PLUGIN_CANCELLED_TRANSFER,
        PLAYER_KICKED_FROM_SERVER,
        SERVER_FULL,
        PLAYER_ALREADY_CONNECTING,
        SERVER_NOT_FOUND,
        ERROR,
    }

    private class AbortException : Exception()
}