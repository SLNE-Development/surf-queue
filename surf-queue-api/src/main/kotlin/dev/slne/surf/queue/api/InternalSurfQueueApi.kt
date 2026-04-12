package dev.slne.surf.queue.api

import dev.slne.surf.surfapi.shared.api.annotation.InternalAPIMarker

/**
 * Opt-in annotation marking internal surf-queue APIs that are not intended for
 * external use.
 *
 * Any declaration annotated with this marker requires an explicit `@OptIn(InternalSurfQueueApi::class)`
 * at the call site, ensuring consumers acknowledge they are using an unsupported,
 * internal API that may change without notice.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal and should not be used outside of the library"
)
@InternalAPIMarker
annotation class InternalSurfQueueApi
