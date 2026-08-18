package dev.slne.surf.queue.client.platform

import dev.slne.surf.api.core.util.requiredService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.*

/**
 * Platform abstraction for the few operations the shared queue logic cannot express
 * on its own.
 *
 * Exactly one implementation is provided per platform and discovered through
 * `ServiceLoader`.
 */
interface QueuePlatform {

    /**
     * Returns the player capacity of the server this plugin runs on.
     */
    fun maxPlayers(): Int

    /**
     * Returns the number of players currently connected to the server this plugin runs on.
     */
    fun onlinePlayerCount(): Int

    /**
     * Returns and forgets the reason [uuid] was most recently rejected by this server.
     *
     * Platforms that cannot observe a rejection reason return [TransferKickReason.OTHER].
     */
    fun consumeKickReason(uuid: UUID): TransferKickReason

    /**
     * Runs [block] on the platform's asynchronous plugin scope.
     */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job

    companion object {
        val instance = requiredService<QueuePlatform>()

        fun get() = instance
    }
}
