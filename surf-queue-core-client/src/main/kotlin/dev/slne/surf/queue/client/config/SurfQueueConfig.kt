package dev.slne.surf.queue.client.config

import dev.slne.surf.api.core.config.SpongeYmlConfigClass
import dev.slne.surf.queue.client.ClientQueueInstance
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class SurfQueueConfig(
    val maxTransfersPerSecond: Int = 20,
) {
    companion object : SpongeYmlConfigClass<SurfQueueConfig>(
        SurfQueueConfig::class.java,
        ClientQueueInstance.get().dataPath,
        "config.yml"
    )
}
