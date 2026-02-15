package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.QueueEntry
import dev.slne.surf.queue.common.queue.RedisQueueScorePacker
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.queue.velocity.metrics.QueueMetrics
import dev.slne.surf.redis.libs.redisson.config.DecorrelatedJitterDelay
import dev.slne.surf.redis.libs.redisson.config.DelayStrategy
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Duration
import java.time.Instant
import java.util.*

class RedisQueueTransferProcessor(
    private val serverName: String,
    private val store: RedisQueueStore,
    private val gracePeriodMs: Long
) {
    private val transfer = QueueTransfer(this, serverName)
    private var delay = createDelay()
    private var attempts: Int = 0
    private var nextTransferTime = System.currentTimeMillis()

    companion object {
        private val log = logger()
        private fun createDelay(): DelayStrategy =
            DecorrelatedJitterDelay(Duration.ofSeconds(2), Duration.ofSeconds(10))
    }

    suspend fun tick() {
        if (store.isPaused()) return

        try {
            // decrease CPU usage and redis commands when the queue is empty or the server is full
            if (System.currentTimeMillis() < nextTransferTime) return

            val transferred = transfer.tryTransfer()
            if (transferred <= 0) {
                val delay = delay.calcDelay(attempts)
                nextTransferTime = System.currentTimeMillis() + delay.toMillis()
                attempts++
            } else {
                attempts = 0
                delay = createDelay()
            }

        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to process transfers for queue %s", serverName)
        }
    }

    suspend fun processTransfers(
        maxTransfers: Int,
        tryTransfer: suspend (QueueEntry) -> VelocitySurfQueue.TransferAction
    ): Int {
        val threadId = Thread.currentThread().threadId()

        return store.tryWithTransferLock(threadId) { acquired ->
            QueueMetrics.recordLockAttempt(acquired)
            if (acquired) {
                doProcessTransfers(maxTransfers, tryTransfer)
            } else {
                0
            }
        }
    }

    private suspend fun doProcessTransfers(
        maxTransfers: Int,
        tryTransfer: suspend (QueueEntry) -> VelocitySurfQueue.TransferAction
    ): Int {
        var transferred = 0

        while (transferred < maxTransfers) {
            val uuid = store.top1() ?: break

            val entry = store.getMeta(uuid)
            if (entry == null) {
                store.removeAllFor(uuid)
                continue
            }

            try {
                when (val result = tryTransfer(entry)) {
                    VelocitySurfQueue.TransferAction.DONE -> {
                        store.dequeue(uuid)
                        transferred++
                        QueueMetrics.recordTransfer(serverName)
                        log.atInfo()
                            .log("Transferred %s to %s", uuid, serverName)
                    }

                    VelocitySurfQueue.TransferAction.PLAYER_NOT_FOUND,
                    VelocitySurfQueue.TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER -> {
                        handlePlayerNotFound(uuid, entry)
                    }

                    VelocitySurfQueue.TransferAction.PLAYER_ALREADY_ON_SERVER -> {
                        store.dequeue(uuid)
                        QueueMetrics.recordDequeue(serverName)
                        log.atInfo().log("Player %s is already on server %s", uuid, serverName)
                    }

                    VelocitySurfQueue.TransferAction.PLAYER_KICKED_FROM_SERVER -> {
                        QueueMetrics.recordFailedTransfer(serverName)
                        retryEntry(uuid, entry, maxRetries = 5)
                    }

                    VelocitySurfQueue.TransferAction.PLAYER_ALREADY_CONNECTING -> {
                        QueueMetrics.recordSkip(serverName)
                        skipEntry(uuid, entry)
                    }

                    VelocitySurfQueue.TransferAction.PLUGIN_CANCELLED_TRANSFER,
                    VelocitySurfQueue.TransferAction.ERROR -> {
                        QueueMetrics.recordFailedTransfer(serverName)
                        retryEntry(uuid, entry, maxRetries = 3)
                    }

                    VelocitySurfQueue.TransferAction.SERVER_FULL -> break
                    VelocitySurfQueue.TransferAction.SERVER_NOT_FOUND -> break
                }
            } catch (_: AbortException) {
                break
            }
        }

        return transferred
    }

    private suspend fun retryEntry(uuid: UUID, meta: QueueEntry, maxRetries: Int) {
        val retryCount = store.incrementRetryCount(uuid)
        if (retryCount >= maxRetries) {
            store.dequeue(uuid)
            QueueMetrics.recordRetryExhausted()
            QueueMetrics.recordDequeue(serverName)
            log.atWarning()
                .log("Player %s removed from queue %s after %d failed transfer attempts", uuid, serverName, retryCount)
        } else {
            skipEntry(uuid, meta)
            log.atInfo()
                .log(
                    "Retrying transfer for player %s in queue %s (attempt %d/%d)",
                    uuid,
                    serverName,
                    retryCount,
                    maxRetries
                )
        }
    }

    private suspend fun handlePlayerNotFound(uuid: UUID, entry: QueueEntry) {
        val now = Instant.now().toEpochMilli()
        val lastSeen = store.getLastSeen(uuid)

        if (lastSeen == null) {
            store.putLastSeen(uuid, now)
            skipEntry(uuid, entry)
            return
        }

        if (now - lastSeen < gracePeriodMs) {
            skipEntry(uuid, entry)
            return
        }

        store.dequeue(uuid)
        QueueMetrics.recordGraceExpiry()
        QueueMetrics.recordDequeue(serverName)
        log.atInfo()
            .log("Player %s removed from queue %s (offline > %dms)", uuid, serverName, gracePeriodMs)
    }

    private suspend fun skipEntry(uuid: UUID, meta: QueueEntry) {
        val currentScore = store.getScore(uuid) ?: throw AbortException()
        val nextEntries = store.entriesAfter(uuid, limit = 1)
        if (nextEntries.isEmpty()) throw AbortException()

        val nextScoreRaw = nextEntries.first().score
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

        store.addOrUpdateScore(uuid, newScore)
    }

    private class AbortException : Exception()
}