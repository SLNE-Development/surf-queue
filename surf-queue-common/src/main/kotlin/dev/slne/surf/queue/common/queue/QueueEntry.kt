package dev.slne.surf.queue.common.queue

import java.io.Serial
import java.io.Serializable
import java.util.*

data class QueueEntry(
    val uuid: UUID,
    val addedAt: Long,
    val priority: Int,
) : Serializable, Comparable<QueueEntry> {
    var lastPlayerSeenMark: Long? = null

    override fun compareTo(other: QueueEntry): Int {
        val priorityDiff = priority - other.priority
        return if (priorityDiff != 0) priorityDiff else addedAt.compareTo(other.addedAt)
    }

    companion object {
        @Serial
        const val serialVersionUID = 1L
    }
}
