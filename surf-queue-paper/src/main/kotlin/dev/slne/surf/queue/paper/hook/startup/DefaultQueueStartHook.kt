package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.api.shared.api.component.SurfComponentMeta
import dev.slne.surf.api.shared.api.component.requirement.ConditionalOnMissingComponent

@SurfComponentMeta
@ConditionalOnMissingComponent(QueueStartHook::class)
class DefaultQueueStartHook : QueueStartHook() {

    override suspend fun onEnable() {
        runServerReadyTasks()
    }
}