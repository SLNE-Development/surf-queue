package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.queue.codec.QueueEntryCodec
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.codec.UUIDCodec
import dev.slne.surf.redis.libs.redisson.api.BatchOptions
import dev.slne.surf.redis.libs.redisson.api.BatchResult
import dev.slne.surf.redis.libs.redisson.api.RBatch
import dev.slne.surf.redis.libs.redisson.client.codec.IntegerCodec
import dev.slne.surf.redis.libs.redisson.client.codec.LongCodec
import dev.slne.surf.redis.libs.redisson.client.protocol.ScoredEntry
import dev.slne.surf.redis.libs.redisson.codec.CompositeCodec
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.Blocking
import java.time.Instant
import java.util.*

/**
 * Redis-backed persistent storage for a single queue.
 *
 * Manages five data structures per queue:
 * - **scoredSet** — sorted set of UUIDs ordered by packed score (the queue order)
 * - **metaMap** — hash of UUID → [QueueEntry] metadata
 * - **lastSeenMap** — hash of UUID → last-seen epoch ms (for grace-period tracking)
 * - **retryCountMap** — hash of UUID → transfer retry count
 * - **pausedBucket** — single-value bucket (`1` = paused)
 *
 * All mutating operations use atomic batches where possible to ensure consistency.
 *
 * @param keys the [RedisQueueKeys] providing key names for this queue
 */
class RedisQueueStore(keys: RedisQueueKeys) {
    private val scoredSet = redisApi.redisson.getScoredSortedSet<UUID>(keys.entriesKey, UUIDCodec.INSTANCE)
    private val metaMap = redisApi.redisson.getMap<UUID, QueueEntry>(
        keys.metaKey,
        CompositeCodec(UUIDCodec.INSTANCE, QueueEntryCodec())
    )
    private val lastSeenMap = redisApi.redisson.getMap<UUID, Long>(
        keys.lastSeenKey,
        CompositeCodec(UUIDCodec.INSTANCE, LongCodec.INSTANCE)
    )

    private val retryCountMap = redisApi.redisson.getMap<UUID, Int>(
        keys.retryCountKey,
        CompositeCodec(UUIDCodec.INSTANCE, IntegerCodec.INSTANCE)
    )

    private val epochMsBucket = redisApi.redisson.getBucket<Long>(keys.epochMsKey, LongCodec.INSTANCE)
    private val pausedBucket = redisApi.redisson.getBucket<Int>(keys.pausedKey, IntegerCodec.INSTANCE)

