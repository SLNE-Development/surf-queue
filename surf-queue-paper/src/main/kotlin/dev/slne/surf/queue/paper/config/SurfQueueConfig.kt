package dev.slne.surf.queue.paper.config

import dev.slne.surf.queue.paper.plugin
import dev.slne.surf.surfapi.core.api.config.SpongeYmlConfigClass
import org.spongepowered.configurate.objectmapping.ConfigSerializable

/**
 * Plugin configuration for surf-queue on Paper.
 *
 * Loaded from `config.yml` in the plugin data directory using Sponge Configurate.
 *
 * @property maxTransfersPerSecond maximum number of players transferred per tick cycle (default: 20)
 */
@ConfigSerializable
data class SurfQueueConfig(
    val maxTransfersPerSecond: Int = 20,
) {
    companion object : SpongeYmlConfigClass<SurfQueueConfig>(
        SurfQueueConfig::class.java,
        plugin.dataPath,
        "config.yml"
    )
}