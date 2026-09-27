package dev.rint.launcher

import android.app.Application
import dev.rint.launcher.apps.AppRepository
import dev.rint.launcher.core.Stores
import dev.rint.launcher.music.MusicEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        dev.rint.launcher.ui.Lang.of(stores.config.value.lang)?.let { dev.rint.launcher.ui.I18n.lang = it }
        scope.launch { stores.config.state.collect { c -> dev.rint.launcher.ui.Lang.of(c.lang)?.let { dev.rint.launcher.ui.I18n.lang = it } } }
        scope.launch {
            var last: Pair<Boolean, Boolean>? = null
            stores.config.state.collect { c ->
                val k = c.notch.enabled to c.notch.everywhere
                if (k != last) { last = k; dev.rint.launcher.system.GlobalNotch.refresh(this@RintApp) }
            }
        }
        // 1.3 final: pixel Rin is the default look
        if (stores.config.value.mascot.version < 1) {
            stores.config.update { it.copy(mascot = it.mascot.copy(style = dev.rint.launcher.core.MascotStyle.PIXEL, color = null, version = 1)) }
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
