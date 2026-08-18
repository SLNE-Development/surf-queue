package dev.slne.surf.queue.minestom.command.sub

import dev.slne.minestom.lobby.api.command.commandapi.CommandAPICommand
import dev.slne.minestom.lobby.api.command.commandapi.dsl.anyExecutor
import dev.slne.minestom.lobby.api.command.commandapi.dsl.anyExecutorSuspend
import dev.slne.minestom.lobby.api.command.commandapi.dsl.subcommand
import dev.slne.surf.queue.client.command.QueueMessages
import dev.slne.surf.queue.client.metrics.QueueMetrics
import dev.slne.surf.queue.client.metrics.QueueMetricsLogger
import dev.slne.surf.queue.client.permission.QueuePermissions

fun CommandAPICommand.metricsCommand(): CommandAPICommand = withSubcommand(
    subcommand("metrics") {
        withPermission(QueuePermissions.COMMAND_METRICS)

        withSubcommand(subcommand("startLogging") {
            anyExecutor { source, arguments ->
                QueueMetricsLogger.stop()
                QueueMetricsLogger.start()
                source.sendMessage(QueueMessages.startedLoggingMetrics())
            }
        })

        withSubcommand(subcommand("stopLogging") {
            anyExecutor { source, arguments ->
                QueueMetricsLogger.stop()
                source.sendMessage(QueueMessages.stoppedLoggingMetrics())
            }
        })

        withSubcommand(subcommand("snapshot") {
            anyExecutorSuspend { sender, _ ->
                val snapshot = QueueMetrics.snapshot()
                val queueSizes = QueueMetrics.collectQueueSizes()

                QueueMessages.metricsSnapshot(snapshot, queueSizes).forEach(sender::sendMessage)
            }
        })
    }
)
