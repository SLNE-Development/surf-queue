package dev.slne.surf.queue.velocity.queue

import dev.slne.surf.api.core.messages.Colors
import dev.slne.surf.api.core.messages.adventure.text
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.textOfChildren
import net.kyori.adventure.text.format.Style

class QueueActionBarRenderer(serverName: String) {

    companion object {
        private val SPINNER = arrayOf("∙∙∙", "●∙∙", "∙ ●∙", "∙∙ ●", "∙∙∙")
        private val SPINNER_REVERSED = SPINNER.reversedArray()
        private const val PAUSE_CHAR = '⏸'

        val FRAME_COUNT = SPINNER.size

        /** Kept as a named constant for consistency with the other shared components below. */
        private val SPACE = Component.space()
        private val SEPARATOR = text("|", Colors.SPACER)
        private val PAUSE_MARKER = text(PAUSE_CHAR.toString(), Colors.SPACER)
        private val PAUSED_LABEL = text("Pausiert", Colors.VARIABLE_VALUE)

        private val POSITION_STYLE: Style = Style.style(Colors.VARIABLE_VALUE)

        private val SPINNER_HEAD = Array(FRAME_COUNT) { text(SPINNER_REVERSED[it], Colors.SPACER) }
        private val SPINNER_TAIL = Array(FRAME_COUNT) { text(SPINNER[it], Colors.SPACER) }
    }

    /** `<spinnerHead> <serverName> | `  */
    private val prefixes: Array<List<Component>>

    /** ` <spinnerTail>` */
    private val suffixes: Array<List<Component>>

    val paused: Component

    init {
        val serverNameComponent = text(serverName, Colors.VARIABLE_VALUE)

        prefixes = Array(FRAME_COUNT) { frame ->
            listOf(SPINNER_HEAD[frame], SPACE, serverNameComponent, SPACE, SEPARATOR, SPACE)
        }
        suffixes = Array(FRAME_COUNT) { frame ->
            listOf(SPACE, SPINNER_TAIL[frame])
        }

        paused = textOfChildren(PAUSE_MARKER, SPACE, serverNameComponent, SPACE, SEPARATOR, SPACE, PAUSED_LABEL)
    }

    fun running(frame: Int, positionText: String): Component = Component.text()
        .append(prefixes[frame])
        .append(Component.text(positionText, POSITION_STYLE))
        .append(suffixes[frame])
        .build()
}
