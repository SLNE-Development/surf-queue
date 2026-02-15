package dev.slne.surf.queue.paper

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.queue.common.SurfQueueInstance

class PaperMain : SuspendingJavaPlugin() {
    override suspend fun onLoadAsync() {
        SurfQueueInstance.get().load()
    }

    override suspend fun onEnableAsync() {
        SurfQueueInstance.get().enable()
    }

    override suspend fun onDisableAsync() {
        SurfQueueInstance.get().disable()
    }
}