package dev.slne.surf.queue.velocity.config

import dev.slne.surf.api.core.config.SpongeYmlConfigClass
import dev.slne.surf.api.core.config.type.ConfigDuration
import dev.slne.surf.queue.velocity.plugin
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import kotlin.time.Duration.Companion.seconds

@ConfigSerializable
data class VelocityQueueConfig(
    val reconnect: ReconnectConfig = ReconnectConfig(),
) {

    @ConfigSerializable
    data class ReconnectConfig(
        @param:Comment(
            "Whether reconnect queueing is enabled."
        )
        val enabled: Boolean = true,

        @param:Comment(
            "How long, a reconnect entry remains valid."
        )
        val timeout: ConfigDuration = ConfigDuration(120.seconds),

        @param:Comment(
            "Servers for which reconnect queueing is available."
        )
        val servers: List<String> = listOf("survival"),
    ) {

        fun timeoutDuration() = timeout.asDuration()

        fun isEnabledFor(serverName: String): Boolean {
            return enabled && serverName in servers
        }
    }

    companion object : SpongeYmlConfigClass<VelocityQueueConfig>(
        VelocityQueueConfig::class.java,
        plugin.dataPath,
        "velocity.yml",
    )
}