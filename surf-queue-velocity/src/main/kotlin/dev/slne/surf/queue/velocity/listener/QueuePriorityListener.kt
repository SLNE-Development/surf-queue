package dev.slne.surf.queue.velocity.listener

import dev.slne.surf.api.core.luckperms.LuckPermsAccess
import dev.slne.surf.api.core.util.logger
import dev.slne.surf.queue.common.QueueInstance
import dev.slne.surf.queue.common.priority.LuckpermsPriorityResolver
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.velocity.plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.luckperms.api.event.EventSubscription
import net.luckperms.api.event.user.UserDataRecalculateEvent
import kotlin.coroutines.cancellation.CancellationException

/**
 * Moves queued players forward when their LuckPerms queue priority increases.
 */
object QueuePriorityListener {
    private val log = logger()

    private var subscription: EventSubscription<UserDataRecalculateEvent>? = null
    private var scope: CoroutineScope? = null

    fun register() {
        check(subscription == null) { "QueuePriorityListener already registered" }

        scope = QueueInstance.get().queueScheduler.createServiceScope()
        subscription = LuckPermsAccess.luckperms.eventBus.subscribe(
            plugin.container,
            UserDataRecalculateEvent::class.java,
            ::onUserDataRecalculate
        )
    }

    fun unregister() {
        subscription?.close()
        subscription = null

        scope?.cancel("QueuePriorityListener unregistered")
        scope = null
    }

    private fun onUserDataRecalculate(event: UserDataRecalculateEvent) {
        val scope = scope ?: return
        val uuid = event.user.uniqueId
        val priority = LuckpermsPriorityResolver.getPriority(event.user)

        if (priority <= 0) return

        scope.launch {
            for (queue in RedisQueueService.get().getAll()) {
                launch {
                    try {
                        queue.raisePriority(uuid, priority)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e

                        log.atWarning()
                            .withCause(e)
                            .log("Failed to raise priority of %s in queue %s", uuid, queue.serverName)
                    }
                }
            }
        }
    }
}
