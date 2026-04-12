package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.surfapi.shared.api.component.ComponentMeta
import dev.slne.surf.surfapi.shared.api.component.requirement.ConditionalOnMissingComponent
import java.util.UUID

/**
 * Default [PriorityHook] implementation that always returns priority `0`.
 *
 * Activated automatically by `@ConditionalOnMissingComponent` when no other
 * [PriorityHook] (e.g., [LuckpermsPriorityHook]) is available.
 */
@ComponentMeta
@ConditionalOnMissingComponent(PriorityHook::class)
class FallbackPriority : PriorityHook {
    override suspend fun getPriority(uuid: UUID): Int = 0
}