package dev.slne.surf.queue.paper.metrics

import com.github.shynixn.mccoroutine.folia.launch
import dev.slne.surf.queue.paper.plugin
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.minutes

/**
 * Periodic logger that outputs a [QueueMetricsSnapshot] and per-queue sizes
 * to the server log every 5 minutes.
 *
 * Controlled via the `/squeue metrics startLogging` and `stopLogging` commands.
 */
object QueueMetricsLogger {
    private val log = logger()
    private var job: Job? = null

    /** Starts the periodic logging coroutine. */
    fun start() {
        job = plugin.launch {
            while (isActive) {
                delay(5.minutes)
                try {
                    val snapshot = QueueMetrics.snapshot()
                    val queueSizes = QueueMetrics.collectQueueSizes()

                    log.atInfo()
                        .log(
                            "Queue Metrics: transfers=%d, failed=%d, enqueues=%d, dequeues=%d, " +
                                    "graceExpiries=%d, retryExhausted=%d, lockRate=%.1f%%, cleanupCycles=%d",
                            snapshot.totalTransfers,
                            snapshot.totalFailedTransfers,
                            snapshot.totalEnqueues,
                            snapshot.totalDequeues,
                            snapshot.totalGraceExpiries,
                            snapshot.totalRetryExhausted,
                            snapshot.lockAcquisitionRate * 100,
                            snapshot.totalCleanupCycles
                        )

                    for ((serverName, size) in queueSizes) {
                        val perQueue = snapshot.perQueue[serverName]
                        log.atInfo().log(
                            "  Queue [%s]: size=%d, transfers=%d, failed=%d, skips=%d",
                            serverName,
                            size,
                            perQueue?.transfers ?: 0,
                            perQueue?.failedTransfers ?: 0,
                            perQueue?.skips ?: 0
                        )
                    }
                } catch (e: Exception) {
                    log.atWarning()
                        .withCause(e)
                        .log("Failed to log metrics snapshot")
                }
            }
        }
    }

    /** Stops the periodic logging coroutine if running. */
    fun stop() {
        job?.cancel()
        job = null
    }
}