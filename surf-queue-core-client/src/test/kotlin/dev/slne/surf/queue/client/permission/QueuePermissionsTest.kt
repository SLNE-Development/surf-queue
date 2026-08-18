package dev.slne.surf.queue.client.permission

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueuePermissionsTest {

    @Test
    fun `command nodes keep their published names`() {
        assertEquals("surf.queue.command.queue", QueuePermissions.COMMAND_QUEUE)
        assertEquals("surf.queue.command.queue.cleanup", QueuePermissions.COMMAND_CLEANUP)
        assertEquals("surf.queue.command.queue.clear", QueuePermissions.COMMAND_CLEAR)
        assertEquals("surf.queue.command.queue.dequeue", QueuePermissions.COMMAND_DEQUEUE)
        assertEquals("surf.queue.command.queue.enqueue", QueuePermissions.COMMAND_ENQUEUE)
        assertEquals("surf.queue.command.queue.fix", QueuePermissions.COMMAND_FIX)
        assertEquals("surf.queue.command.queue.info", QueuePermissions.COMMAND_INFO)
        assertEquals("surf.queue.command.queue.list", QueuePermissions.COMMAND_LIST)
        assertEquals("surf.queue.command.queue.pause", QueuePermissions.COMMAND_PAUSE)
        assertEquals("surf.queue.command.metrics", QueuePermissions.COMMAND_METRICS)
    }
}
