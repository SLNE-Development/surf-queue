package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.queue.common.queue.RedisQueueLockResetResult

interface PaperQueueCommon : SurfQueue {
    suspend fun delete()
    suspend fun fix(): QueueFixResult
    suspend fun forceCleanup()
}

data class QueueFixResult(
    val sizeBefore: Int,
    val sizeAfter: Int,
    val wasPaused: Boolean,
    val lockReset: RedisQueueLockResetResult
) {
    val removedEntries = sizeBefore - sizeAfter
}
