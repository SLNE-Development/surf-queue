package dev.slne.surf.queue.paper.hook.startup

import dev.slne.surf.surfapi.shared.api.component.ComponentMeta
import dev.slne.surf.surfapi.shared.api.component.requirement.ConditionalOnMissingComponent

/**
 * Default [QueueStartHook] implementation used when no platform-specific hook
 * (e.g., [PolarQueueStartHook]) is available.
 *
 * Runs server-ready tasks immediately during [onEnable].
 */
@ComponentMeta
@ConditionalOnMissingComponent(QueueStartHook::class)
class DefaultQueueStartHook : QueueStartHook() {

    override suspend fun onEnable() {
        runServerReadyTasks()
    }
}