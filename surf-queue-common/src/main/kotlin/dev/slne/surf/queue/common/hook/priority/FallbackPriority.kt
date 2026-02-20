package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.surfapi.shared.api.component.ComponentMeta
import dev.slne.surf.surfapi.shared.api.component.requirement.ConditionalOnMissingComponent
import java.util.UUID

@ComponentMeta
@ConditionalOnMissingComponent(PriorityHook::class)
class FallbackPriority : PriorityHook {
    override suspend fun getPriority(uuid: UUID): Int = 0
}