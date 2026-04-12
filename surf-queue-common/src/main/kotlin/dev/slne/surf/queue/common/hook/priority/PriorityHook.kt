package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.api.core.component.SurfComponentApi
import dev.slne.surf.api.shared.api.component.SurfComponent
import java.util.*

/**
 * Hook interface for resolving a player's queue priority.
 *
 * Implementations are registered as [SurfComponent]s. The active implementation
 * is determined by the component system — [LuckpermsPriorityHook] when LuckPerms
 * is present, otherwise [FallbackPriority].
 */
interface PriorityHook : SurfComponent {

    /**
     * Returns the queue priority for the player identified by [uuid].
     *
     * @param uuid the player's unique identifier
     * @return a non-negative priority value; higher values mean higher priority
     */
    suspend fun getPriority(uuid: UUID): Int

    companion object {
        /** Returns the first loaded [PriorityHook] implementation. */
        fun get() = SurfComponentApi.componentsOfTypeLoaded(PriorityHook::class.java).first()
    }
}

