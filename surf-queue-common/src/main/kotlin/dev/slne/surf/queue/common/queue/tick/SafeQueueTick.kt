package dev.slne.surf.queue.common.queue.tick

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.surfapi.core.api.util.logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Utility object that wraps queue tick operations with exception handling.
 *
 * [CancellationException]s are re-thrown to preserve structured concurrency;
 * all other exceptions are logged as warnings without crashing the tick loop.
 */
object SafeQueueTick {
    val log = logger()

    /**
     * Executes [block] for the given [queue] and [component], catching and logging
     * any non-cancellation exceptions.
     *
     * @param queue the queue being ticked (used for logging context)
     * @param component a human-readable label for the tick phase (e.g., `"cleanup"`)
     * @param block the tick logic to execute
     */
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
}