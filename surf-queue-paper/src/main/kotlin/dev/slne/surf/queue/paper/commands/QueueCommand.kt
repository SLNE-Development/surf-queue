package dev.slne.surf.queue.paper.commands

import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.slne.surf.queue.paper.commands.sub.metricsCommand
import dev.slne.surf.queue.paper.commands.sub.queueDequeue
import dev.slne.surf.queue.paper.commands.sub.queueCleanup
import dev.slne.surf.queue.paper.commands.sub.queueClear
import dev.slne.surf.queue.paper.commands.sub.queueEnqueue
import dev.slne.surf.queue.paper.commands.sub.queueInfo
import dev.slne.surf.queue.paper.commands.sub.queueList
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions

/** Registers the root `/squeue` command with all subcommands. */
fun queueCommand() = commandAPICommand("squeue") {
    withPermission(PaperQueuePermissions.COMMAND_QUEUE)

    queueCleanup()
    queueClear()
    queueDequeue()
    queueEnqueue()
    queueInfo()
    queueList()
    metricsCommand()
}