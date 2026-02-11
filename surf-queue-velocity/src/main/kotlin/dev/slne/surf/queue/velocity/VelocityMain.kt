package dev.slne.surf.queue.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import dev.slne.surf.queue.common.SurfQueueInstance
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class VelocityMain @Inject constructor(val proxy: ProxyServer) {
    init {
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