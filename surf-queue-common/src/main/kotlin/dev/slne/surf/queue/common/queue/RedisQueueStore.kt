package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.queue.entry.QueueEntryCodec
import dev.slne.surf.queue.common.queue.entry.QueueEntry
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
import java.time.Instant
import java.util.*

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

    suspend fun initEpochMs(): Long {
        epochMsBucket.setIfAbsentAsync(Instant.now().toEpochMilli()).await()
        return epochMsBucket.getAsync().await()
    }

    suspend fun enqueueIfAbsent(uuid: UUID, meta: QueueEntry, score: RedisQueueScore): Boolean {
        val result = executeAtomicBatch {
            getQueueScoredSet().addIfAbsentAsync(score.packed, uuid)
            getQueueMetaMap().fastPutIfAbsentAsync(uuid, meta)
        }

        return result.responses.first() as Boolean
    }

    suspend fun dequeue(uuid: UUID): Boolean {
        return batchRemove(uuid)
    }

    suspend fun isQueued(uuid: UUID): Boolean = scoredSet.containsAsync(uuid).await()
    suspend fun rank(uuid: UUID): Int? = scoredSet.rankAsync(uuid).await()
    suspend fun getScore(uuid: UUID): RedisQueueScore? = RedisQueueScore.optional(scoredSet.getScoreAsync(uuid).await())
    suspend fun size(): Int = scoredSet.sizeAsync().await()

    suspend fun top1(): UUID? = scoredSet.entryRangeAsync(0, 0).await().firstOrNull()?.value
    suspend fun top2(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, 1).await()
    suspend fun readAllEntries(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, -1).await()
    suspend fun entriesAfter(uuid: UUID, limit: Int): Collection<ScoredEntry<UUID>> {
        val currentScore = getScore(uuid) ?: return emptyList()
        return scoredSet.entryRangeAsync(currentScore.packed, false, Double.MAX_VALUE, true, 0, limit).await()
    }

    suspend fun incrementRetryCount(uuid: UUID): Int {
        return retryCountMap.addAndGetAsync(uuid, 1).await()
    }

    suspend fun getRetryCount(uuid: UUID): Int? = retryCountMap.getAsync(uuid).await()

    suspend fun clearRetryCount(uuid: UUID) {
        retryCountMap.removeAsync(uuid).await()
    }

    suspend fun getMeta(uuid: UUID): QueueEntry? = metaMap.getAsync(uuid).await()

    suspend fun removeAllFor(uuid: UUID) {
        batchRemove(uuid)
    }

    suspend fun putLastSeen(uuid: UUID, nowMs: Long) {
        lastSeenMap.putAsync(uuid, nowMs).await()
    }

    suspend fun getLastSeen(uuid: UUID): Long? = lastSeenMap.getAsync(uuid).await()

    suspend fun clearLastSeen(uuid: UUID) {
        lastSeenMap.removeAsync(uuid).await()
    }

    suspend fun readAllLastSeen(): Map<UUID, Long> = lastSeenMap.readAllMapAsync().await() ?: emptyMap()

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

    suspend fun deleteAll() {
        scoredSet.deleteAsync().await()
        metaMap.deleteAsync().await()
        lastSeenMap.deleteAsync().await()
        retryCountMap.deleteAsync().await()
        pausedBucket.deleteAsync().await()
    }

    suspend fun isPaused() = pausedBucket.getAsync().await() == 1

    suspend fun setPaused(paused: Boolean) {
        if (paused) {
            pausedBucket.setAsync(1).await()
        } else {
            pausedBucket.deleteAsync().await()
        }
    }
}