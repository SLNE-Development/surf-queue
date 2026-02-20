package dev.slne.surf.queue.common.hook.priority

import dev.slne.surf.surfapi.core.api.component.SurfComponentApi
import dev.slne.surf.surfapi.shared.api.component.SurfComponent
import java.util.*

interface PriorityHook : SurfComponent {
    suspend fun getPriority(uuid: UUID): Int

    companion object {
        fun get() = SurfComponentApi.componentsOfTypeLoaded(PriorityHook::class.java).first()
    }
}

