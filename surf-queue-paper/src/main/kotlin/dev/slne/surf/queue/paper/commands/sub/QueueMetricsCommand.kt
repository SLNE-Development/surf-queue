package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.metrics.QueueMetrics
import dev.slne.surf.queue.client.metrics.QueueMetricsLogger
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions

fun CommandAPICommand.metricsCommand() = subcommand("metrics") {
    withPermission(PaperQueuePermissions.COMMAND_METRICS)

    subcommand("startLogging") {
        anyExecutor { source, arguments ->
            QueueMetricsLogger.stop()
            QueueMetricsLogger.start()
            source.sendMessage(QueueMessages.startedLoggingMetrics())
        }
    }

    subcommand("stopLogging") {
        anyExecutor { source, arguments ->
            QueueMetricsLogger.stop()
            source.sendMessage(QueueMessages.stoppedLoggingMetrics())
        }
    }

    subcommand("snapshot") {
        anyExecutorSuspend { sender, _ ->
            val snapshot = QueueMetrics.snapshot()
            val queueSizes = QueueMetrics.collectQueueSizes()

            QueueMessages.metricsSnapshot(snapshot, queueSizes).forEach(sender::sendMessage)
        }
    }
}
