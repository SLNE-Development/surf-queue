package dev.slne.surf.queue.velocity.command.metrics

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.velocity.metrics.QueueMetrics
import dev.slne.surf.queue.velocity.metrics.QueueMetricsLogger
import dev.slne.surf.queue.velocity.permission.SurfQueuePermissions
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import dev.slne.surf.surfapi.velocity.api.command.executors.anyExecutorSuspend

fun CommandTree.metricsCommand() = literalArgument("metrics") {
    withPermission(SurfQueuePermissions.COMMAND_METRICS)

    literalArgument("startLogging") {
        anyExecutor { source, arguments ->
            QueueMetricsLogger.stop()
            QueueMetricsLogger.start()
            source.sendText {
                appendSuccessPrefix()
                success("Started logging metrics")
            }
        }
    }

    literalArgument("stopLogging") {
        anyExecutor { source, arguments ->
            QueueMetricsLogger.stop()
            source.sendText {
                appendSuccessPrefix()
                success("Stopped logging metrics")
            }
        }
    }

    literalArgument("snapshot") {
        anyExecutorSuspend { sender, _ ->
            val snapshot = QueueMetrics.snapshot()
            val queueSizes = QueueMetrics.collectQueueSizes()

            sender.sendText {
                appendSuccessPrefix()
                success("=== Queue Metrics ===")
            }
            sender.sendText {
                success("Transfers: ")
                variableValue("${snapshot.totalTransfers}")
                success(" successful, ")
                variableValue("${snapshot.totalFailedTransfers}")
                success(" failed (")
                variableValue(String.format("%.1f%%", snapshot.transferSuccessRate * 100))
                success(")")
            }
            sender.sendText {
                success("Enqueues: ")
                variableValue("${snapshot.totalEnqueues}")
                success(" | Dequeues: ")
                variableValue("${snapshot.totalDequeues}")
            }
            sender.sendText {
                success("Grace Expiries: ")
                variableValue("${snapshot.totalGraceExpiries}")
                success(" | Retry Exhausted: ")
                variableValue("${snapshot.totalRetryExhausted}")
            }
            sender.sendText {
                success("Lock: ")
                variableValue("${snapshot.totalLockAcquired}/${snapshot.totalLockAttempts}")
                success(" (")
                variableValue(String.format("%.1f%%", snapshot.lockAcquisitionRate * 100))
                success(")")
            }
            sender.sendText {
                success("Cleanup: ")
                variableValue("${snapshot.totalCleanupCycles}")
                success(" cycles, ")
                variableValue("${snapshot.totalCleanupRemovals}")
                success(" removals")
            }
            sender.sendText {
                success("Ticks: ")
                variableValue("${snapshot.totalTicks}")
            }

            if (queueSizes.isNotEmpty()) {
                sender.sendText {
                    appendSuccessPrefix()
                    success("--- Per Queue ---")
                }
                for ((name, size) in queueSizes) {
                    val perQueue = snapshot.perQueue[name]
                    sender.sendText {
                        variableValue(name)
                        success(": size=")
                        variableValue("$size")
                        success(", transfers=")
                        variableValue("${perQueue?.transfers ?: 0}")
                        success(", failed=")
                        variableValue("${perQueue?.failedTransfers ?: 0}")
                        success(", skips=")
                        variableValue("${perQueue?.skips ?: 0}")
                    }
                }
            }
        }
    }
}