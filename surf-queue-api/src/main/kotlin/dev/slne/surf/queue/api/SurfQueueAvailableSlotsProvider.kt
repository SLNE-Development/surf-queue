package dev.slne.surf.queue.api

import dev.slne.surf.core.api.common.server.SurfServer

/**
 * Provides the number of currently available slots for a given [SurfServer].
 */
interface SurfQueueAvailableSlotsProvider {

    /**
     * Returns the number of currently available slots on the given [server].
     *
     * @param server the server whose available slots should be resolved
     * @return the amount of free slots on the server
     */
    suspend fun getAvailableSlots(server: SurfServer): Int

    /**
     * Holds global access to the currently configured [SurfQueueAvailableSlotsProvider].
     */
    companion object {
        private lateinit var instance: SurfQueueAvailableSlotsProvider

        /**
         * Sets the global [SurfQueueAvailableSlotsProvider] instance.
         *
         * @param provider the provider instance to register
         */
        fun set(provider: SurfQueueAvailableSlotsProvider) {
            instance = provider
        }

        /**
         * Returns the globally configured [SurfQueueAvailableSlotsProvider] instance.
         *
         * @return the registered provider instance
         * @throws UninitializedPropertyAccessException if no provider has been set yet
         */
        fun get() = instance
    }
}