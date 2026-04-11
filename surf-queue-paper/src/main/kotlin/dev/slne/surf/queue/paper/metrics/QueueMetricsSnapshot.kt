package dev.slne.surf.queue.paper.metrics

/**
 * Immutable point-in-time snapshot of all queue metrics.
 *
 * Created via [QueueMetrics.snapshot] and used for logging and in-game display.
 *
 * @property totalTransfers total successful transfers across all queues
 * @property totalEnqueues total enqueue events
 * @property totalDequeues total dequeue events
 * @property totalFailedTransfers total failed transfer attempts
 * @property totalGraceExpiries total players removed due to grace period expiry
 * @property totalRetryExhausted total players removed after exhausting retries
 * @property totalLockAttempts total distributed lock acquisition attempts
 * @property totalLockAcquired total successful lock acquisitions
 * @property totalCleanupCycles total cleanup cycles executed
 * @property totalCleanupRemovals total entries removed during cleanup
 * @property totalTicks total tick events
 * @property perQueue per-queue breakdown of metrics
 */
data class QueueMetricsSnapshot(
    val totalTransfers: Long,
    val totalEnqueues: Long,
    val totalDequeues: Long,
    val totalFailedTransfers: Long,
    val totalGraceExpiries: Long,
    val totalRetryExhausted: Long,
    val totalLockAttempts: Long,
    val totalLockAcquired: Long,
    val totalCleanupCycles: Long,
    val totalCleanupRemovals: Long,
    val totalTicks: Long,
    val perQueue: Map<String, PerQueueMetrics>
) {
    /**
     * Per-queue metrics breakdown.
     *
     * @property transfers successful transfers for this queue
     * @property enqueues enqueue events for this queue
     * @property dequeues dequeue events for this queue
     * @property failedTransfers failed transfer attempts for this queue
     * @property skips skip events for this queue
     */
    data class PerQueueMetrics(
        val transfers: Long,
        val enqueues: Long,
        val dequeues: Long,
        val failedTransfers: Long,
        val skips: Long
    )

    /** Ratio of successful lock acquisitions to total attempts (0.0–1.0). */
    val lockAcquisitionRate: Double
        get() = if (totalLockAttempts > 0) totalLockAcquired.toDouble() / totalLockAttempts else 0.0

    /** Ratio of successful transfers to total attempts (0.0–1.0). */
    val transferSuccessRate: Double
        get() {
            val total = totalTransfers + totalFailedTransfers
            return if (total > 0) totalTransfers.toDouble() / total else 0.0
        }

    override fun toString(): String = buildString {
        appendLine("=== Queue Metrics Snapshot ===")
        appendLine(
            "Transfers: $totalTransfers successful, $totalFailedTransfers failed (${
                String.format(
                    "%.1f",
                    transferSuccessRate * 100
                )
            }% success)"
        )
        appendLine("Enqueues: $totalEnqueues | Dequeues: $totalDequeues")
        appendLine("Grace Expiries: $totalGraceExpiries | Retry Exhausted: $totalRetryExhausted")
        appendLine(
            "Lock: $totalLockAcquired/$totalLockAttempts acquired (${
                String.format(
                    "%.1f",
                    lockAcquisitionRate * 100
                )
            }%)"
        )
        appendLine("Cleanup: $totalCleanupCycles cycles, $totalCleanupRemovals removals")
        appendLine("Ticks: $totalTicks")
        if (perQueue.isNotEmpty()) {
            appendLine("--- Per Queue ---")
            for ((name, metrics) in perQueue) {
                appendLine("  $name: transfers=${metrics.transfers}, enqueues=${metrics.enqueues}, dequeues=${metrics.dequeues}, failed=${metrics.failedTransfers}, skips=${metrics.skips}")
            }
        }
    }
}