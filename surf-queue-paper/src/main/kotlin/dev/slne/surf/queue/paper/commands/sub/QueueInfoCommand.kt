package dev.slne.surf.queue.paper.commands.sub

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.AsyncPlayerProfileArgument
import dev.jorel.commandapi.kotlindsl.argument
import dev.jorel.commandapi.kotlindsl.getValue
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.paper.command.executors.anyExecutorSuspend
import dev.slne.surf.api.paper.command.util.awaitAsyncPlayerProfile
import dev.slne.surf.api.paper.command.util.idOrThrow
import dev.slne.surf.core.api.common.server.SurfServer
import dev.slne.surf.core.api.paper.command.argument.surfBackendServerArgument
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.permission.PaperQueuePermissions
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

fun CommandAPICommand.queueInfo() = subcommand("info") {
    withPermission(PaperQueuePermissions.COMMAND_INFO)

    argument(AsyncPlayerProfileArgument("player"))
    surfBackendServerArgument("server", optional = true)

    anyExecutorSuspend { sender, arguments ->
        val profile = arguments.awaitAsyncPlayerProfile("player")
        val uuid = profile.idOrThrow()
        val server: SurfServer? by arguments
        val serverName = server?.name ?: SurfServer.current().name
        val queue = RedisQueueService.get().getQueueByName(serverName)

        val isQueued = queue.isQueued(uuid)
        if (!isQueued) {
            sender.sendText {
                appendErrorPrefix()
                error("Player ")
                variableValue(profile.name ?: uuid.toString())
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
            append {
                appendSuccessPrefix()
                success("=== Player Info: ")
                variableValue(profile.name ?: uuid.toString())
                success(" ===")
            }
            appendNewline {
                appendSuccessPrefix()
                variableKey("UUID: ")
                variableValue("$uuid")
            }
            appendNewline {
                appendSuccessPrefix()
                variableKey("Position: ")
                variableValue("${(position ?: -1) + 1}")
                spacer(" / ")
                variableValue("$size")
            }
            if (meta != null) {
                appendNewline {
                    appendSuccessPrefix()
                    variableKey("Priority: ")
                    variableValue("${meta.priority}")
                }
                appendNewline {
                    val addedAt = Instant.ofEpochMilli(meta.addedAt)
                    val timeInQueue = Duration.between(addedAt, Instant.now()).toSeconds().seconds
                    appendSuccessPrefix()
                    variableKey("Added at: ")
                    variableValue(addedAt.toString())
                    spacer(" (")
                    variableValue(timeInQueue.toString())
                    success(" ago)")
                }
            }
            if (score != null) {
                appendNewline {
                    appendSuccessPrefix()
                    variableKey("Score: ")
                    variableValue(String.format("%.0f", score.packed))
                    spacer(" (")
                    variableKey("priority")
                    spacer("=")
                    variableValue(score.priority)
                    spacer(", ")
                    variableKey("deltaMs")
                    spacer("=")
                    variableValue(score.deltaMs)
                    spacer(", ")
                    variableKey("seq")
                    spacer("=")
                    variableValue(score.sequence)
                    spacer(")")
                }
            }

            if (lastSeen != null) {
                appendNewline {
                    val lastSeenAt = Instant.ofEpochMilli(lastSeen)
                    val timeSinceLastSeen =
                        Duration.between(lastSeenAt, Instant.now()).toSeconds().seconds

                    appendSuccessPrefix()
                    variableKey("Last seen: ")
                    variableValue(lastSeenAt.toString())
                    spacer(" (")
                    variableValue(timeSinceLastSeen.toString())
                    success(" ago)")
                }
            } else {
                appendNewline {
                    appendSuccessPrefix()
                    variableKey("Last seen: ")
                    variableValue("n/a (online or no data)")
                }
            }

            appendNewline {
                appendSuccessPrefix()
                variableKey("Retry count: ")
                variableValue("${retryCount ?: 0}")
            }
        }
    }
}