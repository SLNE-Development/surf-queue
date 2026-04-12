package dev.slne.surf.queue.paper.queue.transfer

import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.core.api.common.util.sendText
import dev.slne.surf.queue.common.queue.QueueEntry
import dev.slne.surf.queue.common.queue.RedisQueueLockManager
import dev.slne.surf.queue.common.queue.RedisQueueScore
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.redis.libs.redisson.config.DecorrelatedJitterDelay
import dev.slne.surf.redis.libs.redisson.config.DelayStrategy
import dev.slne.surf.api.core.util.logger
import net.kyori.adventure.text.Component
import java.time.Duration
import java.time.Instant
import java.util.*

class PaperQueueTransferProcessor(
    private val serverName: String,
    private val store: RedisQueueStore,
    private val lockManager: RedisQueueLockManager,
    private val gracePeriodMs: Long
) {
    private val transfer = PaperQueueTransfer(this, serverName)
    private var delay = createDelay()
    private var attempts: Int = 0
    private var nextTransferTime = System.currentTimeMillis()

    companion object {
        private val log = logger()
        private fun createDelay(): DelayStrategy =
            DecorrelatedJitterDelay(Duration.ofSeconds(2), Duration.ofSeconds(5))
    }

    suspend fun tick() {
        if (store.isPaused()) return

        try {
            // Exponential backoff: decrease CPU usage and Redis commands when the
            // queue is empty or the target server is full.
            if (System.currentTimeMillis() < nextTransferTime) return

            val transferred = transfer.tryTransfer()
            if (transferred <= 0) {
                val delayDuration = delay.calcDelay(attempts)
                nextTransferTime = System.currentTimeMillis() + delayDuration.toMillis()
                attempts++
            } else {
                attempts = 0
                delay = createDelay()
                nextTransferTime = System.currentTimeMillis()
            }

        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to process transfers for queue %s", serverName)
        }
    }

    suspend fun processTransfers(
        maxTransfers: Int,
        tryTransfer: suspend (QueueEntry) -> Pair<TransferAction, Component?>
    ): Int = lockManager.withTransferLock { acquired ->
        QueueMetrics.recordLockAttempt(acquired)
        if (acquired) {
            doProcessTransfers(maxTransfers, tryTransfer)
        } else {
            0
        }
    }

    private suspend fun doProcessTransfers(
        maxTransfers: Int,
        tryTransfer: suspend (QueueEntry) -> Pair<TransferAction, Component?>
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
                val (action, message) = tryTransfer(entry)
                when (action) {
                    TransferAction.DONE -> {
                        store.dequeue(uuid)
                        transferred++
                        QueueMetrics.recordTransfer(serverName)
                        log.atInfo()
                            .log("Transferred %s to %s", uuid, serverName)
                    }

                    TransferAction.PLAYER_NOT_FOUND,
                    TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER -> {
                        handlePlayerNotFound(uuid, entry)
                    }

                    TransferAction.PLAYER_ALREADY_ON_SERVER -> {
                        store.dequeue(uuid)
                        QueueMetrics.recordDequeue(serverName)
                        log.atInfo().log("Player %s is already on server %s", uuid, serverName)
                    }

                    TransferAction.PLAYER_KICKED_FROM_SERVER -> {
                        QueueMetrics.recordFailedTransfer(serverName)
                        retryEntry(uuid, entry, maxRetries = 5) {
                            sendConnectionResultMessage(entry.uuid, message)
                        }
                    }

                    TransferAction.PLAYER_ALREADY_CONNECTING -> {
                        QueueMetrics.recordSkip(serverName)
                        skipEntry(uuid, entry)
                    }

                    TransferAction.PLUGIN_CANCELLED_TRANSFER,
                    TransferAction.ERROR -> {
                        QueueMetrics.recordFailedTransfer(serverName)
                        retryEntry(uuid, entry, maxRetries = 3) {
                            sendConnectionResultMessage(entry.uuid, message)
                        }
                    }

                    TransferAction.TIMEOUT -> {
                        // Timeout means the target server is likely unreachable.
                        // Dequeue immediately instead of retrying with another 30s timeout
                        // to avoid blocking the entire queue for extended periods.
                        store.dequeue(uuid)
                        QueueMetrics.recordFailedTransfer(serverName)
                        QueueMetrics.recordDequeue(serverName)
                        sendConnectionResultMessage(entry.uuid, message)
                        log.atWarning()
                            .log(
                                "Player %s removed from queue %s due to transfer timeout",
                                uuid,
                                serverName
                            )
                        break
                    }

                    TransferAction.SERVER_FULL -> break
                    TransferAction.SERVER_NOT_FOUND -> break
                }
            } catch (_: AbortException) {
                break
            }
        }

        return transferred
    }

    private suspend fun retryEntry(uuid: UUID, meta: QueueEntry, maxRetries: Int, onMaxRetriesReached: () -> Unit) {
        val retryCount = store.incrementRetryCount(uuid)
        if (retryCount >= maxRetries) {
            store.dequeue(uuid)
            QueueMetrics.recordRetryExhausted()
            QueueMetrics.recordDequeue(serverName)
            onMaxRetriesReached()
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

    /**
     * Moves a queue entry behind the next entry in the sorted set so that
     * the transfer loop can proceed to other players.
     *
     * If the entry is the last one in the queue (no next entry exists),
     * we simply leave it in place and return instead of aborting the
     * entire transfer loop.
     */
    private suspend fun skipEntry(uuid: UUID, meta: QueueEntry) {
        val currentScore = store.getScore(uuid) ?: throw AbortException()
        val nextEntries = store.entriesAfter(uuid, limit = 1)
        if (nextEntries.isEmpty()) {
            // This entry is the last in the queue — nothing to skip past.
            return
        }

        val nextScoreRaw = nextEntries.first().score
        val nextScore = RedisQueueScore(nextScoreRaw)

        val sequenceOverflow = nextScore.sequence >= RedisQueueScore.MAX_SEQUENCE
        val nextSequence = if (sequenceOverflow) 0 else nextScore.sequence + 1
        // On overflow, also bump deltaMs so the new score is strictly greater
        // than the entry we are skipping past.
        val nextDeltaMs = if (sequenceOverflow) nextScore.deltaMs + 1 else nextScore.deltaMs

        val newScore = RedisQueueScore.pack(
            meta.priority,
            nextDeltaMs,
            nextSequence
        )

        store.addOrUpdateScore(uuid, newScore)
    }

    private fun sendConnectionResultMessage(uuid: UUID, message: Component?) {
        try {
            if (message != null) {
                val player = SurfCoreApi.getPlayer(uuid) ?: return
                player.sendText { append(message) }
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to send connection result message for player %s", uuid)
        }
    }

    private class AbortException : Exception()
}