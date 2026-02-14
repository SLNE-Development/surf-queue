package dev.slne.surf.queue.velocity.command

import dev.jorel.commandapi.kotlindsl.commandTree
import dev.slne.surf.queue.velocity.permission.SurfQueuePermissions

fun queueCommand() = commandTree("queue") {
    withPermission(SurfQueuePermissions.COMMAND_QUEUE)


}