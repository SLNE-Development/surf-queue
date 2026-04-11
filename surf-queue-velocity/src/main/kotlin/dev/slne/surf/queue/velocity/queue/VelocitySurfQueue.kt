package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import java.time.Instant
import java.util.*

class VelocitySurfQueue(serverName: String) : AbstractQueue(serverName) {
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