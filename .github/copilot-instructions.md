# Copilot Instructions for surf-queue

## Build & Test Commands

### Basic Gradle Commands

```bash
# Build the project
./gradlew build

# Run all checks including tests (required before committing)
./gradlew check

# Clean build artifacts
./gradlew clean

# Build with a full rebuild (clean + build)
./gradlew clean build
```

**Note:** This project uses the Gradle Wrapper (`./gradlew`). Always use the wrapper instead of a system-wide Gradle installation.

### Project Structure

The project is a multi-module Gradle setup:
- **surf-queue-common**: Core queue logic (Redis-backed, cross-platform abstractions)
- **surf-queue-paper**: Paper (Spigot) plugin implementation
- **surf-queue-velocity**: Velocity proxy plugin implementation
- **surf-queue-api**: Public API module for external consumption
- **buildSrc**: Convention plugins and shared build configuration

## High-Level Architecture

### System Overview

surf-queue is a distributed server queue system for Minecraft networks, allowing players to queue for servers and be automatically transferred when space becomes available.

### Queue Storage & Coordination

- **Redis-backed**: All queue state (entries, scores, locks) is stored in Redis, enabling shared state across multiple instances
- **Distributed Locks**: Uses Redis-based locking (RedisQueueLockManager) to coordinate multi-process access
- **Scoring System**: Uses a packed Redis ZSET score that encodes:
  - Priority (from LuckPerms permissions)
  - Relative enqueue timestamp (milliseconds since a per-queue epoch)
  - Sequence number (for tie-breaking within the same millisecond)

### Module Dependencies

```
surf-queue-api (public interfaces)
    ↑
surf-queue-common (core Redis queue logic, cross-platform abstractions)
    ↑
surf-queue-paper (Paper/Folia implementation) & surf-queue-velocity (Velocity implementation)
```

### Core Queue Implementation

- **AbstractQueue**: Base class providing core enqueue/dequeue logic with Redis backing
- **AbstractTickableQueue**: Extends AbstractQueue to add periodic processing (cleanup, metrics, transfers)
- **Platform-specific implementations**: PaperQueueImpl, VelocityQueueImpl override platform-specific behaviors (commands, events, metrics)

### Key Components

- **RedisQueueStore**: Handles low-level Redis operations (entries, scores, epoch initialization)
- **RedisQueueLockManager**: Distributed locking for safe concurrent access
- **QueueScheduler**: Periodic ticking of queue operations (cleanup, transfers, metrics)
- **SafeQueueTick**: Utility for safe exception handling during ticks with timeout support

### Coroutine Architecture

- Uses **mcCoroutine** for suspending plugin lifecycle hooks
- Paper plugin: SuspendingJavaPlugin (Folia-aware)
- Velocity plugin: SuspendingPluginContainer
- All queue operations are suspend functions for non-blocking Redis I/O

## Key Conventions

### Logging

Use printf-style logging with the `logger()` utility:

```kotlin
log.atInfo().log("Enqueued %s in queue %s with priority %d", uuid, serverName, priority)
log.atWarning().withCause(e).log("Failed to process %s", component)
```

**Pattern:** `log.atLevel().log("message with %s placeholders", args...)`

Exception logging always includes the cause:
```kotlin
log.atWarning().withCause(e).log("Error message")
```

### Exception Handling

Use `SafeQueueTick` utility functions for safe tick execution with exception handling:

```kotlin
SafeQueueTick.tickSafe(queue, "componentName") {
    // Synchronous work that should not crash the tick
}

SafeQueueTick.tickSafeWithTimeout(queue, "componentName", Duration.seconds(5)) {
    // Suspend work with timeout protection
}
```

These automatically log warnings and swallow non-cancellation exceptions, preventing plugin crashes.

### Code Style

- **Kotlin style**: Follows `official` Kotlin code style (from `gradle.properties`)
- **suspend functions**: Use suspend functions for all I/O operations (Redis calls, network operations)
- **No null safety exceptions**: StdLib default dependency is disabled; avoid relying on implicit stdlib
- **UUID-based player identification**: Players are consistently referenced as UUIDs (not usernames)

### Queue Entry Management

- **QueueEntry**: Codec-based serialization for Redis storage
- **RedisQueueScore**: Packed score format (priority + timestamp + sequence)
- Entries are ordered by score (high priority first, then oldest first, then sequence order)

### Configuration

- Multi-queue support: Configured per-server via `SurfQueueConfig`
- Custom priority resolution: Pluggable via `LuckpermsPriorityResolver` (can be overridden)
- Server dependencies: Paper/Velocity plugins declare their dependencies (e.g., LuckPerms required, PolarLoader soft-dependency)

## Gradle Build Configuration

- **Caching enabled**: Uses Gradle build cache for faster incremental builds
- **Configuration cache disabled**: Not enabled; clean builds may be slower
- **Parallel builds enabled**: Gradle runs tasks in parallel
- **Version catalog**: Dependencies declared in `gradle/libs.versions.toml`
- **Convention plugins**: Build configuration via `dev.slne.surf.api.gradle` plugins