    companion object {
        private fun atomicBatchOptions(): BatchOptions {
            return BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC)
        }
    }

    // region RBatch helper
    private inline fun <R> createAtomicBatch(block: RBatch.() -> R) =
        redisApi.redisson.createBatch(atomicBatchOptions()).run(block)

    private suspend inline fun executeAtomicBatch(block: RBatch.() -> Unit): BatchResult<*> = createAtomicBatch {
        block()
        executeAsync().await()
    }

    private fun RBatch.getQueueScoredSet() = getScoredSortedSet<UUID>(scoredSet.name, scoredSet.codec)
    private fun RBatch.getQueueMetaMap() = getMap<UUID, QueueEntry>(metaMap.name, metaMap.codec)
    private fun RBatch.getQueueLastSeenMap() = getMap<UUID, Long>(lastSeenMap.name, lastSeenMap.codec)
    private fun RBatch.getQueueRetryCountMap() = getMap<UUID, Int>(retryCountMap.name, retryCountMap.codec)
    // endregion

    /**
     * Initialises the per-queue epoch. Sets it to `now` if not already present,
     * then returns the stored value. This is a **blocking** call.
     *
     * @return the epoch timestamp in milliseconds
     */
    @Blocking
    fun initEpochMs(): Long {
        epochMsBucket.setIfAbsent(Instant.now().toEpochMilli())
        return epochMsBucket.get()
    }

    /**
     * Atomically adds [uuid] to the sorted set and stores its [meta] only if
     * the UUID is not already present.
     *
     * @return `true` if the entry was newly added, `false` if it already existed
     */
    suspend fun enqueueIfAbsent(uuid: UUID, meta: QueueEntry, score: RedisQueueScore): Boolean {
        val result = executeAtomicBatch {
            getQueueScoredSet().addIfAbsentAsync(score.packed, uuid)
            getQueueMetaMap().fastPutIfAbsentAsync(uuid, meta)
        }

        return result.responses.first() as Boolean
    }

    /**
     * Removes [uuid] from all queue data structures atomically.
     *
     * @return `true` if the entry was present and removed
     */
    suspend fun dequeue(uuid: UUID): Boolean {
        return batchRemove(uuid)
    }

    /** Returns `true` if [uuid] is in the sorted set. */
    suspend fun isQueued(uuid: UUID): Boolean = scoredSet.containsAsync(uuid).await()
    /** Returns the 0-based rank of [uuid] in the sorted set, or `null` if absent. */
    suspend fun rank(uuid: UUID): Int? = scoredSet.rankAsync(uuid).await()
    /** Returns the [RedisQueueScore] for [uuid], or `null` if absent. */
    suspend fun getScore(uuid: UUID): RedisQueueScore? = RedisQueueScore.optional(scoredSet.getScoreAsync(uuid).await())
    /** Returns the number of entries in the sorted set. */
    suspend fun size(): Int = scoredSet.sizeAsync().await()

    /** Returns the highest-priority (lowest-score) entry, or `null` if the queue is empty. */
    suspend fun top1(): UUID? = scoredSet.entryRangeAsync(0, 0).await().firstOrNull()?.value
    /** Returns the two highest-priority entries. */
    suspend fun top2(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, 1).await()
    /** Returns all entries ordered by score (ascending). */
    suspend fun readAllEntries(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, -1).await()
    /**
     * Returns up to [limit] entries whose score is strictly greater than that of [uuid].
     * Used by skip-entry logic to find the next entry to skip past.
     */
    suspend fun entriesAfter(uuid: UUID, limit: Int): Collection<ScoredEntry<UUID>> {
        val currentScore = getScore(uuid) ?: return emptyList()
        return scoredSet.entryRangeAsync(currentScore.packed, false, Double.MAX_VALUE, true, 0, limit).await()
    }

    /**
     * Atomically increments the retry count for [uuid] and returns the new value.
     */
    suspend fun incrementRetryCount(uuid: UUID): Int {
        return retryCountMap.addAndGetAsync(uuid, 1).await()
    }

    /** Returns the retry count for [uuid], or `null` if not present. */
    suspend fun getRetryCount(uuid: UUID): Int? = retryCountMap.getAsync(uuid).await()

    /** Removes the retry count entry for [uuid]. */
    suspend fun clearRetryCount(uuid: UUID) {
        retryCountMap.removeAsync(uuid).await()
    }

    /** Returns the [QueueEntry] metadata for [uuid], or `null` if not present. */
    suspend fun getMeta(uuid: UUID): QueueEntry? = metaMap.getAsync(uuid).await()

    /** Removes all data (sorted set, meta, last-seen, retry count) for [uuid]. */
    suspend fun removeAllFor(uuid: UUID) {
        batchRemove(uuid)
    }

    /** Records the last-seen timestamp [nowMs] for [uuid]. */
    suspend fun putLastSeen(uuid: UUID, nowMs: Long) {
        lastSeenMap.putAsync(uuid, nowMs).await()
    }

    /** Returns the last-seen timestamp for [uuid], or `null` if not recorded. */
    suspend fun getLastSeen(uuid: UUID): Long? = lastSeenMap.getAsync(uuid).await()

    /** Removes the last-seen record for [uuid]. */
    suspend fun clearLastSeen(uuid: UUID) {
        lastSeenMap.removeAsync(uuid).await()
    }

    /** Returns all last-seen entries as a UUID → epoch-ms map. */
    suspend fun readAllLastSeen(): Map<UUID, Long> = lastSeenMap.readAllMapAsync().await() ?: emptyMap()

    /**
     * Adds or updates the score for [uuid] in the sorted set.
     *
     * @return `true` if the entry was newly added, `false` if it was updated
     */
    suspend fun addOrUpdateScore(uuid: UUID, score: RedisQueueScore): Boolean {
        return scoredSet.addAsync(score.packed, uuid).await()
    }

    private suspend fun batchRemove(uuid: UUID): Boolean {
        try {
            val result = executeAtomicBatch {
                getQueueScoredSet().removeAsync(uuid)
                getQueueMetaMap().removeAsync(uuid)
                getQueueLastSeenMap().removeAsync(uuid)
                getQueueRetryCountMap().removeAsync(uuid)
            }

            return result.responses.first() as Boolean
        } catch (e: Exception) {
            // If the batch fails, we need try to remove the individual elements manually
            runCatching { scoredSet.removeAsync(uuid).await() }
            runCatching { metaMap.removeAsync(uuid).await() }
            runCatching { lastSeenMap.removeAsync(uuid).await() }
            runCatching { retryCountMap.removeAsync(uuid).await() }

            throw e
        }
    }

    /** Deletes the sorted set, meta map, and last-seen map entirely. */
    suspend fun deleteAll() {
        scoredSet.deleteAsync().await()
        metaMap.deleteAsync().await()
        lastSeenMap.deleteAsync().await()
    }

    /** Returns `true` if this queue is currently paused. */
    suspend fun isPaused() = pausedBucket.getAsync().await() == 1

    /**
     * Sets or clears the paused state.
     *
     * @param paused `true` to pause the queue, `false` to resume
     */
    suspend fun setPaused(paused: Boolean) {
        if (paused) {
            pausedBucket.setAsync(1).await()
        } else {
            pausedBucket.deleteAsync().await()
        }
    }
}