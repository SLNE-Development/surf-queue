package dev.slne.surf.queue.paper.metrics

import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.surfapi.core.api.util.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe metrics collector for queue operations.
 *
 * Tracks global and per-queue counters for transfers, enqueues, dequeues,
 * failures, grace expiries, retry exhaustions, lock attempts, cleanup cycles,
 * and ticks. All counters use [AtomicLong] for lock-free thread safety.
 */
object QueueMetrics {
    private val log = logger()

    val totalTransfers = AtomicLong(0)
    val totalEnqueues = AtomicLong(0)
    val totalDequeues = AtomicLong(0)
    val totalFailedTransfers = AtomicLong(0)
    val totalGraceExpiries = AtomicLong(0)
    val totalRetryExhausted = AtomicLong(0)
    val totalLockAttempts = AtomicLong(0)
    val totalLockAcquired = AtomicLong(0)
    val totalCleanupCycles = AtomicLong(0)
    val totalCleanupRemovals = AtomicLong(0)
    val totalTicks = AtomicLong(0)

    private val perQueueTransfers = ConcurrentHashMap<String, AtomicLong>()
    private val perQueueEnqueues = ConcurrentHashMap<String, AtomicLong>()
    private val perQueueDequeues = ConcurrentHashMap<String, AtomicLong>()
    private val perQueueFailedTransfers = ConcurrentHashMap<String, AtomicLong>()
    private val perQueueSkips = ConcurrentHashMap<String, AtomicLong>()

    /** Records a successful transfer for the given [serverName]. */
    fun recordTransfer(serverName: String) {
        totalTransfers.incrementAndGet()
        perQueueTransfers.computeIfAbsent(serverName) { AtomicLong(0) }.incrementAndGet()
    }

    /** Records an enqueue event for the given [serverName]. */
    fun recordEnqueue(serverName: String) {
        totalEnqueues.incrementAndGet()
        perQueueEnqueues.computeIfAbsent(serverName) { AtomicLong(0) }.incrementAndGet()
    }

    /** Records a dequeue event for the given [serverName]. */
    fun recordDequeue(serverName: String) {
        totalDequeues.incrementAndGet()
        perQueueDequeues.computeIfAbsent(serverName) { AtomicLong(0) }.incrementAndGet()
    }

    /** Records a failed transfer for the given [serverName]. */
    fun recordFailedTransfer(serverName: String) {
        totalFailedTransfers.incrementAndGet()
        perQueueFailedTransfers.computeIfAbsent(serverName) { AtomicLong(0) }.incrementAndGet()
    }

    /** Records a skipped entry for the given [serverName]. */
    fun recordSkip(serverName: String) {
        perQueueSkips.computeIfAbsent(serverName) { AtomicLong(0) }.incrementAndGet()
    }

    /** Records that a player's grace period expired and they were removed. */
    fun recordGraceExpiry() {
        totalGraceExpiries.incrementAndGet()
    }

    /** Records that a player exhausted all retry attempts. */
    fun recordRetryExhausted() {
        totalRetryExhausted.incrementAndGet()
    }

    /**
     * Records a transfer lock attempt.
     *
     * @param acquired whether the lock was successfully acquired
     */
    fun recordLockAttempt(acquired: Boolean) {
        totalLockAttempts.incrementAndGet()
        if (acquired) totalLockAcquired.incrementAndGet()
    }

    /**
     * Records a completed cleanup cycle.
     *
     * @param removals the number of entries removed during this cycle
     */
    fun recordCleanupCycle(removals: Int) {
        totalCleanupCycles.incrementAndGet()
        totalCleanupRemovals.addAndGet(removals.toLong())
    }

    /** Records a tick event. */
    fun recordTick() {
        totalTicks.incrementAndGet()
    }

    fun getTransfersFor(serverName: String): Long =
        perQueueTransfers[serverName]?.get() ?: 0

    fun getEnqueuesFor(serverName: String): Long =
        perQueueEnqueues[serverName]?.get() ?: 0

    fun getDequeuesFor(serverName: String): Long =
        perQueueDequeues[serverName]?.get() ?: 0

    fun getFailedTransfersFor(serverName: String): Long =
        perQueueFailedTransfers[serverName]?.get() ?: 0

    fun getSkipsFor(serverName: String): Long =
        perQueueSkips[serverName]?.get() ?: 0

    /**
     * Creates an immutable [QueueMetricsSnapshot] of all current counter values.
     */
    fun snapshot(): QueueMetricsSnapshot = QueueMetricsSnapshot(
        totalTransfers = totalTransfers.get(),
        totalEnqueues = totalEnqueues.get(),
        totalDequeues = totalDequeues.get(),
        totalFailedTransfers = totalFailedTransfers.get(),
        totalGraceExpiries = totalGraceExpiries.get(),
        totalRetryExhausted = totalRetryExhausted.get(),
        totalLockAttempts = totalLockAttempts.get(),
        totalLockAcquired = totalLockAcquired.get(),
        totalCleanupCycles = totalCleanupCycles.get(),
        totalCleanupRemovals = totalCleanupRemovals.get(),
        totalTicks = totalTicks.get(),
        perQueue = perQueueTransfers.keys.associateWith { serverName ->
            QueueMetricsSnapshot.PerQueueMetrics(
                transfers = getTransfersFor(serverName),
                enqueues = getEnqueuesFor(serverName),
                dequeues = getDequeuesFor(serverName),
                failedTransfers = getFailedTransfersFor(serverName),
                skips = getSkipsFor(serverName)
            )
        }
    )

    /**
     * Collects current queue sizes from all known queues.
     *
     * @return a map of server name to queue size
     */
    suspend fun collectQueueSizes(): Map<String, Int> {
        return try {
            RedisQueueService.get().getAll()
                .associate { it.serverName to it.size() }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to collect queue sizes for metrics")
            emptyMap()
        }
    }
}