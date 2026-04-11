package dev.slne.surf.queue.common.queue

import java.io.Serial
import java.io.Serializable
import java.util.UUID

/**
 * Immutable data class representing a queued player entry.
 *
 * @property uuid the player's unique identifier
 * @property addedAt the epoch millisecond timestamp when the player was enqueued
 * @property priority the player's queue priority (higher = more important)
 */
data class QueueEntry(
    val uuid: UUID,
    val addedAt: Long,
    val priority: Int,
) : Serializable {

    companion object {
        @Serial
        const val serialVersionUID = 2L
    }
}