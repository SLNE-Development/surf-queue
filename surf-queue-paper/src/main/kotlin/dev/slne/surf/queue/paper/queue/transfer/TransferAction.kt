package dev.slne.surf.queue.paper.queue.transfer

/**
 * Enumerates all possible outcomes of a player transfer attempt.
 */
enum class TransferAction {
    /** Transfer completed successfully. */
    DONE,
    /** The player could not be found on any proxy. */
    PLAYER_NOT_FOUND,
    /** The player is not currently connected to any backend server. */
    PLAYER_NOT_CONNECTED_TO_A_SERVER,
    /** The player is already on the target server. */
    PLAYER_ALREADY_ON_SERVER,
    /** A plugin cancelled the transfer. */
    PLUGIN_CANCELLED_TRANSFER,
    /** The player was kicked from the target server after connecting. */
    PLAYER_KICKED_FROM_SERVER,
    /** The target server has no available slots. */
    SERVER_FULL,
    /** The player is already in the process of connecting. */
    PLAYER_ALREADY_CONNECTING,
    /** The target server was not found. */
    SERVER_NOT_FOUND,
    /** An unexpected error occurred during transfer. */
    ERROR,
    /** The transfer timed out (target server likely unreachable). */
    TIMEOUT
}