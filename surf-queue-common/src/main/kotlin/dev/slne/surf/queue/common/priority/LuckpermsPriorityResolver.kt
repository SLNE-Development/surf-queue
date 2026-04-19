package dev.slne.surf.queue.common.priority

import dev.slne.surf.api.core.luckperms.LuckPermsAccess
import net.luckperms.api.node.NodeType
import java.util.*

/**
 * [PriorityHook] implementation that resolves queue priority from a LuckPerms
 * meta key.
 *
 * Reads the `queue-priority` meta value from the player's inherited nodes.
 * If the meta key is absent or not a valid integer, the priority defaults to `0`.
 */
object LuckpermsPriorityResolver {
    /** The LuckPerms meta key used to store a player's queue priority. */
    const val KEY = "queue-priority"

    suspend fun getPriority(uuid: UUID): Int {
        val user = LuckPermsAccess.loadUser(uuid)
        return user.resolveInheritedNodes(NodeType.META, user.queryOptions)
            .find { it.metaKey == KEY }
            ?.metaValue?.toIntOrNull() ?: 0
    }
}