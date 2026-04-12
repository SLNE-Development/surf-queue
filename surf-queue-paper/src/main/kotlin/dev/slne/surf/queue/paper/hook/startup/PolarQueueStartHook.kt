package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.api.shared.api.component.SurfComponentMeta
import dev.slne.surf.api.shared.api.component.requirement.DependsOnClass
import top.polar.api.loader.LoaderApi

@SurfComponentMeta
@DependsOnClass(LoaderApi::class)
class PolarQueueStartHook : QueueStartHook() {

    override suspend fun onLoad() {
        LoaderApi.registerEnableCallback(::runServerReadyTasks)
    }
}