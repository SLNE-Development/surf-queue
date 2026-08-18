package dev.slne.surf.queue.minestom.command

import dev.slne.minestom.lobby.api.command.commandapi.dsl.commandAPICommand
import dev.slne.surf.queue.client.permission.QueuePermissions
import dev.slne.surf.queue.minestom.command.sub.metricsCommand
import dev.slne.surf.queue.minestom.command.sub.queueCleanup
import dev.slne.surf.queue.minestom.command.sub.queueClear
import dev.slne.surf.queue.minestom.command.sub.queueDequeue
import dev.slne.surf.queue.minestom.command.sub.queueEnqueue
import dev.slne.surf.queue.minestom.command.sub.queueFix
import dev.slne.surf.queue.minestom.command.sub.queueInfo
import dev.slne.surf.queue.minestom.command.sub.queueList
import dev.slne.surf.queue.minestom.command.sub.queuePause

fun queueCommand() = commandAPICommand("squeue") {
    withPermission(QueuePermissions.COMMAND_QUEUE)

    queueCleanup()
    queueClear()
    queueDequeue()
    queueEnqueue()
    queueFix()
    queueInfo()
    queueList()
    queuePause()
    metricsCommand()
}
