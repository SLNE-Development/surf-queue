package dev.slne.surf.queue.paper.queue

import dev.slne.surf.queue.api.SurfQueue

interface PaperQueueCommon : SurfQueue {
    suspend fun delete()
    suspend fun forceCleanup()
}