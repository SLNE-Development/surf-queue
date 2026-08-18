package dev.slne.surf.queue.client.platform

/**
 * Reason a server refused a player's connection attempt.
 */
enum class TransferKickReason {
    FULL_SERVER,
    NOT_WHITELISTED,
    OTHER
}
