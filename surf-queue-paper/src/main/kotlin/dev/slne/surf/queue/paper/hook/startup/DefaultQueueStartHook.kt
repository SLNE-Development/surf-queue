package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.surfapi.shared.api.component.ComponentMeta
import dev.slne.surf.surfapi.shared.api.component.requirement.ConditionalOnMissingComponent

@ComponentMeta
@ConditionalOnMissingComponent(QueueStartHook::class)
class DefaultQueueStartHook : QueueStartHook() {

    override suspend fun onEnable() {
        runServerReadyTasks()
    }
}