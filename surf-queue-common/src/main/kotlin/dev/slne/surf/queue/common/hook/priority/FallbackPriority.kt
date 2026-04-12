package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.api.shared.api.component.SurfComponentMeta
import dev.slne.surf.api.shared.api.component.requirement.ConditionalOnMissingComponent
import java.util.*

@SurfComponentMeta
@ConditionalOnMissingComponent(PriorityHook::class)
class FallbackPriority : PriorityHook {
    override suspend fun getPriority(uuid: UUID): Int = 0
}