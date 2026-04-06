package dev.slne.surf.queue.common.queue

object RedisQueueScorePacker {
    private const val PRIORITY_BITS = 7
    private const val DELTA_MS_BITS = 38
    private const val SEQUENCE_BITS = 8

    private const val DELTA_MS_SHIFT = SEQUENCE_BITS
    private const val PRIORITY_SHIFT = DELTA_MS_BITS + SEQUENCE_BITS

    private const val SEQUENCE_MASK = (1L shl SEQUENCE_BITS) - 1   // 0xFF (255)
    private const val DELTA_MS_MASK = (1L shl DELTA_MS_BITS) - 1   // 0x3FFFFFFFFF (274,877,906,943)
    private const val PRIORITY_MASK = (1L shl PRIORITY_BITS) - 1   // 0x7F

    const val MAX_PRIORITY = PRIORITY_MASK.toInt()
    const val MAX_DELTA_MS = DELTA_MS_MASK
    const val MAX_SEQUENCE = SEQUENCE_MASK

    fun unpack(score: Double): Unpacked {
        val value = score.toLong()
        val priority = PRIORITY_MASK - ((value shr (DELTA_MS_BITS + SEQUENCE_BITS)) and PRIORITY_MASK).toInt()
        val deltaMs = (value shr SEQUENCE_BITS) and DELTA_MS_MASK
        val sequence = (value and SEQUENCE_MASK).toInt()
        return Unpacked(priority.toInt(), deltaMs, sequence)
    }

    fun pack(priority: Int, deltaMs: Long, sequence: Int): Double {
        // Priority: invert for desired direction.
        val invPriority = PRIORITY_MASK - (priority.toLong() and PRIORITY_MASK)

        require(invPriority in 0..PRIORITY_MASK) { "Priority bounds" }
        require(deltaMs in 0..DELTA_MS_MASK) { "deltaMs out of range" }
        require(sequence in 0..SEQUENCE_MASK) { "sequence out of range" }

        val value = (invPriority shl (DELTA_MS_BITS + SEQUENCE_BITS)) or
                ((deltaMs and DELTA_MS_MASK) shl SEQUENCE_BITS) or
                (sequence.toLong() and SEQUENCE_MASK)

        return value.toDouble()
    }

    data class Unpacked(val priority: Int, val deltaMs: Long, val sequence: Int)
}