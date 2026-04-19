package dev.slne.surf.queue.common.queue

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.queue.common.priority.LuckpermsPriorityResolver
import dev.slne.surf.queue.common.queue.entry.QueueEntry
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectList
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base implementation of [SurfQueue] backed by Redis.
 *
 * Subclasses must supply a [serverName] and may override [onEnqueued] / [onDequeued]
 * for platform-specific side effects (e.g., metrics). They may also override [tick]
 * to add periodic processing, but **must** call `super.tick()`.
 *
 * Ordering is determined by a packed Redis score that encodes priority, enqueue
 * timestamp (relative to a per-queue epoch), and a monotonic sequence number for
 * tie-breaking within the same millisecond.
 *
 * @param serverName The name of the server this queue targets.
 */
abstract class AbstractQueue(override val serverName: String) : SurfQueue {

    /**
     * Redis keys used for queue storage and synchronization.
     */
    protected val keys = RedisQueueKeys(serverName)

    /**
     * Persistent storage for queue entries and scores.
     */
    protected val store = RedisQueueStore(keys)

    /**
     * Distributed lock manager for this queue.
     */
    protected val lockManager = RedisQueueLockManager(keys)

    /**
     * Millisecond epoch used to make timestamps relative, reducing score magnitude.
     */
    protected val epochMs = store.initEpochMs()

    /**
     * Monotonically increasing sequence counter used to break ties when
     * multiple players enqueue within the same millisecond. Wraps around
     * at MAX_SEQUENCE; by the time it wraps, the millisecond will have
     * advanced so no collision occurs.
     */
    private val enqueueSequence = AtomicInteger(0)

    companion object {
        private val log = logger()

        /**
         * Caps [priority] to [RedisQueueScore.MAX_PRIORITY] and logs a warning if it exceeds the limit.
         */
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

    override suspend fun enqueue(uuid: UUID): Boolean {
        val priority = LuckpermsPriorityResolver.getPriority(uuid)
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

    /**
     * Called after a player is successfully enqueued. Override for side effects such as
     * recording metrics. No-op by default.
     */
    protected open fun onEnqueued() {}

    /**
     * Called after a player is successfully dequeued. Override for side effects such as
     * recording metrics. No-op by default.
     */
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

    /**
     * Returns the raw [QueueEntry] metadata for [uuid], or `null` if not queued.
     */
    suspend fun getEntryMeta(uuid: UUID): QueueEntry? = store.getMeta(uuid)

    /**
     * Returns the packed [RedisQueueScore] for [uuid], or `null` if not queued.
     */
    suspend fun getEntryScore(uuid: UUID): RedisQueueScore? = store.getScore(uuid)

    /**
     * Returns the last-seen timestamp (epoch ms) for [uuid], or `null` if not recorded.
     * Used to track disconnected players within their grace period.
     */
    suspend fun getEntryLastSeen(uuid: UUID): Long? = store.getLastSeen(uuid)

    /**
     * Returns the number of transfer retry attempts for [uuid], or `null` if not queued.
     */
    suspend fun getEntryRetryCount(uuid: UUID): Int? = store.getRetryCount(uuid)
}