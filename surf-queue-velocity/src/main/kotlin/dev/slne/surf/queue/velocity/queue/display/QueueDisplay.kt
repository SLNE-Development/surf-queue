package dev.slne.surf.queue.velocity.queue.display

import dev.slne.surf.queue.velocity.queue.VelocitySurfQueue
import dev.slne.surf.queue.velocity.util.toVelocityPlayer
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.util.*

class QueueDisplay(private val queue: VelocitySurfQueue) {

    companion object {
        private val spinner = arrayOf("∙∙∙", "●∙∙", "∙ ●∙", "∙∙ ●", "∙∙∙")
        private val spinnerReversed = spinner.reversedArray()
        private const val PAUSE_CHAR = '⏸'
    }

    private var cachedUuidsWithPosition: Collection<Object2IntMap.Entry<UUID>>? = null

    suspend fun tick() {
        if (queue.getTickCount() % 3L == 0L) {
            cachedUuidsWithPosition = queue.getAllUuidsWithPosition()
        }

        updateActionBars()
    }

    private suspend fun updateActionBars() {
        val uuidsWithPosition = cachedUuidsWithPosition ?: return
        val spinnerIndex = (queue.getTickCount() % spinner.size).toInt()
        val spinnerEnd = spinner[spinnerIndex]
        val spinnerStart = spinnerReversed[spinnerIndex]
        val paused = queue.isPaused()

        for (entry in uuidsWithPosition) {
            val uuid = entry.key
            val position = entry.intValue
            val player = uuid.toVelocityPlayer() ?: continue

            player.sendActionBar(buildText {
                if (paused) {
                    spacer(PAUSE_CHAR)
                    appendSpace()
                    variableValue(queue.serverName)
                    appendSpace()
                    spacer('|')
                    appendSpace()
                    variableValue("Pausiert")
                } else {
                    spacer(spinnerStart)
                    appendSpace()
                    variableValue(queue.serverName)
                    appendSpace()
                    spacer('|')
                    appendSpace()
                    variableValue("$position/${uuidsWithPosition.size}")
                    appendSpace()
                    spacer(spinnerEnd)
                }
            })
        }
    }
}