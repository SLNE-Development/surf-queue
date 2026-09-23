package dev.slne.surf.queue.velocity.hook.integration

import dev.slne.surf.settings.api.SurfSettingsApi
import dev.slne.surf.settings.api.setting.SettingKeys
import java.util.UUID

object SurfSettingsIntegration {
    suspend fun hasAutoQueueAfterReconnectEnabled(playerUuid: UUID): Boolean {
        return SurfSettingsApi.getCachedValueOrLoad(
            playerUuid,
            SettingKeys.AUTO_QUEUE_AFTER_RECONNECT,
        )
    }
}