package dev.slne.surf.queue.minestom.command

import dev.slne.surf.api.minestom.command.CommandAPI
import dev.slne.surf.api.core.messages.adventure.buildText
import dev.slne.surf.core.api.common.player.SurfPlayer
import kotlinx.coroutines.Deferred

/**
 * Awaits the player resolved by a `surfOfflinePlayerArgument`, failing the command with a
 * user-facing message when no player matches the given input.
 */
suspend fun Deferred<SurfPlayer?>.awaitOrFail(): SurfPlayer = await() ?: CommandAPI.failWithMessage(
    buildText {
        appendErrorPrefix()
        error("Der Spieler wurde nicht gefunden.")
    }
)
