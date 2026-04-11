package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.surfapi.shared.api.component.ComponentMeta
import dev.slne.surf.surfapi.shared.api.component.requirement.DependsOnClass
import top.polar.api.loader.LoaderApi

/**
 * [QueueStartHook] implementation for the Polar loader.
 *
 * Registers [runServerReadyTasks] as a Polar enable callback during [onLoad],
 * so server-ready tasks run when Polar considers the server fully enabled.
 * Only activated when the [LoaderApi] class is available on the classpath.
 */
@ComponentMeta
@DependsOnClass(LoaderApi::class)
class PolarQueueStartHook : QueueStartHook() {

    override suspend fun onLoad() {
        LoaderApi.registerEnableCallback(::runServerReadyTasks)
    }
}