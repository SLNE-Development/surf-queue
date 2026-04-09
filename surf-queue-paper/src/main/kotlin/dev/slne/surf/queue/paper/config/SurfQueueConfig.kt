package dev.slne.surf.queue.paper.config

import dev.slne.surf.queue.paper.plugin
import dev.slne.surf.surfapi.core.api.config.SpongeYmlConfigClass
import org.spongepowered.configurate.objectmapping.ConfigSerializable

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