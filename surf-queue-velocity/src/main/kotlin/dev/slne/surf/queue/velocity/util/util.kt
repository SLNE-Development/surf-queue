package dev.slne.surf.queue.velocity.util

import dev.slne.surf.queue.velocity.plugin
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Resolves a [UUID] to a Velocity [Player][com.velocitypowered.api.proxy.Player] instance.
 *
 * @return the online player, or `null` if not connected
 */
fun UUID.toVelocityPlayer() = plugin.proxy.getPlayer(this).getOrNull()