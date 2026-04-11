package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.queue.common.hook.priority.LuckpermsPriorityHook
import dev.slne.surf.surfapi.core.api.util.logger
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectList
import org.jetbrains.annotations.MustBeInvokedByOverriders
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractQueue(override val serverName: String) : SurfQueue {
    protected val keys = RedisQueueKeys(serverName)
    protected val store = RedisQueueStore(keys)
    protected val lockManager = RedisQueueLockManager(keys)
    protected val epochMs = store.initEpochMs()

    /**
     * Monotonically increasing sequence counter used to break ties when
     * multiple players enqueue within the same millisecond. Wraps around
     * at MAX_SEQUENCE; by the time it wraps, the millisecond will have
     * advanced so no collision occurs.
     */
    private val enqueueSequence = AtomicInteger(0)

    var tickCount = 0
        private set

    companion object {
        private val log = logger()

        fun fixPriority(uuid: UUID, priority: Int): Int {
            return if (priority <= RedisQueueScore.MAX_PRIORITY) {
                priority
            } else {
                log.atWarning()
                    .log(
                        "Priority %d for %s exceeds max representable priority, capping to %d",
                        priority,
                        uuid,
                        RedisQueueScore.MAX_PRIORITY
                    )

                RedisQueueScore.MAX_PRIORITY
            }
        }
    }

    @MustBeInvokedByOverriders
    open suspend fun tick() {
        tickCount++
    }

    override suspend fun enqueue(uuid: UUID): Boolean {
        val priority = LuckpermsPriorityHook.getPriority(uuid)
        return enqueue(uuid, priority)
    }

    override suspend fun enqueue(uuid: UUID, priority: Int): Boolean {
        val priorityFixed = fixPriority(uuid, priority)
        val now = Instant.now().toEpochMilli()
        val sequence = enqueueSequence.getAndUpdate { current ->
            if (current >= RedisQueueScore.MAX_SEQUENCE) 0 else current + 1
        }

        val score = RedisQueueScore.pack(
            priorityFixed,
            now - epochMs,
            sequence
        )

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

    @Deprecated(
        "Use getAllUuidsOrderedByPosition for better performance",
        replaceWith = ReplaceWith("getAllUuidsOrderedByPosition()")
    )
    override suspend fun getAllUuidsWithPosition(): ObjectList<Object2IntMap.Entry<UUID>> {
        val entries = store.readAllEntries()
        val uuidsWithPosition = ObjectArrayList<Object2IntMap.Entry<UUID>>(entries.size)

        for ((index, entry) in entries.withIndex()) {
            uuidsWithPosition.add(Object2IntMap.entry(entry.value, index + 1))
        }

        return uuidsWithPosition
    }

    override suspend fun getAllUuidsOrderedByPosition(): ObjectList<UUID> {
        val entries = store.readAllEntries()
        val uuids = ObjectArrayList<UUID>(entries.size)

        for (entry in entries) {
            uuids.add(entry.value)
        }

        return uuids
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

    suspend fun getEntryMeta(uuid: UUID): QueueEntry? = store.getMeta(uuid)
    suspend fun getEntryScore(uuid: UUID): RedisQueueScore? = store.getScore(uuid)
    suspend fun getEntryLastSeen(uuid: UUID): Long? = store.getLastSeen(uuid)
    suspend fun getEntryRetryCount(uuid: UUID): Int? = store.getRetryCount(uuid)
}