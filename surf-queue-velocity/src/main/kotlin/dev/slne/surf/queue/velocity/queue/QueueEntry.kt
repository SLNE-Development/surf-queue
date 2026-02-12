package dev.slne.surf.queue.velocity.queue

import java.io.Serial
import java.io.Serializable
import java.util.*

data class QueueEntry(
    val uuid: UUID,
    val addedAt: Long,
    val priority: Int,
) : Serializable {

    companion object {
        @Serial
        const val serialVersionUID = 1L
    }
}
