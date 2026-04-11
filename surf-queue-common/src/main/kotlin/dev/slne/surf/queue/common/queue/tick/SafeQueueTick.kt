package dev.slne.surf.queue.common.queue.tick

import dev.slne.surf.queue.common.queue.AbstractQueue
import dev.slne.surf.surfapi.core.api.util.logger
import kotlin.coroutines.cancellation.CancellationException

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
}