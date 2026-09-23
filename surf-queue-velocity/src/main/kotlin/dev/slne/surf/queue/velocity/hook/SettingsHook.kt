package dev.slne.surf.queue.velocity.hook

import dev.slne.surf.queue.velocity.hook.integration.SurfSettingsIntegration
import dev.slne.surf.queue.velocity.plugin
import java.util.UUID

object SettingsHook {
    private const val SETTINGS_PLUGIN_ID = "surf-settings-velocity"

    val available: Boolean
        get() = plugin.proxy.pluginManager
            .getPlugin(SETTINGS_PLUGIN_ID)
            .isPresent

    suspend fun hasAutoQueueAfterReconnectEnabled(playerUuid: UUID): Boolean {
        return !available || SurfSettingsIntegration.hasAutoQueueAfterReconnectEnabled(playerUuid)
    }
}