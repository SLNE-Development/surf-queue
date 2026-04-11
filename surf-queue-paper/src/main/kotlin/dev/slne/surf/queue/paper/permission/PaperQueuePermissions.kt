package dev.slne.surf.queue.paper.permission

import dev.slne.surf.surfapi.bukkit.api.permission.PermissionRegistry

/**
 * Registry of permission nodes used by Paper queue commands.
 *
 * All permissions are prefixed with `surf.queue.command`.
 */
object PaperQueuePermissions : PermissionRegistry() {
    private const val PREFIX = "surf.queue."
    private const val COMMAND_PREFIX = PREFIX + "command"

    val COMMAND_QUEUE = create("$COMMAND_PREFIX.queue")
    val COMMAND_CLEANUP = create("$COMMAND_QUEUE.cleanup")
    val COMMAND_CLEAR = create("$COMMAND_QUEUE.clear")
    val COMMAND_DEQUEUE = create("$COMMAND_QUEUE.dequeue")
    val COMMAND_ENQUEUE = create("$COMMAND_QUEUE.enqueue")
    val COMMAND_INFO = create("$COMMAND_QUEUE.info")
    val COMMAND_LIST = create("$COMMAND_QUEUE.list")
    val COMMAND_PAUSE = create("$COMMAND_QUEUE.pause")
    val COMMAND_METRICS = create("$COMMAND_PREFIX.metrics")
}