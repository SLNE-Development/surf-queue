package dev.slne.surf.queue.velocity.command

import dev.jorel.commandapi.kotlindsl.commandTree
import dev.slne.surf.queue.velocity.command.test.testQueueCommands
import dev.slne.surf.queue.velocity.permission.SurfQueuePermissions

fun queueCommand() = commandTree("squeue") {
    withPermission(SurfQueuePermissions.COMMAND_QUEUE)
    testQueueCommands()
}