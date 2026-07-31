package dev.slne.surf.queue.paper.queue.transfer

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.api.core.util.mutableObjectSetOf
import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.queue.RedisQueueLockManager
import dev.slne.surf.queue.common.queue.RedisQueueScore
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.queue.common.queue.entry.QueueEntry
import dev.slne.surf.queue.paper.metrics.QueueMetrics
import dev.slne.surf.redis.libs.redisson.config.DecorrelatedJitterDelay
import dev.slne.surf.redis.libs.redisson.config.DelayStrategy
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
        if (!QueueInstance.get().isLoaded) return
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
        var attempts = 0
        val maxAttempts = maxTransfers * 3
        val seen = mutableObjectSetOf<UUID>()

        while (transferred < maxTransfers && attempts < maxAttempts) {
            val uuid = store.top1() ?: break
            if (!seen.add(uuid)) break // UUID already processed this tick -> we've cycled back, stop to avoid infinite loop

            attempts++

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

                    TransferAction.PLAYER_NOT_FOUND -> {
                        handlePlayerNotFound(uuid)
                    }

                    TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER -> {
                        QueueMetrics.recordSkip(serverName)
                        markPlayerSeen(uuid)
                        skipEntry(uuid)
                    }

                    TransferAction.PLAYER_ALREADY_ON_SERVER -> {
                        store.dequeue(uuid)
                        QueueMetrics.recordDequeue(serverName)
                        log.atInfo().log("Player %s is already on server %s", uuid, serverName)
                    }

                    TransferAction.PLAYER_KICKED_FROM_SERVER -> {
                        QueueMetrics.recordFailedTransfer(serverName)
                        retryEntry(uuid, maxRetries = 5) {
                            sendConnectionResultMessage(entry.uuid, message)
                        }
                    }

                    TransferAction.PLAYER_ALREADY_CONNECTING -> {
                        QueueMetrics.recordSkip(serverName)
                        markPlayerSeen(uuid)
                        skipEntry(uuid)
                    }

                    TransferAction.PLUGIN_CANCELLED_TRANSFER,
                    TransferAction.ERROR -> {
                        QueueMetrics.recordFailedTransfer(serverName)
                        retryEntry(uuid, maxRetries = 3) {
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

                    TransferAction.NOT_WHITELISTED -> {
                        store.dequeue(uuid)
                        QueueMetrics.recordFailedTransfer(serverName)
                        QueueMetrics.recordDequeue(serverName)
                        sendConnectionResultMessage(entry.uuid, message)
                        log.atWarning()
                            .log(
                                "Player %s removed from queue %s due to not being whitelisted",
                                uuid,
                                serverName
                            )
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

    private suspend fun retryEntry(uuid: UUID, maxRetries: Int, onMaxRetriesReached: () -> Unit) {
        val retryCount = store.incrementRetryCount(uuid)
        if (retryCount >= maxRetries) {
            store.dequeue(uuid)
            QueueMetrics.recordRetryExhausted()
            QueueMetrics.recordDequeue(serverName)
            onMaxRetriesReached()
            log.atWarning()
                .log("Player %s removed from queue %s after %d failed transfer attempts", uuid, serverName, retryCount)
        } else {
            skipEntry(uuid)
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

    private suspend fun handlePlayerNotFound(uuid: UUID) {
        val now = Instant.now().toEpochMilli()
        val lastSeen = store.getLastSeen(uuid)

        if (lastSeen == null) {
            store.putLastSeen(uuid, now)
            skipEntry(uuid)
            return
        }

        if (now - lastSeen < gracePeriodMs) {
            skipEntry(uuid)
            return
        }

        store.dequeue(uuid)
        QueueMetrics.recordGraceExpiry()
        QueueMetrics.recordDequeue(serverName)
        log.atInfo()
            .log("Player %s removed from queue %s (offline > %dms)", uuid, serverName, gracePeriodMs)
    }

    private suspend fun markPlayerSeen(uuid: UUID) {
        store.clearLastSeen(uuid)
    }

    /**
     * Moves a queue entry behind the next entry in the sorted set so that
     * the transfer loop can proceed to other players.
     *
     * The new score is derived from the *next* entry's packed score rather than from this
     * entry's own priority: priority occupies the high bits, so re-packing inside the
     * original priority band cannot move an entry past a lower-priority successor — the
     * entry would keep winning [RedisQueueStore.top1] and stall the loop forever.
     *
     * If the entry is the last one in the queue (no next entry exists),
     * we simply leave it in place and return instead of aborting the
     * entire transfer loop.
     */
    private suspend fun skipEntry(uuid: UUID) {
        if (store.getScore(uuid) == null) throw AbortException() // entry vanished mid-tick

        val nextEntries = store.entriesAfter(uuid, limit = 1)
        if (nextEntries.isEmpty()) {
            // This entry is the last in the queue — nothing to skip past.
            return
        }

        val newScore = RedisQueueScore(nextEntries.first().score).nextAfter()
        if (newScore == null) {
            log.atWarning()
                .log("Cannot skip entry %s in queue %s: packed score space exhausted", uuid, serverName)
            return
        }

        store.addOrUpdateScore(uuid, newScore)
    }

    private fun sendConnectionResultMessage(uuid: UUID, message: Component?) {
        try {
            if (message != null) {
                val player = SurfCoreApi.getPlayer(uuid) ?: return
                SurfCoreApi.sendText(player, message)
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to send connection result message for player %s", uuid)
        }
    }

    private class AbortException : Exception()
}