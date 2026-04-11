package dev.slne.surf.queue.velocity.permission

/**
 * Permission string constants for Velocity queue commands.
 *
 * All permissions are prefixed with `surf.queue.command.`.
 */
object SurfQueuePermissions {
    private const val PREFIX = "surf.queue."

    private const val COMMAND_PREFIX = PREFIX + "command."
    const val COMMAND_QUEUE = COMMAND_PREFIX + "queue"
    const val COMMAND_PAUSE = COMMAND_PREFIX + "pause"
}