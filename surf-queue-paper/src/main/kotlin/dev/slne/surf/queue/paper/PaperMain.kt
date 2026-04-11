package dev.slne.surf.queue.paper

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.queue.common.QueueInstance
import org.bukkit.plugin.java.JavaPlugin

/**
 * Paper plugin entry point for surf-queue.
 *
 * Delegates all lifecycle events to [QueueInstance], which handles Redis
 * connection, component loading, and queue ticker management.
 */
class PaperMain : SuspendingJavaPlugin() {
    override suspend fun onLoadAsync() {
        QueueInstance.get().load()
    }

    override suspend fun onEnableAsync() {
        QueueInstance.get().enable()
    }

    override suspend fun onDisableAsync() {
        QueueInstance.get().disable()
    }
}

/** Convenience accessor for the [PaperMain] plugin instance. */
val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)