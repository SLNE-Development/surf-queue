package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.velocity.util.toVelocityPlayer
import it.unimi.dsi.fastutil.objects.ObjectList
import java.util.*

class QueueDisplay(private val queue: VelocityQueueImpl) {

    private val renderer = QueueActionBarRenderer(queue.serverName)

    private var cachedUuidsWithPosition: ObjectList<UUID>? = null

    suspend fun tick() {
        if (queue.tickCount % 3L == 0L) {
            cachedUuidsWithPosition = queue.getAllUuidsOrderedByPosition()
        }

        updateActionBars()
    }

    private suspend fun updateActionBars() {
        val uuidsWithPosition = cachedUuidsWithPosition ?: return
        val total = uuidsWithPosition.size
        if (total == 0) return

        if (queue.isPaused()) {
            val message = renderer.paused
            for (index in 0 until total) {
                uuidsWithPosition[index].toVelocityPlayer()?.sendActionBar(message)
            }
            return
        }

        val frame = (queue.tickCount % QueueActionBarRenderer.FRAME_COUNT).toInt()
        val totalSuffix = "/$total"

        for (index in 0 until total) {
            val player = uuidsWithPosition[index].toVelocityPlayer() ?: continue

            player.sendActionBar(renderer.running(frame, "${index + 1}$totalSuffix"))
        }
    }
}
