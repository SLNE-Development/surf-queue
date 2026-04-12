package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.api.core.component.AbstractComponent
import dev.slne.surf.api.core.component.surfComponentApi
import java.util.concurrent.ConcurrentLinkedQueue

abstract class QueueStartHook : AbstractComponent() {
    private val serverReadyTasks = ConcurrentLinkedQueue<() -> Unit>()

    protected fun runServerReadyTasks() {
        val iterator = serverReadyTasks.iterator()
        while (iterator.hasNext()) {
            iterator.next().invoke()
            iterator.remove()
        }
    }

    fun onServerReady(block: () -> Unit) {
        serverReadyTasks.add(block)
    }

    companion object {
        fun get() = surfComponentApi.componentsOfTypeLoaded(QueueStartHook::class.java).first()
    }
}