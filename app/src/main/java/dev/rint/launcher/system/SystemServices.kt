package dev.rint.launcher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Badges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    val listenerConnected = MutableStateFlow(false)

    fun publish(sbns: Array<StatusBarNotification>?) {
        _counts.value = sbns.orEmpty()
            .filter { it.isClearable && !it.isOngoing }
            .groupingBy { it.packageName }
            .eachCount()
    }
}

class RintNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        Badges.listenerConnected.value = true
        Badges.publish(runCatching { activeNotifications }.getOrNull())
    }

    override fun onListenerDisconnected() {
        Badges.listenerConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Badges.publish(runCatching { activeNotifications }.getOrNull())
        sbn?.let { runCatching { RinAlerts.onNotification(this, it) } }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Badges.publish(runCatching { activeNotifications }.getOrNull())
    }
}

class RintAccessibility : AccessibilityService() {
    private var bubble: dev.rint.launcher.assistant.AgentBubble? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bubble?.remove()
        bubble = null
        instance = null
        super.onDestroy()
    }

    /** Floating Rin bubble shown over other apps while the assistant works. */
    fun bubble(): dev.rint.launcher.assistant.AgentBubble =
        bubble ?: dev.rint.launcher.assistant.AgentBubble(this).also { bubble = it }

    fun hideBubble() {
        bubble?.remove()
        bubble = null
    }

    fun setOverlayVisible(visible: Boolean) {
        bubble?.setVisible(visible)
    }

    companion object {
        var instance: RintAccessibility? = null
            private set
    }
}

object SystemActions {
    fun notificationAccessGranted(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(ctx, RintNotificationListener::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    fun accessibilityGranted(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, RintAccessibility::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    @SuppressLint("WrongConstant")
    private fun statusBar(ctx: Context, method: String): Boolean = runCatching {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sb)
        true
    }.getOrDefault(false)

    fun expandNotifications(ctx: Context) {
        if (!statusBar(ctx, "expandNotificationsPanel")) {
            RintAccessibility.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    fun expandQuickSettings(ctx: Context) {
        if (!statusBar(ctx, "expandSettingsPanel")) {
            RintAccessibility.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }

    /** Returns false when the accessibility service isn't enabled, so the caller can explain why. */
    fun lock(): Boolean =
        RintAccessibility.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false

    fun recents(): Boolean =
        RintAccessibility.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false

    private var torchOn = false
    fun toggleFlashlight(ctx: Context): Boolean {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        torchOn = !torchOn
        return runCatching { cm.setTorchMode(id, torchOn); torchOn }.getOrDefault(false)
    }

    fun openAccessibilitySettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openNotificationAccess(ctx: Context) {
        val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun openWallpaperPicker(ctx: Context) {
        val i = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }
}
