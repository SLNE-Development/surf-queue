package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.kotlindsl.commandTree
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions

fun queueCommand() = commandTree("squeue") {
    withPermission(SurfQueuePermissions.COMMAND_QUEUE)
    queueStatusCommand()
    queuePauseCommand()
    queueListCommand()
    queueEnqueueCommand()
    queueDequeueCommand()
    queuePositionCommand()
    queueClearCommand()
    queueTickCommand()
    queueTransferTaskCommand()
    queueCleanupCommand()
    queueInfoCommand()
}
