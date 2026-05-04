package dev.slne.surf.queue.common.queue.tick

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.queue.common.queue.AbstractQueue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

object SafeQueueTick {
    val log = logger()

    inline fun tickSafe(queue: AbstractQueue, component: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.atWarning()
                .withCause(e)
                .log("Failed to tick %s for queue %s", component, queue.serverName)
        }
    }

    suspend inline fun tickSafeWithTimeout(
        queue: AbstractQueue,
        component: String,
        timeout: Duration,
        crossinline block: suspend () -> Unit
    ) {
        try {
            withTimeout(timeout) {
                block()
            }
        } catch (_: TimeoutCancellationException) {
            log.atWarning()
                .log("Timed out ticking %s for queue %s", component, queue.serverName)
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            log.atWarning()
                .withCause(e)
                .log("Cancelled ticking %s for queue %s", component, queue.serverName)
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to tick %s for queue %s", component, queue.serverName)
        }
    }
}