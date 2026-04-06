package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.surfapi.shared.api.component.ComponentMeta
import dev.slne.surf.surfapi.shared.api.component.requirement.DependsOnClass
import top.polar.api.loader.LoaderApi

@ComponentMeta
@DependsOnClass(LoaderApi::class)
class PolarQueueStartHook : QueueStartHook() {

    override suspend fun onLoad() {
        LoaderApi.registerEnableCallback(::runServerReadyTasks)
    }
}