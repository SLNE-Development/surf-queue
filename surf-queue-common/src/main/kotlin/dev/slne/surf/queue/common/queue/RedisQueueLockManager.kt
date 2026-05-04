package dev.slne.surf.queue.common.queue

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.api.core.util.runWithFixedDelay
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.RPermitExpirableSemaphoreAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferSemaphore = redisApi.redisson.getPermitExpirableSemaphore(keys.transferSemaphoreKey)
    private val cleanupSemaphore = redisApi.redisson.getPermitExpirableSemaphore(keys.cleanupSemaphoreKey)

    companion object {
        private val log = logger()

        private val TRANSFER_LEASE = 60.seconds
        private val TRANSFER_REFRESH = 20.seconds
        private val CLEANUP_LEASE = 30.seconds
        private val CLEANUP_REFRESH = 10.seconds
    }

    private class PermitLostException(label: String, serverName: String) :
        Exception("Lost $label permit for $serverName (lease expired or released elsewhere)")

    init {
        transferSemaphore.trySetPermits(1)
        cleanupSemaphore.trySetPermits(1)
    }

    suspend fun <T> withTransferLock(
        block: suspend (acquired: Boolean) -> T
    ): T = withSemaphore(
        semaphore = transferSemaphore,
        lease = TRANSFER_LEASE,
        refreshEvery = TRANSFER_REFRESH,
        label = "transfer",
        block = block
    )

    suspend fun withCleanupLock(block: suspend () -> Unit) = withSemaphore(
        semaphore = cleanupSemaphore,
        lease = CLEANUP_LEASE,
        refreshEvery = CLEANUP_REFRESH,
        label = "cleanup",
        block = { acquired ->
            if (acquired) {
                block()
            }
        }
    )

    private suspend fun <T> withSemaphore(
        semaphore: RPermitExpirableSemaphoreAsync,
        lease: Duration,
        refreshEvery: Duration,
        label: String,
        block: suspend (acquired: Boolean) -> T
    ): T {
        val permitId = semaphore.tryAcquireAsync(0, lease.inWholeMilliseconds, TimeUnit.MILLISECONDS).await()
            ?: return block(false)

        val permitLost = AtomicBoolean(false)

        return try {
            coroutineScope {
                val outerScope = this
                val refresher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        runWithFixedDelay(delay = refreshEvery, initialDelay = refreshEvery) {
                            val refreshed = try {
                                semaphore.updateLeaseTimeAsync(permitId, lease.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                                    .await()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                log.atWarning()
                                    .withCause(e)
                                    .log("Failed to refresh %s lease for %s", label, keys.serverName)
                                false
                            }

                            if (!refreshed) {
                                log.atWarning()
                                    .log(
                                        "Lost %s permit for %s (lease expired or released elsewhere)",
                                        label,
                                        keys.serverName
                                    )
                                permitLost.set(true)
                                outerScope.cancel("$label permit lost for ${keys.serverName}")
                            }
                        }
                    } catch (_: CancellationException) {
                    }
                }

                try {
                    block(true)
                } finally {
                    refresher.cancel()
                    withContext(NonCancellable) {
                        try {
                            semaphore.tryReleaseAsync(permitId).await()
                        } catch (e: Exception) {
                            log.atWarning()
                                .withCause(e)
                                .log("Failed to release %s lock for %s", label, keys.serverName)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            if (permitLost.get()) {
                throw PermitLostException(label, keys.serverName)
            }
            throw e
        }
    }
}