package dev.slne.surf.queue.velocity.util

import dev.slne.surf.queue.velocity.plugin
import java.util.*
import kotlin.jvm.optionals.getOrNull

fun UUID.toVelocityPlayer() = plugin.proxy.getPlayer(this).getOrNull()