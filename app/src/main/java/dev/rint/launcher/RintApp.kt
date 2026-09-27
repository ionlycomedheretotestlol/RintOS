package dev.rint.launcher

import android.app.Application
import dev.rint.launcher.apps.AppRepository
import dev.rint.launcher.core.Stores
import dev.rint.launcher.music.MusicEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RintApp : Application() {
    /** Background failures are recorded for the crash report instead of killing the launcher. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> CrashLog.record(this, e, fatal = false) }
    )
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
        CrashLog.install(this)
        stores = Stores(filesDir, scope)
        // pre-1.3.1 configs saved "play songs with: my music app" as a default; move them to streaming once
        if (stores.config.value.music.version < 2) {
            stores.config.update { it.copy(music = it.music.copy(playVia = dev.rint.launcher.core.PlayVia.STREAM, version = 2)) }
        }
        apps = AppRepository(this, scope)
        apps.setRenames(stores.config.value.renamedApps)
        apps.setIconPack(stores.config.value.icons.iconPack)
        music = MusicEngine(this, scope)
        assistant = dev.rint.launcher.assistant.AgentEngine(this, scope)
        dev.rint.launcher.lock.LockActivity.install(this)
        dev.rint.launcher.system.BatteryWatch.install(this)
    }

    companion object {
        lateinit var instance: RintApp
            private set
    }
}
