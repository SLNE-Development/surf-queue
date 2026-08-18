package dev.slne.surf.queue.client.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueueMetricsSnapshotTest {

    @Test
    fun `lock acquisition rate is the acquired share of all attempts`() {
        val snapshot = snapshot(totalLockAttempts = 8, totalLockAcquired = 2)

        assertEquals(0.25, snapshot.lockAcquisitionRate)
    }

    @Test
    fun `lock acquisition rate is zero without attempts`() {
        val snapshot = snapshot(totalLockAttempts = 0, totalLockAcquired = 0)

        assertEquals(0.0, snapshot.lockAcquisitionRate)
    }

    @Test
    fun `transfer success rate counts failed transfers towards the total`() {
        val snapshot = snapshot(totalTransfers = 3, totalFailedTransfers = 1)

        assertEquals(0.75, snapshot.transferSuccessRate)
    }

    @Test
    fun `transfer success rate is zero without transfers`() {
        val snapshot = snapshot()

        assertEquals(0.0, snapshot.transferSuccessRate)
    }

    private fun snapshot(
        totalTransfers: Long = 0,
        totalFailedTransfers: Long = 0,
        totalLockAttempts: Long = 0,
        totalLockAcquired: Long = 0
    ) = QueueMetricsSnapshot(
        totalTransfers = totalTransfers,
        totalEnqueues = 0,
        totalDequeues = 0,
        totalFailedTransfers = totalFailedTransfers,
        totalGraceExpiries = 0,
        totalRetryExhausted = 0,
        totalLockAttempts = totalLockAttempts,
        totalLockAcquired = totalLockAcquired,
        totalCleanupCycles = 0,
        totalCleanupRemovals = 0,
        totalTicks = 0,
        perQueue = emptyMap()
    )
}
