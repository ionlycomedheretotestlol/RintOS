package dev.rint.launcher.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

class IconPack private constructor(
    val packageName: String,
    private val res: Resources,
    private val map: Map<String, String>,
) {
    @SuppressLint("DiscouragedApi")
    fun drawableFor(cn: ComponentName): Drawable? {
        val name = map["${cn.packageName}/${cn.className}"] ?: map[cn.packageName] ?: return null
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) }
                .map { it.activityInfo.packageName }
                .distinct()
                .mapNotNull { pkg ->
                    runCatching {
                        IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
        }

        @SuppressLint("DiscouragedApi")
        fun load(context: Context, pkg: String): IconPack {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            val parser: XmlPullParser? = run {
                val id = res.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) res.getXml(id) else runCatching {
                    val f = XmlPullParserFactory.newInstance().newPullParser()
                    f.setInput(res.assets.open("appfilter.xml"), "UTF-8")
                    f
                }.getOrNull()
            }
            if (parser != null) {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val draw = parser.getAttributeValue(null, "drawable")
                        if (comp != null && draw != null && comp.startsWith("ComponentInfo{")) {
                            val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                            val parts = inner.split("/")
                            if (parts.size == 2) {
                                val cls = if (parts[1].startsWith(".")) parts[0] + parts[1] else parts[1]
                                map["${parts[0]}/$cls"] = draw
                                map.putIfAbsent(parts[0], draw)
                            }
                        }
                    }
                    ev = parser.next()
                }
            }
            return IconPack(pkg, res, map)
        }
    }
}
