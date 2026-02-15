package dev.slne.surf.queue.velocity.command.pause

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.velocity.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.api.queue
import dev.slne.surf.queue.velocity.permission.SurfQueuePermissions
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import dev.slne.surf.surfapi.velocity.api.command.executors.anyExecutorSuspend

fun CommandTree.queuePauseCommand() = literalArgument("pause") {
    withPermission(SurfQueuePermissions.COMMAND_PAUSE)

    surfBackendServerArgument("server") {
        literalArgument("pause") {
            anyExecutorSuspend { source, arguments ->
                val server: SurfServer by arguments
                server.queue().pause()

                source.sendText {
                    appendSuccessPrefix()
                    success("Paused queue")
                }
            }
        }

        literalArgument("resume") {
            anyExecutorSuspend { source, arguments ->
                val server: SurfServer by arguments
                server.queue().resume()

                source.sendText {
                    appendSuccessPrefix()
                    success("Resumed queue")
                }
            }
        }

        literalArgument("status") {
            anyExecutorSuspend { source, arguments ->
                val server: SurfServer by arguments
                val isPaused = server.queue().isPaused()

                source.sendText {
                    appendInfoPrefix()
                    if (isPaused) {
                        info("The queue is currently paused")
                    } else {
                        info("The queue is currently running")
                    }
                }
            }
        }
    }
}