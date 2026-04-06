package dev.slne.surf.queue.common.queue

import dev.slne.surf.queue.common.queue.codec.QueueEntryCodec
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.codec.UUIDCodec
import dev.slne.surf.redis.libs.redisson.api.BatchOptions
import dev.slne.surf.redis.libs.redisson.client.codec.IntegerCodec
import dev.slne.surf.redis.libs.redisson.client.codec.LongCodec
import dev.slne.surf.redis.libs.redisson.client.protocol.ScoredEntry
import dev.slne.surf.redis.libs.redisson.codec.CompositeCodec
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.Blocking
import java.time.Instant
import java.util.*

class RedisQueueStore(private val keys: RedisQueueKeys) {
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

    @Blocking
    fun initEpochMs(): Long {
        epochMsBucket.setIfAbsent(Instant.now().toEpochMilli())
        return epochMsBucket.get()
    }

    suspend fun enqueueIfAbsent(uuid: UUID, meta: QueueEntry, score: Double): Boolean {
        val batch = redisApi.redisson.createBatch(atomicBatchOptions())

        val addAsync = batch.getScoredSortedSet<UUID>(scoredSet.name, scoredSet.codec)
            .addIfAbsentAsync(score, uuid)
        batch.getMap<UUID, QueueEntry>(metaMap.name, metaMap.codec)
            .fastPutIfAbsentAsync(uuid, meta)

        batch.executeAsync().await()
        return addAsync.await()
    }

    suspend fun dequeue(uuid: UUID): Boolean {
       return batchRemove(uuid)
    }

    suspend fun isQueued(uuid: UUID): Boolean = scoredSet.containsAsync(uuid).await()
    suspend fun rank(uuid: UUID): Int? = scoredSet.rankAsync(uuid).await()
    suspend fun getScore(uuid: UUID): Double? = scoredSet.getScoreAsync(uuid).await()
    suspend fun size(): Int = scoredSet.sizeAsync().await()

    suspend fun top1(): UUID? = scoredSet.entryRangeAsync(0, 0).await().firstOrNull()?.value
    suspend fun top2(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, 1).await()
    suspend fun readAllEntries(): Collection<ScoredEntry<UUID>> = scoredSet.entryRangeAsync(0, -1).await()
    suspend fun entriesAfter(uuid: UUID, limit: Int): Collection<ScoredEntry<UUID>> {
        val currentScore = getScore(uuid) ?: return emptyList()
        return scoredSet.entryRangeAsync(currentScore, false, Double.MAX_VALUE, true, 0, limit).await()
    }

    suspend fun incrementRetryCount(uuid: UUID): Int {
        return retryCountMap.addAndGetAsync(uuid, 1).await()
    }

    suspend fun clearRetryCount(uuid: UUID) {
        retryCountMap.removeAsync(uuid).await()
    }

    suspend fun getRetryCount(uuid: UUID): Int? = retryCountMap.getAsync(uuid).await()

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

    suspend fun addOrUpdateScore(uuid: UUID, score: Double): Boolean {
        return scoredSet.addAsync(score, uuid).await()
    }

    private suspend fun batchRemove(uuid: UUID): Boolean {
        val batch = redisApi.redisson.createBatch(atomicBatchOptions())

        val removeAsync = batch.getScoredSortedSet<UUID>(scoredSet.name, scoredSet.codec)
            .removeAsync(uuid)
        batch.getMap<UUID, QueueEntry>(metaMap.name, metaMap.codec)
            .removeAsync(uuid)
        batch.getMap<UUID, Long>(lastSeenMap.name, lastSeenMap.codec)
            .removeAsync(uuid)
        batch.getMap<UUID, Int>(retryCountMap.name, retryCountMap.codec)
            .removeAsync(uuid)

        batch.executeAsync().await()
        try {
            batch.executeAsync().await()
            return removeAsync.await()
        } catch (e: Exception) {
            // If the batch fails, we need to remove the individual elements manually
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