package dev.slne.surf.queue.common.hook.priority

import kotlinx.coroutines.future.await
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.NodeType
import java.util.*

//@ComponentMeta
//@DependsOnClass(LuckPerms::class)
object LuckpermsPriorityHook : PriorityHook {
    //    companion object {
    const val KEY = "queue-priority"
//    }

    override suspend fun getPriority(uuid: UUID): Int {
        val user = LuckPermsProvider.get()
            .userManager
            .loadUser(uuid)
            .await()

        return user.resolveInheritedNodes(NodeType.META, user.queryOptions)
            .find { it.metaKey == KEY }
            ?.metaValue?.toIntOrNull() ?: 0
    }
}