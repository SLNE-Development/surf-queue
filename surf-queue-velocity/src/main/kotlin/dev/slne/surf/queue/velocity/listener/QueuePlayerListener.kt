package dev.slne.surf.queue.velocity.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import dev.slne.surf.api.core.messages.adventure.sendText
import dev.slne.surf.api.core.util.logger
import dev.slne.surf.queue.api.SurfQueue
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.velocity.config.VelocityQueueConfig
import dev.slne.surf.queue.velocity.hook.SettingsHook
import dev.slne.surf.queue.velocity.plugin
import dev.slne.surf.queue.velocity.queue.VelocityQueueImpl
import dev.slne.surf.queue.velocity.reconnect.QueueReconnectStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.jvm.optionals.getOrNull

object QueuePlayerListener {
    private val log = logger()

    @Subscribe
    suspend fun onPostLogin(event: PostLoginEvent) {
        val uuid = event.player.uniqueId
        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocityQueueImpl) { "Queue must be VelocityQueueImpl" }
                launch {
                    try {
                        queue.markPlayerReconnected(uuid)
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Failed to clear grace period for %s in queue %s", uuid, queue.serverName)
                    }
                }
            }
        }
    }

    @Subscribe
    suspend fun onServerConnected(event: ServerConnectedEvent) {
        if (event.previousServer.isPresent) {
            return
        }

        val uuid = event.player.uniqueId

        val targetServer = try {
            QueueReconnectStore.peek(uuid)
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Failed to read reconnect entry for %s", uuid)
            return
        } ?: return

        val reconnectConfig = VelocityQueueConfig.getConfig().reconnect

        if (!reconnectConfig.isEnabledFor(targetServer)) {
            QueueReconnectStore.remove(uuid)
            return
        }

        if (event.server.serverInfo.name == targetServer) {
            QueueReconnectStore.remove(uuid)
            return
        }

        if (plugin.proxy.getServer(targetServer).isEmpty) {
            QueueReconnectStore.remove(uuid)

            log.atWarning()
                .log(
                    "Discarded reconnect entry for %s because server %s is not registered",
                    uuid,
                    targetServer,
                )

            return
        }

        val autoQueueEnabled = try {
            SettingsHook.hasAutoQueueAfterReconnectEnabled(uuid)
        } catch (e: Exception) {
            QueueReconnectStore.remove(uuid)

            log.atWarning()
                .withCause(e)
                .log(
                    "Failed to load reconnect setting for %s",
                    uuid,
                )

            return
        }

        if (!autoQueueEnabled) {
            QueueReconnectStore.remove(uuid)
            return
        }

        try {
            val enqueued = SurfQueue
                .byServer(targetServer)
                .enqueue(uuid)

            QueueReconnectStore.remove(uuid)

            if (enqueued) {
                event.player.sendText {
                    appendInfoPrefix()
                    info("Du wirst automatisch wieder mit ")
                    variableValue(targetServer)
                    info(" verbunden. Du kannst diese Funktion jederzeit in der Lobby im Settings-Menü deaktivieren.")
                }

                log.atInfo()
                    .log(
                        "Automatically queued %s for %s after reconnect",
                        uuid,
                        targetServer,
                    )
            }
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log(
                    "Failed to automatically queue %s for %s after reconnect",
                    uuid,
                    targetServer,
                )
        }
    }

    @Subscribe
    suspend fun onDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId

        storeReconnectEntry(event)

        coroutineScope {
            for (queue in RedisQueueService.get().getAll()) {
                require(queue is VelocityQueueImpl)
                launch {
                    try {
                        queue.markPlayerDisconnected(uuid)
                    } catch (e: Exception) {
                        log.atWarning()
                            .withCause(e)
                            .log("Failed to mark disconnect for %s in queue %s", uuid, queue.serverName)
                    }
                }
            }
        }
    }

    private suspend fun storeReconnectEntry(event: DisconnectEvent) {
        if (event.loginStatus != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return
        }

        val reconnectConfig = VelocityQueueConfig.getConfig().reconnect

        if (!reconnectConfig.enabled) {
            return
        }

        val serverName = event.player.currentServer
            .getOrNull()
            ?.serverInfo
            ?.name
            ?: return

        if (!reconnectConfig.isEnabledFor(serverName)) {
            return
        }

        try {
            val timeoutSeconds = reconnectConfig.timeoutDuration().inWholeSeconds

            QueueReconnectStore.put(
                uuid = event.player.uniqueId,
                serverName = serverName,
                timeoutSeconds = timeoutSeconds,
            )

            log.atInfo()
                .log(
                    "Stored reconnect entry for %s to %s for %d seconds",
                    event.player.uniqueId,
                    serverName,
                    timeoutSeconds,
                )
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log(
                    "Failed to store reconnect entry for %s to %s",
                    event.player.uniqueId,
                    serverName,
                )
        }
    }
}