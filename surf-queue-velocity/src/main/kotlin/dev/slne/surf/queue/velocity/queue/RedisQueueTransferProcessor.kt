package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.QueueEntry
import dev.slne.surf.queue.common.queue.RedisQueueScorePacker
import dev.slne.surf.queue.common.queue.RedisQueueStore
import dev.slne.surf.surfapi.core.api.util.logger
import java.time.Instant
import java.util.*

class RedisQueueTransferProcessor(
    private val serverName: String,
    private val store: RedisQueueStore,
    private val gracePeriodMs: Long,
    private val lockLeaseSeconds: Long
) {
    private val transfer = QueueTransfer(this, serverName)

    companion object {
        private val log = logger()
    }

    suspend fun tick() {
        try {
            transfer.tryTransfer()
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

        return store.tryWithTransferLock(threadId, lockLeaseSeconds) {
            doProcessTransfers(maxTransfers, tryTransfer) {
                store.isTransferLockHeldBy(threadId)
            }
        }
    }

    private suspend fun doProcessTransfers(
        maxTransfers: Int,
        tryTransfer: suspend (QueueEntry) -> VelocitySurfQueue.TransferAction,
        isLocked: suspend () -> Boolean
    ): Int {
        var transferred = 0

        while (transferred < maxTransfers) {
            val uuid = store.top1() ?: break

            val entry = store.getMeta(uuid)
            if (entry == null) {
                // no metadata
                store.removeAllFor(uuid)
                continue
            }

            try {
                when (val result = tryTransfer(entry)) {
                    VelocitySurfQueue.TransferAction.DONE -> {
                        store.dequeue(uuid)
                        transferred++
                        log.atInfo()
                            .log("Transferred %s to %s", uuid, serverName)
                    }

                    VelocitySurfQueue.TransferAction.PLAYER_NOT_FOUND,
                    VelocitySurfQueue.TransferAction.PLAYER_NOT_CONNECTED_TO_A_SERVER -> {
                        handlePlayerNotFound(uuid, entry)
                    }

                    VelocitySurfQueue.TransferAction.PLAYER_ALREADY_ON_SERVER -> {
                        store.dequeue(uuid)
                        log.atInfo().log("Player %s is already on server %s", uuid, serverName)
                    }

                    VelocitySurfQueue.TransferAction.PLUGIN_CANCELLED_TRANSFER,
                    VelocitySurfQueue.TransferAction.PLAYER_KICKED_FROM_SERVER,
                    VelocitySurfQueue.TransferAction.PLAYER_ALREADY_CONNECTING,
                    VelocitySurfQueue.TransferAction.ERROR -> {
                        skipEntry(uuid, entry)
                    }

                    VelocitySurfQueue.TransferAction.SERVER_FULL -> break
                    VelocitySurfQueue.TransferAction.SERVER_NOT_FOUND -> break
                }
            } catch (_: AbortException) {
                break
            }

            if (!isLocked()) break
        }

        return transferred
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
        log.atInfo()
            .log("Player %s removed from queue %s (offline > %dms)", uuid, serverName, gracePeriodMs)
    }

    private suspend fun skipEntry(uuid: UUID, meta: QueueEntry) {
        val scores = store.top2()
        if (scores.size < 2) throw AbortException()

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

        store.addOrUpdateScore(uuid, newScore)
    }

    private class AbortException : Exception()
}