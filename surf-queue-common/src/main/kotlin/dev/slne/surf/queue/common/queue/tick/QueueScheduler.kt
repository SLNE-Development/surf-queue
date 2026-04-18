package dev.slne.surf.queue.common.queue.tick

import kotlinx.coroutines.*
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.lang.AutoCloseable
import java.util.concurrent.Executors

class QueueScheduler(workerCount: Int) : AutoCloseable {
    init {
        require(workerCount > 0) { "Worker count must be greater than 0" }
    }

    private val dispatchers = Array(workerCount) { index ->
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "surf-queue-worker-$index")
        }.asCoroutineDispatcher()
    }

    fun dispatcherFor(serverName: String): CoroutineDispatcher {
        val index = (serverName.hashCode() and Int.MAX_VALUE) % dispatchers.size
        return dispatchers[index]
    }

    fun scopeFor(serverName: String): CoroutineScope {
        val logger = ComponentLogger.logger("QueueScheduler-$serverName")
        return CoroutineScope(
            SupervisorJob() + dispatcherFor(serverName) + CoroutineExceptionHandler { _, throwable ->
                logger.error("Unhandled exception in QueueScheduler-$serverName:", throwable)
            }
        )
    }

    fun createServiceScope(): CoroutineScope {
        val logger = ComponentLogger.logger("QueueService")
        return CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                logger.error("Unhandled exception in QueueService:", throwable)
            }
        )
    }

    override fun close() {
        dispatchers.forEach { it.close() }
    }
}