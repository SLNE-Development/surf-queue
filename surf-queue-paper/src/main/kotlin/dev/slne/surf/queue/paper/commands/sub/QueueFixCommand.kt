package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperQueueCommon

fun CommandAPICommand.queueFix() = subcommand("fix") {
    withPermission(PaperQueuePermissions.COMMAND_FIX)
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName) as PaperQueueCommon
        val result = queue.fix()
        val locks = result.lockReset

        sender.sendText {
            appendSuccessPrefix()
            success("Fix attempted for queue ")
            variableValue(serverName)
            success(".")
            appendNewline {
                appendSuccessPrefix()
                variableKey("Entries: ")
                variableValue("${result.sizeBefore}")
                spacer(" -> ")
                variableValue("${result.sizeAfter}")
                spacer(" (removed ")
                variableValue("${result.removedEntries}")
                spacer(")")
            }
            appendNewline {
                appendSuccessPrefix()
                variableKey("Was paused: ")
                variableValue("${result.wasPaused}")
                spacer(" (queue resumed)")
            }
            appendNewline {
                appendSuccessPrefix()
                variableKey("Transfer semaphore: ")
                variableValue("deleted=${locks.transferDeleted}, initialized=${locks.transferInitialized}")
            }
            appendNewline {
                appendSuccessPrefix()
                variableKey("Cleanup semaphore: ")
                variableValue("deleted=${locks.cleanupDeleted}, initialized=${locks.cleanupInitialized}")
            }
        }
    }
}
