package dev.slne.surf.queue.velocity

import com.google.auto.service.AutoService
import dev.slne.surf.queue.common.SurfQueueInstance
import dev.slne.surf.queue.common.queue.AbstractSurfQueue
import dev.slne.surf.queue.velocity.command.queueCommand
import dev.slne.surf.queue.velocity.listener.QueuePlayerListener
import dev.slne.surf.queue.velocity.metrics.QueueBstatsIntegration
import dev.slne.surf.queue.velocity.metrics.QueueMetricsLogger
import dev.slne.surf.queue.velocity.queue.QueueTickTask
import dev.slne.surf.queue.velocity.queue.VelocitySurfQueue
import dev.slne.surf.surfapi.core.api.util.logger

@AutoService(SurfQueueInstance::class)
class VelocitySurfQueueInstance : SurfQueueInstance() {

    override suspend fun enable() {
        super.enable()

        plugin.proxy.eventManager.register(plugin, QueuePlayerListener)
        QueueTickTask.startTransferring()
        queueCommand()

        try {
            QueueBstatsIntegration.setup(plugin.metricsFactory)
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to initialize bStats integration")
        }
    }

    override suspend fun disable() {
        super.disable()

        QueueMetricsLogger.stop()
        QueueTickTask.shutdown()
    }

    override fun createQueue(serverName: String): AbstractSurfQueue {
        return VelocitySurfQueue(serverName)
    }

    companion object {
        private val log = logger()
    }
}