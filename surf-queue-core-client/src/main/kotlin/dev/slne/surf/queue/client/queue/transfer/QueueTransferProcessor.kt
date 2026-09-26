package dev.slne.surf.queue.client.queue.transfer

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.api.core.util.mutableObjectSetOf
import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.core.api.common.player.SurfPlayer
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.queue.api.SurfQueueAvailableSlotsProvider
import dev.slne.surf.queue.client.config.SurfQueueConfig
import dev.slne.surf.queue.client.metrics.QueueMetrics
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.queue.RedisQueueLockManager
import dev.slne.surf.queue.common.queue.RedisQueueScore
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.queue.common.queue.entry.QueueEntry
import dev.slne.surf.redis.libs.redisson.config.DecorrelatedJitterDelay
import dev.slne.surf.redis.libs.redisson.config.DelayStrategy
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import java.time.Duration
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

class QueueTransferProcessor(
    private val serverName: String,
    private val store: RedisQueueStore,
    private val lockManager: RedisQueueLockManager,
    private val gracePeriodMs: Long,
    private val scope: CoroutineScope,
) {
    private val transfer = QueueTransfer(serverName)
    private val inFlight = ObjectOpenHashSet<UUID>()
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

        // Exponential backoff: decrease CPU usage and Redis commands when the
        // queue is empty or the target server is full.
        if (System.currentTimeMillis() < nextTransferTime) return
        if (store.isPaused()) return

        try {
            val started = startTransfers()
            if (started <= 0) {
                backOff()
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

    private fun backOff() {
        val delayDuration = delay.calcDelay(attempts)
        nextTransferTime = System.currentTimeMillis() + delayDuration.toMillis()
        attempts++
    }

    private suspend fun startTransfers(): Int {
        val targetServer = SurfServer[serverName] ?: return 0
        val availableSlots =
            SurfQueueAvailableSlotsProvider.get().getAvailableSlots(targetServer) - inFlight.size

        if (availableSlots <= 0) return 0
        val maxTransfers = min(availableSlots, SurfQueueConfig.getConfig().maxTransfersPerSecond)

        return lockManager.withTransferLock { acquired ->
            QueueMetrics.recordLockAttempt(acquired)
            if (acquired) {
                doStartTransfers(maxTransfers, targetServer)
            } else {
                0
            }
        }
    }

    private suspend fun doStartTransfers(maxTransfers: Int, targetServer: SurfServer): Int {
        var started = 0

        // In-flight entries remain at the head of the queue and are skipped
        val candidates = store.topValues(maxTransfers * 3 + inFlight.size)

        for (uuid in candidates) {
            if (started >= maxTransfers) break
            if (uuid in inFlight) continue

            val entry = store.getMeta(uuid)
            if (entry == null) {
                store.removeAllFor(uuid)
                continue
            }

            when (val preparation = transfer.prepare(uuid)) {
                is QueueTransfer.Preparation.Ready -> {
                    inFlight.add(uuid)
                    scope.launch {
                        runTransfer(entry, preparation.player, targetServer)
                    }
                    started++
                }

                is QueueTransfer.Preparation.Rejected -> handleResult(entry, preparation.action, null)
            }
        }

        return started
    }

    private suspend fun runTransfer(entry: QueueEntry, player: SurfPlayer, targetServer: SurfServer) {
        try {
            val (action, message) = transfer.connect(player, targetServer)
            handleResult(entry, action, message)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.atWarning()
                .withCause(e)
                .log("Failed to handle transfer result of %s for queue %s", entry.uuid, serverName)
        } finally {
            inFlight.remove(entry.uuid)
        }
    }

    private suspend fun handleResult(entry: QueueEntry, action: TransferAction, message: Component?) {
        val uuid = entry.uuid
        when (action) {
            TransferAction.DONE -> {
                store.dequeue(uuid)
                QueueMetrics.recordTransfer(serverName)
                log.atInfo()
                    .log("Transferred %s to %s", uuid, serverName)
            }

            TransferAction.PLAYER_NOT_FOUND -> {
                handlePlayerNotFound(uuid)
            }

            TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER,
            TransferAction.PLAYER_ALREADY_CONNECTING -> {
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
                    sendConnectionResultMessage(uuid, message)
                }
            }

            TransferAction.PLUGIN_CANCELLED_TRANSFER,
            TransferAction.ERROR,
            TransferAction.TIMEOUT -> {
                QueueMetrics.recordFailedTransfer(serverName)
                retryEntry(uuid, maxRetries = 3) {
                    sendConnectionResultMessage(uuid, message)
                }
            }

            TransferAction.NOT_WHITELISTED -> {
                store.dequeue(uuid)
                QueueMetrics.recordFailedTransfer(serverName)
                QueueMetrics.recordDequeue(serverName)
                sendConnectionResultMessage(uuid, message)
                log.atWarning()
                    .log(
                        "Player %s removed from queue %s due to not being whitelisted",
                        uuid,
                        serverName
                    )
            }

            TransferAction.SERVER_FULL,
            TransferAction.SERVER_NOT_FOUND -> backOff()
        }
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
        val now = System.currentTimeMillis()
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
     * players behind it are selected first on the next tick.
     *
     * The new score is derived from the *next* entry's packed score rather than from this
     * entry's own priority: priority occupies the high bits, so re-packing inside the
     * original priority band cannot move an entry past a lower-priority successor.
     *
     * Does nothing if the entry is no longer queued or is the last one in the queue.
     */
    private suspend fun skipEntry(uuid: UUID) {
        val currentScore = store.getScore(uuid) ?: return

        val nextEntries = store.entriesAfter(currentScore, limit = 1)
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
}
