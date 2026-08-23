package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.api.core.component.AbstractComponent
import dev.slne.surf.api.core.component.surfComponentApi
import java.util.concurrent.ConcurrentLinkedQueue

abstract class QueueStartHook : AbstractComponent() {
    private val serverReadyTasks = ConcurrentLinkedQueue<() -> Unit>()

    protected fun runServerReadyTasks() {
        while (true) {
            val task = serverReadyTasks.poll() ?: break
            task()
        }
    }

    fun onServerReady(block: () -> Unit) {
        serverReadyTasks.add(block)
    }

    companion object {
        fun get() = surfComponentApi.componentsOfTypeLoaded(QueueStartHook::class.java).first()
    }
}