package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.redis.RedisInstance
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.future.await
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration.Companion.minutes

class RedisQueue(val serverName: String) {
    private val log = logger()
    private val queue =
        redisApi.redisson.getPriorityBlockingQueue<QueueEntry>(RedisInstance.namespaced("queue-$serverName"))

    companion object {
        val MAX_PLAYER_DISCONNECT_TIME = 1.minutes.inWholeMilliseconds
    }

    suspend fun enqueue(uuid: UUID, priority: Int) {
        queue.addAsync(QueueEntry(uuid, System.currentTimeMillis(), priority)).await()
    }

    suspend fun tryTransfer(transfer: suspend (TransferContext, QueueEntry) -> Boolean): Boolean {
        try {
            val context = TransferContext()

            queue.pollAsync().await()?.let { entry ->
                if (!transfer(entry)) {
                    queue.addAsync(entry).await()
                    return false
                } else {
                    return true
                }
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("An error occurred while trying to transfer a player from the queue for server $serverName.")
        }

        return false
    }

    class TransferContext {
        private val readd = LinkedBlockingQueue<QueueEntry>()

        fun markPlayerNotSeen(entry: QueueEntry) {
            val lastPlayerSeenMark = entry.lastPlayerSeenMark

            if (lastPlayerSeenMark == null) {
                entry.lastPlayerSeenMark = System.currentTimeMillis()
            } else {

            }

            readd.add(entry)
        }
    }

    enum class TransferResult {
        SUCCESS,
        SERVER_FULL,
        PLAYER_NOT_FOUND,
        ERROR
    }
}