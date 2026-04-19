package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractTickableQueue
import dev.slne.surf.queue.common.queue.tick.QueueScheduler
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import java.time.Instant
import java.util.*

class VelocityQueueImpl(serverName: String, scheduler: QueueScheduler) : AbstractTickableQueue(serverName, scheduler) {
    val display = QueueDisplay(this)

    suspend fun markPlayerReconnected(uuid: UUID) {
        store.clearLastSeen(uuid)
    }

    suspend fun markPlayerDisconnected(uuid: UUID) {
        store.putLastSeen(uuid, Instant.now().toEpochMilli())
    }

    override suspend fun tick() {
        super.tick()
        SafeQueueTick.tickSafe(this, "display") { display.tick() }
    }
}