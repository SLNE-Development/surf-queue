package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.api.core.component.SurfComponentApi
import dev.slne.surf.api.shared.api.component.SurfComponent
import java.util.*

interface PriorityHook : SurfComponent {
    suspend fun getPriority(uuid: UUID): Int

    companion object {
        fun get() = SurfComponentApi.componentsOfTypeLoaded(PriorityHook::class.java).first()
    }
}

