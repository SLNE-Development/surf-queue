package dev.slne.surf.queue.velocity.permission

object SurfQueuePermissions {
    private const val PREFIX = "surf.queue."

    private const val COMMAND_PREFIX = PREFIX + "command."
    const val COMMAND_QUEUE = COMMAND_PREFIX + "queue"

    const val COMMAND_METRICS = COMMAND_PREFIX + "metrics"
}