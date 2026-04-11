package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.velocity.util.toVelocityPlayer
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import it.unimi.dsi.fastutil.objects.ObjectList
import java.util.*

class QueueDisplay(private val queue: VelocitySurfQueue) {

    companion object {
        private val spinner = arrayOf("∙∙∙", "●∙∙", "∙ ●∙", "∙∙ ●", "∙∙∙")
        private val spinnerReversed = spinner.reversedArray()
        private const val PAUSE_CHAR = '⏸'
    }

    private var cachedUuidsWithPosition: ObjectList<UUID>? = null

    suspend fun tick() {
        if (queue.tickCount % 3L == 0L) {
            cachedUuidsWithPosition = queue.getAllUuidsOrderedByPosition()
        }

        updateActionBars()
    }

    private suspend fun updateActionBars() {
        val uuidsWithPosition = cachedUuidsWithPosition ?: return
        val spinnerIndex = (queue.tickCount % spinner.size)
        val spinnerEnd = spinner[spinnerIndex]
        val spinnerStart = spinnerReversed[spinnerIndex]
        val paused = queue.isPaused()

        for ((index, uuid) in uuidsWithPosition.withIndex()) {
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
                    variableValue("${index + 1}/${uuidsWithPosition.size}")
                    appendSpace()
                    spacer(spinnerEnd)
                }
            })
        }
    }
}