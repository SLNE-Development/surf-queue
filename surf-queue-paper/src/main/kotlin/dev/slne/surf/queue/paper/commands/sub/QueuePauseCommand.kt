package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.api.core.messages.adventure.sendText

fun CommandAPICommand.queuePause() = subcommand("pause") {
    withPermission(PaperQueuePermissions.COMMAND_PAUSE)

    subcommand("pause") {
        surfBackendServerArgument("server", optional = true)
        anyExecutorSuspend { sender, arguments ->
            val server: SurfServer? by arguments
            val serverName = server?.name ?: SurfServer.current().name
            val queue = RedisQueueService.get().getQueueByName(serverName)
            queue.pause()

            sender.sendText {
                appendSuccessPrefix()
                success("Paused queue")
            }
        }
    }

    subcommand("resume") {
        surfBackendServerArgument("server", optional = true)
        anyExecutorSuspend { sender, arguments ->
            val server: SurfServer? by arguments
            val serverName = server?.name ?: SurfServer.current().name
            val queue = RedisQueueService.get().getQueueByName(serverName)
            queue.resume()
            sender.sendText {
                appendSuccessPrefix()
                success("Resumed queue")
            }
        }
    }

    subcommand("status") {
        surfBackendServerArgument("server", optional = true)
        anyExecutorSuspend { sender, arguments ->
            val server: SurfServer? by arguments
            val serverName = server?.name ?: SurfServer.current().name
            val queue = RedisQueueService.get().getQueueByName(serverName)
            val isPaused = queue.isPaused()
            sender.sendText {
                appendSuccessPrefix()
                if (isPaused) {
                    info("The queue is currently paused")
                } else {
                    info("The queue is currently running")
                }
            }
        }
    }
}