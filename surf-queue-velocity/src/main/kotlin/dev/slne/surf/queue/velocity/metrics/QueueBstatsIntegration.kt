package dev.slne.surf.queue.velocity.metrics

import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.velocity.plugin
import dev.slne.surf.queue.velocity.queue.VelocitySurfQueue
import dev.slne.surf.surfapi.core.api.util.logger
import dev.slne.surf.surfapi.velocity.api.metrics.Metrics
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

object QueueBstatsIntegration {
    private val log = logger()

    private var lastTransfers = AtomicLong(0)
    private var lastEnqueues = AtomicLong(0)
    private var lastFailedTransfers = AtomicLong(0)

    fun setup(metricsFactory: Metrics.Factory) {
        val metrics = metricsFactory.make(plugin, 29544)

        metrics.addCustomChart(Metrics.SimplePie("queue_count") {
            try {
                RedisQueueService.get().getAll().size.toString()
            } catch (_: Exception) {
                "0"
            }
        })

        metrics.addCustomChart(Metrics.SingleLineChart("total_transfers") {
            val current = QueueMetrics.totalTransfers.get()
            val last = lastTransfers.getAndSet(current)
            (current - last).toInt()
        })

        metrics.addCustomChart(Metrics.SingleLineChart("total_enqueues") {
            val current = QueueMetrics.totalEnqueues.get()
            val last = lastEnqueues.getAndSet(current)
            (current - last).toInt()
        })

        metrics.addCustomChart(Metrics.SingleLineChart("total_failed_transfers") {
            val current = QueueMetrics.totalFailedTransfers.get()
            val last = lastFailedTransfers.getAndSet(current)
            (current - last).toInt()
        })

        metrics.addCustomChart(Metrics.SingleLineChart("total_queued_players") {
            try {
                runBlocking {
                    QueueMetrics.collectQueueSizes().values.sum()
                }
            } catch (_: Exception) {
                0
            }
        })

        metrics.addCustomChart(Metrics.AdvancedPie("transfers_per_queue") {
            try {
                RedisQueueService.get().getAll()
                    .filterIsInstance<VelocitySurfQueue>()
                    .associate { it.serverName to QueueMetrics.getTransfersFor(it.serverName).toInt() }
                    .filterValues { it > 0 }
            } catch (_: Exception) {
                emptyMap()
            }
        })

        metrics.addCustomChart(Metrics.AdvancedPie("queue_sizes") {
            try {
                runBlocking {
                    QueueMetrics.collectQueueSizes()
                }
            } catch (_: Exception) {
                emptyMap()
            }
        })

        log.atInfo()
            .log("bStats integration initialized")
    }
}