package dev.slne.surf.queue.common.queue

/**
 * Packs queue metadata into a single 53-bit value stored as a Redis ZSET score (Double).
 *
 * The layout is designed to:
 * - Preserve total ordering inside Redis sorted sets
 * - Avoid precision loss (fits exactly into IEEE-754 double: 53 bits)
 * - Support priority, timestamp, and tie-breaking sequence
 *
 * ## Bit layout:
 *
 * `priority:7`|`deltaMs:40`|`sequence:6`
 *
 *
 * Total: 53 bits → exactly representable as Double
 *
 * ## Field details:
 *
 * - priority (7 bits)
 *   Higher priority should come first.
 *   To achieve this with ascending ZSET ordering, the value is inverted:
 *     `storedPriority = MAX_PRIORITY - priority`
 *
 * - deltaMs (40 bits)
 *   Time component (usually epoch delta in milliseconds).
 *   Provides the primary ordering.
 *   Range: ~34.8 years
 *
 * - sequence (6 bits)
 *   Tie-breaker for entries created within the same millisecond.
 *   Range: 0–63 (64 entries per millisecond).
 *
 * Ordering behavior in Redis ZSET:
 * 1. Lower storedPriority → higher logical priority
 * 2. Lower deltaMs → earlier timestamp
 * 3. Lower sequence → earlier insertion within same millisecond
 *
 * Important:
 * - The total bit size MUST NOT exceed 53 bits, otherwise precision loss occurs in Double.
 * - All values must be within their defined ranges.
 */
@JvmInline
value class RedisQueueScore(val packed: Double) {
    companion object {
        private const val PRIORITY_BITS = 7
        private const val DELTA_MS_BITS = 40
        private const val SEQUENCE_BITS = 6

        private const val DELTA_MS_SHIFT = SEQUENCE_BITS
        private const val PRIORITY_SHIFT = DELTA_MS_BITS + SEQUENCE_BITS

        private const val SEQUENCE_MASK = (1L shl SEQUENCE_BITS) - 1   // 0x3F (63)
        private const val DELTA_MS_MASK = (1L shl DELTA_MS_BITS) - 1   // 0xFFFFFFFFFF
        private const val PRIORITY_MASK = (1L shl PRIORITY_BITS) - 1   // 0x7F

        const val MAX_PRIORITY = PRIORITY_MASK.toInt()
        const val MAX_DELTA_MS = DELTA_MS_MASK
        const val MAX_SEQUENCE = SEQUENCE_MASK.toInt()

        /**
         * Largest representable packed value, with every field at its maximum.
         * Equals `2^53 - 1`, the biggest integer a Double still holds exactly.
         */
        const val MAX_PACKED = (1L shl (PRIORITY_BITS + DELTA_MS_BITS + SEQUENCE_BITS)) - 1

        /**
         * Packs priority, timestamp and sequence into a single Double score.
         *
         * @param priority logical priority (0..MAX_PRIORITY), higher = more important
         * @param deltaMs time value (usually epoch delta in ms)
         * @param sequence tie-breaker for same timestamp (0..MAX_SEQUENCE)
         *
         * @return packed score suitable for Redis ZSET
         *
         * @throws IllegalArgumentException if any value is out of range
         */
        fun pack(priority: Int, deltaMs: Long, sequence: Int): RedisQueueScore {
            require(priority in 0..MAX_PRIORITY) { "priority out of range" }
            require(deltaMs in 0..MAX_DELTA_MS) { "deltaMs out of range" }
            require(sequence in 0..MAX_SEQUENCE) { "sequence out of range" }

            val invertedPriority = (MAX_PRIORITY - priority).toLong()

            val value =
                (invertedPriority shl PRIORITY_SHIFT) or
                        (deltaMs shl DELTA_MS_SHIFT) or
                        sequence.toLong()

            return RedisQueueScore(value.toDouble())
        }

        fun optional(value: Double?): RedisQueueScore? = value?.let { RedisQueueScore(it) }
    }

    /**
     * @return packed score as a Long
     */
    private val packedLong get() = packed.toLong()

    /**
     * @return logical priority (0..[MAX_PRIORITY]), higher = more important
     */
    val priority: Int
        get() = MAX_PRIORITY - ((packedLong shr PRIORITY_SHIFT) and PRIORITY_MASK).toInt()

    /**
     * @return time value (usually epoch delta in ms)
     */
    val deltaMs: Long
        get() = (packedLong shr DELTA_MS_SHIFT) and DELTA_MS_MASK

    /**
     * @return tie-breaker for same timestamp (0..[MAX_SEQUENCE])
     */
    val sequence: Int
        get() = (packedLong and SEQUENCE_MASK).toInt()

    /**
     * The smallest score that sorts strictly after this one, or `null` if this score is
     * already [MAX_PACKED] and cannot be incremented.
     *
     * Incrementing the packed value advances [sequence] first and carries into [deltaMs]
     * and then the priority band, so the result always orders directly behind `this` —
     * including when that means leaving the original priority band.
     */
    fun nextAfter(): RedisQueueScore? {
        val next = packedLong + 1
        return if (next > MAX_PACKED) null else RedisQueueScore(next.toDouble())
    }
}