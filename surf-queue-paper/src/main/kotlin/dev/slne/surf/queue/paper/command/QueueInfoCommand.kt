package dev.slne.surf.queue.paper.command

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.slne.surf.queue.common.queue.RedisQueueScorePacker
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.SurfQueuePermissions
import dev.slne.surf.queue.paper.queue.PaperSurfQueue
import dev.slne.surf.surfapi.bukkit.api.command.executors.anyExecutorSuspend
import dev.slne.surf.surfapi.core.api.messages.adventure.sendText
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.time.Duration
import java.time.Instant

fun CommandTree.queueInfoCommand() = literalArgument("info") {
    withPermission(SurfQueuePermissions.COMMAND_INFO)

    argument(OfflinePlayerArgument("player")) {
        anyExecutorSuspend { sender, args ->
            val player = args.get("player") as OfflinePlayer
            val uuid = player.uniqueId
            val playerName = player.name ?: uuid.toString()

            @Suppress("DEPRECATION")
            val serverName = Bukkit.getServerName()
            val queue = RedisQueueService.get().get(serverName) as? PaperSurfQueue

            if (queue == null) {
                sender.sendText {
                    appendErrorPrefix()
                    error("No queue found for this server.")
                }
                return@anyExecutorSuspend
            }

            val isQueued = queue.isQueued(uuid)
            if (!isQueued) {
                sender.sendText {
                    appendErrorPrefix()
                    error("Player ")
                    variableValue(playerName)
                    error(" is not in the queue.")
                }
                return@anyExecutorSuspend
            }

            val position = queue.getPosition(uuid)
            val size = queue.size()
            val meta = queue.getEntryMeta(uuid)
            val score = queue.getEntryScore(uuid)
            val lastSeen = queue.getEntryLastSeen(uuid)
            val retryCount = queue.getEntryRetryCount(uuid)

            sender.sendText {
                appendSuccessPrefix()
                success("=== Player Info: ")
                variableValue(playerName)
                success(" ===")
            }
            sender.sendText {
                success("UUID: ")
                variableValue("$uuid")
            }
            sender.sendText {
                success("Position: ")
                variableValue("${(position ?: -1) + 1}")
                success(" / ")
                variableValue("$size")
            }

            if (meta != null) {
                sender.sendText {
                    success("Priority: ")
                    variableValue("${meta.priority}")
                }

                val addedAt = Instant.ofEpochMilli(meta.addedAt)
                val timeInQueue = Duration.between(addedAt, Instant.now())
                sender.sendText {
                    success("Added at: ")
                    variableValue(addedAt.toString())
                    success(" (")
                    variableValue(formatDuration(timeInQueue))
                    success(" ago)")
                }
            }

            if (score != null) {
                val unpacked = RedisQueueScorePacker.unpack(score)
                sender.sendText {
                    success("Score: ")
                    variableValue(String.format("%.0f", score))
                    success(" (priority=")
                    variableValue("${unpacked.priority}")
                    success(", deltaMs=")
                    variableValue("${unpacked.deltaMs}")
                    success(", seq=")
                    variableValue("${unpacked.sequence}")
                    success(")")
                }
            }

            if (lastSeen != null) {
                val lastSeenInstant = Instant.ofEpochMilli(lastSeen)
                val elapsed = Duration.between(lastSeenInstant, Instant.now())
                sender.sendText {
                    success("Last Seen: ")
                    variableValue(lastSeenInstant.toString())
                    success(" (")
                    variableValue(formatDuration(elapsed))
                    success(" ago)")
                }
            } else {
                sender.sendText {
                    success("Last Seen: ")
                    variableValue("n/a (online or no data)")
                }
            }

            sender.sendText {
                success("Retry Count: ")
                variableValue("${retryCount ?: 0}")
            }
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()

    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
