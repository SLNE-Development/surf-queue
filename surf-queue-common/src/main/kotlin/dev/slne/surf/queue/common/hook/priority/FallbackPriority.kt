package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.api.shared.api.component.SurfComponentMeta
import dev.slne.surf.api.shared.api.component.requirement.ConditionalOnMissingComponent
import java.util.*

/**
 * Default [PriorityHook] implementation that always returns priority `0`.
 *
 * Activated automatically by `@ConditionalOnMissingComponent` when no other
 * [PriorityHook] (e.g., [LuckpermsPriorityHook]) is available.
 */
@SurfComponentMeta
@ConditionalOnMissingComponent(PriorityHook::class)
class FallbackPriority : PriorityHook {
    override suspend fun getPriority(uuid: UUID): Int = 0
}