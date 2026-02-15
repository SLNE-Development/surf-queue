package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.queue.codec.QueueEntryCodec
import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.codec.UUIDCodec
import dev.slne.surf.redis.libs.redisson.api.BatchOptions
import dev.slne.surf.redis.libs.redisson.client.codec.LongCodec
import dev.slne.surf.redis.libs.redisson.client.protocol.ScoredEntry
import dev.slne.surf.redis.libs.redisson.codec.CompositeCodec
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.Blocking
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

class RedisQueueStore(private val keys: RedisQueueKeys) {
    private val scoredSet = redisApi.redisson.getScoredSortedSet<UUID>(keys.entriesKey, UUIDCodec.INSTANCE)
    private val metaMap = redisApi.redisson.getMap<UUID, QueueEntry>(
        keys.metaKey,
        CompositeCodec(UUIDCodec.INSTANCE, QueueEntryCodec())
    )
    private val lastSeenMap = redisApi.redisson.getMap<UUID, Long>(
        keys.lastSeenKey,
        CompositeCodec(UUIDCodec.INSTANCE, LongCodec.INSTANCE)
    )

    private val transferLock = redisApi.redisson.getLock(keys.transferLockKey)
    private val epochMsBucket = redisApi.redisson.getBucket<Long>(keys.epochMsKey, LongCodec.INSTANCE)


    companion object {
        val REDIS_QUEUE_PREFIX = RedisInstance.namespaced("queue:")

        private fun atomicBatchOptions(): BatchOptions {
            return BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC)
        }
    }

    @Blocking
    fun initEpochMs(): Long {
        epochMsBucket.setIfAbsent(Instant.now().toEpochMilli())
        return epochMsBucket.get()
    }

    suspend fun enqueue(uuid: UUID, meta: QueueEntry, score: Double): Boolean {
        val batch = redisApi.redisson.createBatch(atomicBatchOptions())

        val addAsync = batch.getScoredSortedSet<UUID>(scoredSet.name, scoredSet.codec)
            .addAsync(score, uuid)
        batch.getMap<UUID, QueueEntry>(metaMap.name, metaMap.codec)
            .putAsync(uuid, meta)

        batch.executeAsync().await()
        return addAsync.await()
    }

    suspend fun dequeue(uuid: UUID): Boolean {
        val batch = redisApi.redisson.createBatch(atomicBatchOptions())

        val removeAsync = batch.getScoredSortedSet<UUID>(scoredSet.name, scoredSet.codec)
            .removeAsync(uuid)
        batch.getMap<UUID, QueueEntry>(metaMap.name, metaMap.codec)
            .removeAsync(uuid)
        batch.getMap<UUID, Long>(lastSeenMap.name, lastSeenMap.codec)
            .removeAsync(uuid)

        batch.executeAsync().await()
        return removeAsync.await()
    }

    suspend fun isQueued(uuid: UUID): Boolean = scoredSet.containsAsync(uuid).await()
    suspend fun rank(uuid: UUID): Int? = scoredSet.rankAsync(uuid).await()
    suspend fun size(): Int = scoredSet.sizeAsync().await()

    suspend fun top1(): UUID? = scoredSet.entryRangeAsync(0, 0).await().firstOrNull()?.value
    suspend fun top2(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, 1).await()
    suspend fun readAllEntries(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, -1).await()

    suspend fun getMeta(uuid: UUID): QueueEntry? = metaMap.getAsync(uuid).await()

    suspend fun removeAllFor(uuid: UUID) {
        scoredSet.removeAsync(uuid).await()
        metaMap.removeAsync(uuid).await()
        lastSeenMap.removeAsync(uuid).await()
    }

    suspend fun putLastSeen(uuid: UUID, nowMs: Long) {
        lastSeenMap.putAsync(uuid, nowMs).await()
    }

    suspend fun getLastSeen(uuid: UUID): Long? = lastSeenMap.getAsync(uuid).await()

    suspend fun clearLastSeen(uuid: UUID) {
        lastSeenMap.removeAsync(uuid).await()
    }

    suspend fun readAllLastSeen(): Map<UUID, Long> = lastSeenMap.readAllMapAsync().await() ?: emptyMap()

    suspend fun addOrUpdateScore(uuid: UUID, score: Double): Boolean {
        return scoredSet.addAsync(score, uuid).await()
    }

    suspend fun deleteAll() {
        scoredSet.deleteAsync().await()
        metaMap.deleteAsync().await()
        lastSeenMap.deleteAsync().await()
    }

    suspend fun tryWithTransferLock(
        threadId: Long,
        leaseSeconds: Long,
        block: suspend () -> Int
    ): Int {
        val acquired = transferLock.tryLockAsync(0, leaseSeconds, TimeUnit.SECONDS, threadId).await()
        if (!acquired) return 0

        try {
            return block()
        } finally {
            transferLock.unlockAsync(threadId).await()
        }
    }

    suspend fun isTransferLockHeldBy(threadId: Long): Boolean = transferLock.isHeldByThreadAsync(threadId).await()
}