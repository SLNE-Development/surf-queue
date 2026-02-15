package dev.slne.surf.queue.common.queue.codec

import dev.slne.surf.queue.common.queue.QueueEntry
import dev.slne.surf.redis.libs.redisson.client.codec.BaseCodec
import dev.slne.surf.redis.libs.redisson.client.protocol.Decoder
import dev.slne.surf.redis.libs.redisson.client.protocol.Encoder
import io.netty.buffer.Unpooled
import java.util.*

class QueueEntryCodec : BaseCodec() {
    private val encoder = Encoder { entry ->
        val entry = entry as QueueEntry
        val buf = Unpooled.buffer()

        try {
            buf.writeLong(entry.uuid.mostSignificantBits)
            buf.writeLong(entry.uuid.leastSignificantBits)
            buf.writeLong(entry.addedAt)
            buf.writeInt(entry.priority)
        } catch (e: Throwable) {
            buf.release()
            throw e
        }

        buf
    }

    private val decoder = Decoder<Any> { buf, _ ->
        val uuid = UUID(buf.readLong(), buf.readLong())
        val addedAt = buf.readLong()
        val priority = buf.readInt()

        QueueEntry(uuid, addedAt, priority)
    }

    override fun getValueDecoder(): Decoder<in Any>? {
        return decoder
    }

    override fun getValueEncoder(): Encoder {
        return encoder
    }
}