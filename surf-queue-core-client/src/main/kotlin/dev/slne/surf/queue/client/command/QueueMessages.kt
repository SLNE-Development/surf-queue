package dev.slne.surf.queue.client.command

import dev.slne.surf.api.core.messages.adventure.buildText
import dev.slne.surf.api.core.messages.pagination.Pagination
import dev.slne.surf.queue.client.metrics.QueueMetricsSnapshot
import dev.slne.surf.queue.client.queue.QueueFixResult
import dev.slne.surf.queue.common.queue.RedisQueueScore
import dev.slne.surf.queue.common.queue.entry.QueueEntry
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectList
import net.kyori.adventure.text.Component
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Renders every message the queue commands send, so that all platforms share the exact same
 * wording and layout.
 */
object QueueMessages {

    val queueListPagination = Pagination<UUID> {
        title { primary("Queue list") }
        rowRenderer { uuid, i ->
            listOf(buildText {
                val position = i + 1
                variableKey(position)
                info("—")
                variableValue(uuid.toString())
            })
        }
    }

    fun cleanupDone(serverName: String, sizeBefore: Int, sizeAfter: Int) = buildText {
        val removed = sizeBefore - sizeAfter

        appendSuccessPrefix()
        success("Forced cleanup of queue '")
        variableValue(serverName)
        success("' complete. Removed ")
        variableValue("$removed")
        success(" expired entries (")
        variableValue("$sizeBefore")
        success(" —> ")
        variableValue("$sizeAfter")
        success(").")
    }

    fun queueCleared(serverName: String, size: Int) = buildText {
        appendSuccessPrefix()
        success("Cleared queue for server ")
        variableValue(serverName)
        success(". Removed ")
        variableValue("$size")
        success(" entries.")
    }

    fun playerDequeued(playerName: String, serverName: String) = buildText {
        appendSuccessPrefix()
        success("Removed ")
        variableValue(playerName)
        success(" from the queue ")
        variableValue(serverName)
        success(".")
    }

    fun playerNotQueued(playerName: String) = buildText {
        appendErrorPrefix()
        error("Player ")
        variableValue(playerName)
        error(" is not in the queue.")
    }

    fun playerAlreadyQueued(playerName: String) = buildText {
        appendErrorPrefix()
        error("Player ")
        variableValue(playerName)
        error(" is already in the queue.")
    }

    fun playerEnqueued(playerName: String, serverName: String, priority: Int?) = buildText {
        appendSuccessPrefix()
        success("Enqueued ")
        variableValue(playerName)
        success(" to the queue ")
        variableValue(serverName)
        priority?.let {
            success(" with priority ")
            variableValue(it)
        }
        success(".")
    }

    fun queueFixed(serverName: String, result: QueueFixResult) = buildText {
        val locks = result.lockReset

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

    fun playerQueueInfo(
        playerName: String,
        uuid: UUID,
        position: Int?,
        size: Int,
        meta: QueueEntry?,
        score: RedisQueueScore?,
        lastSeen: Long?,
        retryCount: Int?
    ) = buildText {
        append {
            appendSuccessPrefix()
            success("=== Player Info: ")
            variableValue(playerName)
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

    fun queueEmpty() = buildText {
        appendInfoPrefix()
        info("The queue is empty.")
    }

    fun startedLoggingMetrics() = buildText {
        appendSuccessPrefix()
        success("Started logging metrics")
    }

    fun stoppedLoggingMetrics() = buildText {
        appendSuccessPrefix()
        success("Stopped logging metrics")
    }

    /**
     * Renders the metrics overview as one component per message, in send order.
     */
    fun metricsSnapshot(
        snapshot: QueueMetricsSnapshot,
        queueSizes: Map<String, Int>
    ): ObjectList<Component> {
        val lines = ObjectArrayList<Component>()

        lines += buildText {
            appendSuccessPrefix()
            success("=== Queue Metrics ===")
        }
        lines += buildText {
            success("Transfers: ")
            variableValue("${snapshot.totalTransfers}")
            success(" successful, ")
            variableValue("${snapshot.totalFailedTransfers}")
            success(" failed (")
            variableValue(String.format("%.1f%%", snapshot.transferSuccessRate * 100))
            success(")")
        }
        lines += buildText {
            success("Enqueues: ")
            variableValue("${snapshot.totalEnqueues}")
            success(" | Dequeues: ")
            variableValue("${snapshot.totalDequeues}")
        }
        lines += buildText {
            success("Grace Expiries: ")
            variableValue("${snapshot.totalGraceExpiries}")
            success(" | Retry Exhausted: ")
            variableValue("${snapshot.totalRetryExhausted}")
        }
        lines += buildText {
            success("Lock: ")
            variableValue("${snapshot.totalLockAcquired}/${snapshot.totalLockAttempts}")
            success(" (")
            variableValue(String.format("%.1f%%", snapshot.lockAcquisitionRate * 100))
            success(")")
        }
        lines += buildText {
            success("Cleanup: ")
            variableValue("${snapshot.totalCleanupCycles}")
            success(" cycles, ")
            variableValue("${snapshot.totalCleanupRemovals}")
            success(" removals")
        }
        lines += buildText {
            success("Ticks: ")
            variableValue("${snapshot.totalTicks}")
        }

        if (queueSizes.isNotEmpty()) {
            lines += buildText {
                appendSuccessPrefix()
                success("--- Per Queue ---")
            }
            for ((name, size) in queueSizes) {
                val perQueue = snapshot.perQueue[name]
                lines += buildText {
                    variableValue(name)
                    success(": size=")
                    variableValue("$size")
                    success(", transfers=")
                    variableValue("${perQueue?.transfers ?: 0}")
                    success(", failed=")
                    variableValue("${perQueue?.failedTransfers ?: 0}")
                    success(", skips=")
                    variableValue("${perQueue?.skips ?: 0}")
                }
            }
        }

        return lines
    }

    fun queuePaused() = buildText {
        appendSuccessPrefix()
        success("Paused queue")
    }

    fun queueResumed() = buildText {
        appendSuccessPrefix()
        success("Resumed queue")
    }

    fun queuePauseStatus(paused: Boolean) = buildText {
        appendSuccessPrefix()
        if (paused) {
            info("The queue is currently paused")
        } else {
            info("The queue is currently running")
        }
    }
}
