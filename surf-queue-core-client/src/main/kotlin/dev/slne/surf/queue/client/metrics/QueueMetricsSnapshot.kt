package dev.slne.surf.queue.client.metrics

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
    data class PerQueueMetrics(
        val transfers: Long,
        val enqueues: Long,
        val dequeues: Long,
        val failedTransfers: Long,
        val skips: Long
    )

    val lockAcquisitionRate: Double
        get() = if (totalLockAttempts > 0) totalLockAcquired.toDouble() / totalLockAttempts else 0.0

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