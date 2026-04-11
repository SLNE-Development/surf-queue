package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.queue.common.queue.tick.SafeQueueTick
import java.time.Instant
import java.util.*

/**
 * Velocity-specific queue implementation.
 *
 * Extends [AbstractQueue] with a [QueueDisplay] for action bar updates and
 * player connect/disconnect tracking for grace-period management.
 *
 * @param serverName the name of the target server
 */
class VelocityQueueImpl(serverName: String) : AbstractQueue(serverName) {
    val display = QueueDisplay(this)

    /**
     * Clears the last-seen record for [uuid], indicating the player is back online
     * and their grace period should be cancelled.
     */
    suspend fun markPlayerReconnected(uuid: UUID) {
        store.clearLastSeen(uuid)
    }

    /**
     * Records the current time as the last-seen timestamp for [uuid],
     * starting their grace period.
     */
    suspend fun markPlayerDisconnected(uuid: UUID) {
        store.putLastSeen(uuid, Instant.now().toEpochMilli())
    }

    override suspend fun tick() {
        super.tick()
        SafeQueueTick.tickSafe(this, "display") { display.tick() }
    }
}