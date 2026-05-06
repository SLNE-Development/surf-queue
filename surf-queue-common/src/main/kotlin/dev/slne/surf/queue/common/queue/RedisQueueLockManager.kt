package dev.slne.surf.queue.common.queue

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.queue.common.redis.redisApi
import dev.slne.surf.redis.libs.redisson.api.RPermitExpirableSemaphoreAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RedisQueueLockManager(private val keys: RedisQueueKeys) {
    private val transferSemaphore = redisApi.redisson.getPermitExpirableSemaphore(keys.transferSemaphoreKey)
    private val cleanupSemaphore = redisApi.redisson.getPermitExpirableSemaphore(keys.cleanupSemaphoreKey)

    companion object {
        private val log = logger()

        private val TRANSFER_LEASE = 60.seconds
        private val CLEANUP_LEASE = 30.seconds
        private val RELEASE_TIMEOUT = 5.seconds
    }

    init {
        transferSemaphore.trySetPermits(1)
        cleanupSemaphore.trySetPermits(1)
    }

    suspend fun <T> withTransferLock(
        block: suspend (acquired: Boolean) -> T
    ): T = withSemaphore(
        semaphore = transferSemaphore,
        lease = TRANSFER_LEASE,
        label = "transfer",
        block = block
    )

    suspend fun withCleanupLock(block: suspend () -> Unit) = withSemaphore(
        semaphore = cleanupSemaphore,
        lease = CLEANUP_LEASE,
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
        label: String,
        block: suspend (acquired: Boolean) -> T
    ): T {
        val permitId = semaphore.tryAcquireAsync(0, lease.inWholeMilliseconds, TimeUnit.MILLISECONDS).await()
            ?: return block(false)

        return try {
            block(true)
        } finally {
            withContext(NonCancellable) {
                releasePermit(semaphore, permitId, label)
            }
        }
    }

    private suspend fun releasePermit(
        semaphore: RPermitExpirableSemaphoreAsync,
        permitId: String,
        label: String
    ) {
        val releaseResult = withTimeoutOrNull(RELEASE_TIMEOUT) {
            runCatching { semaphore.tryReleaseAsync(permitId).await() }
        }

        if (releaseResult == null) {
            log.atWarning()
                .log("Timed out releasing %s lock for %s", label, keys.serverName)
            return
        }

        releaseResult
            .onFailure { e ->
                log.atWarning()
                    .withCause(e)
                    .log("Failed to release %s lock for %s", label, keys.serverName)
            }
            .onSuccess { released ->
                if (!released) {
                    log.atWarning()
                        .log(
                            "Could not release %s lock for %s because the permit no longer exists",
                            label,
                            keys.serverName
                        )
                }
            }
    }
}
