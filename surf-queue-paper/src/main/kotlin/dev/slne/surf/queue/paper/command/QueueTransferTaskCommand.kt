package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperQueueTickTask
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit

fun CommandTree.queueTransferTaskCommand() = literalArgument("transfer-task") {
    withPermission(SurfQueuePermissions.COMMAND_TRANSFER_TASK)

    literalArgument("start") {
        anyExecutorSuspend { source, _ ->
            @Suppress("DEPRECATION")
            val serverName = Bukkit.getServerName()
            PaperQueueTickTask.shutdown()
            PaperQueueTickTask.start(serverName)

            source.sendText {
                appendSuccessPrefix()
                success("Started transfer task.")
            }
        }
    }

    literalArgument("stop") {
        anyExecutorSuspend { source, _ ->
            PaperQueueTickTask.shutdown()

            source.sendText {
                appendSuccessPrefix()
                success("Stopped transfer task.")
            }
        }
    }

    literalArgument("restart") {
        anyExecutorSuspend { source, _ ->
            @Suppress("DEPRECATION")
            val serverName = Bukkit.getServerName()
            PaperQueueTickTask.shutdown()
            PaperQueueTickTask.start(serverName)

            source.sendText {
                appendSuccessPrefix()
                success("Restarted transfer task.")
            }
        }
    }
}
