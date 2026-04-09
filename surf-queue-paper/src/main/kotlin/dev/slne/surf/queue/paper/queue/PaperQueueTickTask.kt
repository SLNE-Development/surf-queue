package dev.slne.surf.queue.paper.queue

import com.github.shynixn.mccoroutine.folia.launch
import dev.slne.surf.core.api.common.SurfCoreApi
import dev.slne.surf.queue.common.queue.RedisQueueService
import dev.slne.surf.queue.paper.plugin
import dev.slne.surf.surfapi.core.api.util.logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

object PaperQueueTickTask {
    private val log = logger()
    private var job: Job? = null
    private lateinit var serverName: String

    fun start() {
        this.serverName = SurfCoreApi.getCurrentServerName()
        log.atInfo().log("Starting queue tick task for server: %s", serverName)

        job = plugin.launch {
            while (isActive) {
                delay(1.seconds)
                tick()
            }
        }
    }

    suspend fun shutdown() {
        job?.cancelAndJoin()
        job = null
    }

    private suspend fun tick() {
        val queue = RedisQueueService.get().get(serverName) as? PaperSurfQueue
        if (queue == null) {
            log.atWarning().log("Queue for server %s not found", serverName)
            return
        }

        try {
            queue.tickSecond()
        } catch (e: Exception) {
            log.atWarning()
                .withCause(e)
                .log("Error during tickSecond for queue %s", serverName)
        }
    }
}