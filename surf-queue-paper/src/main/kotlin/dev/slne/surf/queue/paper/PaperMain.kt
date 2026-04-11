package dev.slne.surf.queue.paper

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.queue.common.QueueInstance
import org.bukkit.plugin.java.JavaPlugin

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

val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)