package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.queue.velocity.util.toVelocityPlayer
import dev.slne.surf.surfapi.core.api.messages.adventure.buildText
import it.unimi.dsi.fastutil.objects.ObjectList
import java.util.*

/**
 * Sends action bar messages to all queued players showing their position
 * and an animated spinner.
 *
 * Refreshes the UUID list every 3 ticks and sends an action bar update
 * every tick. When the queue is paused, shows a pause indicator instead
 * of the spinner animation.
 *
 * @param queue the [VelocityQueueImpl] this display belongs to
 */
class QueueDisplay(private val queue: VelocityQueueImpl) {

    companion object {
        private val spinner = arrayOf("∙∙∙", "●∙∙", "∙ ●∙", "∙∙ ●", "∙∙∙")
        private val spinnerReversed = spinner.reversedArray()
        private const val PAUSE_CHAR = '⏸'
    }

    private var cachedUuidsWithPosition: ObjectList<UUID>? = null

    /** Refreshes the cached UUID list (every 3 ticks) and updates action bars. */
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