package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.surfapi.core.api.util.logger
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.time.Instant
import java.util.*

abstract class AbstractSurfQueue(override val serverName: String) : SurfQueue {
    protected val keys = RedisQueueKeys(serverName)
    protected val store = RedisQueueStore(keys)
    protected val lockManager = RedisQueueLockManager(keys)
    protected val epochMs = store.initEpochMs()

    companion object {
        private val log = logger()

        fun fixPriority(uuid: UUID, priority: Int): Int {
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
    }

    override suspend fun enqueue(uuid: UUID, priority: Int): Boolean {
        val priorityFixed = fixPriority(uuid, priority)
        val now = Instant.now().toEpochMilli()

        val score = RedisQueueScorePacker.pack(
            priorityFixed,
            now - epochMs,
            0
        ) // TODO: set sequence if it happens to enqueue multiple times in the same ms


        val meta = QueueEntry(uuid, now, priorityFixed)
        val added = store.enqueueIfAbsent(uuid, meta, score)

        if (added) {
            onEnqueued()
            log.atInfo()
                .log("Enqueued %s in queue %s with priority %d", uuid, serverName, priorityFixed)
        }

        return added
    }

    protected open fun onEnqueued() {}
    protected open fun onDequeued() {}

    override suspend fun dequeue(uuid: UUID): Boolean {
        val removed = store.dequeue(uuid)
        if (removed) {
            onDequeued()
        }
        return removed
    }

    override suspend fun isQueued(uuid: UUID): Boolean {
        return store.isQueued(uuid)
    }

    override suspend fun getPosition(uuid: UUID): Int? {
        return store.rank(uuid)
    }

    override suspend fun getAllUuidsWithPosition(): Collection<Object2IntMap.Entry<UUID>> {
        return store.readAllEntries()
            .mapIndexed { index, entry ->
                Object2IntMap.entry(entry.value, index + 1)
            }
    }

    override suspend fun size(): Int {
        return store.size()
    }

    override suspend fun isPaused(): Boolean {
        return store.isPaused()
    }

    override suspend fun resume() {
        store.setPaused(false)
    }

    override suspend fun pause() {
        store.setPaused(true)
    }
}