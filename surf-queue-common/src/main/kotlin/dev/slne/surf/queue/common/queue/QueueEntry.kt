package dev.slne.surf.queue.common.queue

import java.io.Serial
import java.io.Serializable
import java.util.UUID

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