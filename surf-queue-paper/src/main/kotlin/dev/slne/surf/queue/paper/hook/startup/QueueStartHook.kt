package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.surfapi.core.api.component.AbstractComponent
import dev.slne.surf.surfapi.core.api.component.surfComponentApi
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Abstract component that queues tasks to be executed once the server is
 * fully ready.
 *
 * Platform-specific subclasses ([PolarQueueStartHook], [DefaultQueueStartHook])
 * determine when "ready" occurs and call [runServerReadyTasks] at the appropriate
 * lifecycle point.
 */
abstract class QueueStartHook : AbstractComponent() {
    private val serverReadyTasks = ConcurrentLinkedQueue<() -> Unit>()

    /** Executes and removes all queued server-ready tasks. */
    protected fun runServerReadyTasks() {
        val iterator = serverReadyTasks.iterator()
        while (iterator.hasNext()) {
            iterator.next().invoke()
            iterator.remove()
        }
    }

    /**
     * Registers a task to be executed when the server is ready.
     *
     * @param block the task to execute
     */
    fun onServerReady(block: () -> Unit) {
        serverReadyTasks.add(block)
    }

    companion object {
        /** Returns the first loaded [QueueStartHook] implementation. */
        fun get() = surfComponentApi.componentsOfTypeLoaded(QueueStartHook::class.java).first()
    }
}