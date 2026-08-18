package dev.slne.surf.queue.client.permission

/**
 * Platform-neutral registry of all permission node strings used by surf-queue.
 */
object QueuePermissions {
    private const val PREFIX = "surf.queue."
    private const val COMMAND_PREFIX = PREFIX + "command"

    const val COMMAND_QUEUE = "$COMMAND_PREFIX.queue"
    const val COMMAND_CLEANUP = "$COMMAND_QUEUE.cleanup"
    const val COMMAND_CLEAR = "$COMMAND_QUEUE.clear"
    const val COMMAND_DEQUEUE = "$COMMAND_QUEUE.dequeue"
    const val COMMAND_ENQUEUE = "$COMMAND_QUEUE.enqueue"
    const val COMMAND_FIX = "$COMMAND_QUEUE.fix"
    const val COMMAND_INFO = "$COMMAND_QUEUE.info"
    const val COMMAND_LIST = "$COMMAND_QUEUE.list"
    const val COMMAND_PAUSE = "$COMMAND_QUEUE.pause"
    const val COMMAND_METRICS = "$COMMAND_PREFIX.metrics"
}
