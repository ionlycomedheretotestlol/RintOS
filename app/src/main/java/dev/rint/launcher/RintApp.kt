package dev.rint.launcher

import android.app.Application
import dev.rint.launcher.apps.AppRepository
import dev.rint.launcher.core.Stores
import dev.rint.launcher.music.MusicEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RintApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var stores: Stores
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var music: MusicEngine
        private set
    lateinit var assistant: dev.rint.launcher.assistant.AgentEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        stores = Stores(filesDir, scope)
        apps = AppRepository(this, scope)
        apps.setRenames(stores.config.value.renamedApps)
        apps.setIconPack(stores.config.value.icons.iconPack)
        music = MusicEngine(this, scope)
        assistant = dev.rint.launcher.assistant.AgentEngine(this, scope)
        dev.rint.launcher.lock.LockActivity.install(this)
    }

    companion object {
        lateinit var instance: RintApp
            private set
    }
}
