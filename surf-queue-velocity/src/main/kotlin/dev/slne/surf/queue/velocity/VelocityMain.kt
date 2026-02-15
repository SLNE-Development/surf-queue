package dev.slne.surf.queue.velocity

import com.github.shynixn.mccoroutine.velocity.SuspendingPluginContainer
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.surfapi.velocity.api.metrics.Metrics
import kotlinx.coroutines.runBlocking

class VelocityMain @Inject constructor(
    val proxy: ProxyServer,
    val container: PluginContainer,
    val suspendingPluginContainer: SuspendingPluginContainer,
    val metricsFactory: Metrics.Factory
) {
    init {
        suspendingPluginContainer.initialize(this)
        plugin = this
        runBlocking {
            SurfQueueInstance.get().load()
        }
    }

    @Subscribe
    suspend fun onProxyInitialize(event: ProxyInitializeEvent) {
        SurfQueueInstance.get().enable()
    }


    @Subscribe
    suspend fun onProxyShutdown(event: ProxyShutdownEvent) {
        SurfQueueInstance.get().disable()
    }
}

lateinit var plugin: VelocityMain
    private set