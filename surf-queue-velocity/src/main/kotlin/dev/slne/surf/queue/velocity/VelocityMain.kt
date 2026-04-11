package dev.slne.surf.queue.velocity

import com.github.shynixn.mccoroutine.velocity.SuspendingPluginContainer
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import dev.slne.surf.queue.common.QueueInstance
import kotlinx.coroutines.runBlocking

/**
 * Velocity proxy plugin entry point for surf-queue.
 *
 * Initialises the [SuspendingPluginContainer], sets the global [plugin] reference,
 * and delegates lifecycle events to [QueueInstance].
 *
 * @param proxy the Velocity [ProxyServer] instance
 * @param container the plugin's [PluginContainer]
 * @param suspendingPluginContainer MCCoroutine's suspending plugin container
 */
class VelocityMain @Inject constructor(
    val proxy: ProxyServer,
    val container: PluginContainer,
    suspendingPluginContainer: SuspendingPluginContainer,
) {
    init {
        suspendingPluginContainer.initialize(this)
        plugin = this
        runBlocking {
            QueueInstance.get().load()
        }
    }

    @Subscribe
    suspend fun onProxyInitialize(event: ProxyInitializeEvent) {
        QueueInstance.get().enable()
    }


    @Subscribe
    suspend fun onProxyShutdown(event: ProxyShutdownEvent) {
        QueueInstance.get().disable()
    }
}

/** Global accessor for the [VelocityMain] plugin instance. */
lateinit var plugin: VelocityMain
    private set