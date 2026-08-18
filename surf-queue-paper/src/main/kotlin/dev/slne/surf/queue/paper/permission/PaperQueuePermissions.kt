package dev.slne.surf.queue.paper.permission

import dev.slne.surf.api.paper.permission.PermissionRegistry
import dev.slne.surf.queue.client.permission.QueuePermissions

object PaperQueuePermissions : PermissionRegistry() {
    val COMMAND_QUEUE = create(QueuePermissions.COMMAND_QUEUE)
    val COMMAND_CLEANUP = create(QueuePermissions.COMMAND_CLEANUP)
    val COMMAND_CLEAR = create(QueuePermissions.COMMAND_CLEAR)
    val COMMAND_DEQUEUE = create(QueuePermissions.COMMAND_DEQUEUE)
    val COMMAND_ENQUEUE = create(QueuePermissions.COMMAND_ENQUEUE)
    val COMMAND_FIX = create(QueuePermissions.COMMAND_FIX)
    val COMMAND_INFO = create(QueuePermissions.COMMAND_INFO)
    val COMMAND_LIST = create(QueuePermissions.COMMAND_LIST)
    val COMMAND_PAUSE = create(QueuePermissions.COMMAND_PAUSE)
    val COMMAND_METRICS = create(QueuePermissions.COMMAND_METRICS)
}
