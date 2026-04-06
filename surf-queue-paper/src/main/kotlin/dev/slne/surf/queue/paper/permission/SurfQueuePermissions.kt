package dev.slne.surf.queue.paper.permission

object SurfQueuePermissions {
    private const val PREFIX = "surf.queue."

    private const val COMMAND_PREFIX = PREFIX + "command."
    const val COMMAND_QUEUE = COMMAND_PREFIX + "queue"

    const val COMMAND_STATUS = COMMAND_PREFIX + "status"
    const val COMMAND_PAUSE = COMMAND_PREFIX + "pause"
    const val COMMAND_LIST = COMMAND_PREFIX + "list"
    const val COMMAND_ENQUEUE = COMMAND_PREFIX + "enqueue"
    const val COMMAND_DEQUEUE = COMMAND_PREFIX + "dequeue"
    const val COMMAND_POSITION = COMMAND_PREFIX + "position"
    const val COMMAND_CLEAR = COMMAND_PREFIX + "clear"
    const val COMMAND_TICK = COMMAND_PREFIX + "tick"
    const val COMMAND_TRANSFER_TASK = COMMAND_PREFIX + "transfertask"
    const val COMMAND_CLEANUP = COMMAND_PREFIX + "cleanup"
    const val COMMAND_INFO = COMMAND_PREFIX + "info"
}
