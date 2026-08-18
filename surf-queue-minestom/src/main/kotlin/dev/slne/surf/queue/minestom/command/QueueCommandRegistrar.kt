package dev.slne.surf.queue.minestom.command

import com.google.inject.Inject
import dev.slne.minestom.lobby.api.command.CommandRegistrar

/**
 * Registers the queue commands of this plugin.
 */
class QueueCommandRegistrar @Inject constructor() : CommandRegistrar {
    override fun register() {
        queueCommand()
    }
}
