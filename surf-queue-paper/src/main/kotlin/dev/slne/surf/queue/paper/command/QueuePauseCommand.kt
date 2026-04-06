package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit

fun CommandTree.queuePauseCommand() = literalArgument("pause") {
    withPermission(SurfQueuePermissions.COMMAND_PAUSE)

    literalArgument("pause") {
        anyExecutorSuspend { source, _ ->
            @Suppress("DEPRECATION")
            val queue = RedisQueueService.get().get(Bukkit.getServerName())
            queue.pause()

            source.sendText {
                appendSuccessPrefix()
                success("Queue paused.")
            }
        }
    }

    literalArgument("resume") {
        anyExecutorSuspend { source, _ ->
            @Suppress("DEPRECATION")
            val queue = RedisQueueService.get().get(Bukkit.getServerName())
            queue.resume()

            source.sendText {
                appendSuccessPrefix()
                success("Queue resumed.")
            }
        }
    }

    literalArgument("status") {
        anyExecutorSuspend { source, _ ->
            @Suppress("DEPRECATION")
            val queue = RedisQueueService.get().get(Bukkit.getServerName())
            val isPaused = queue.isPaused()

            source.sendText {
                appendInfoPrefix()
                if (isPaused) {
                    info("The queue is currently paused.")
                } else {
                    info("The queue is currently running.")
                }
            }
        }
    }
}
