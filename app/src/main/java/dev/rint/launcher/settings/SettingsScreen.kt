package dev.rint.launcher.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import kotlinx.coroutines.launch
import dev.rint.launcher.apps.IconPack
import dev.rint.launcher.core.Binding
import dev.rint.launcher.core.GestureAction
import dev.rint.launcher.core.IconShape
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.RintJson
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.home.SystemSettingsLinks
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.music.rememberMusicApps
import dev.rint.launcher.system.SystemActions
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Pill
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintSprings
import dev.rint.launcher.ui.Shapes
import dev.rint.launcher.ui.argbLong
import dev.rint.launcher.ui.color
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberHaptic
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

private val store get() = RintApp.instance.stores.config

@Composable
fun SettingsScreen(state: LauncherState) {
    val look = LocalRint.current
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<String?>(null) }
    BackHandler { if (sheet != null) sheet = null else state.dismissTop() }
    val ctx0 = LocalContext.current
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.rint.launcher.ui.PhotoWallpaper.save(ctx0, uri) }
            if (ok) RintApp.instance.stores.config.update { it.copy(look = it.look.copy(wallpaper = dev.rint.launcher.core.WallpaperMode.PHOTO, photoVersion = System.currentTimeMillis())) }
            state.say(if (ok) "wallpaper set" else "couldn't open that photo")
        }
    }
    PhotoPickRequest.launch = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Box(Modifier.fillMaxSize().background(look.colors.bg)) {
        AnimatedContent(
            targetState = state.settingsSection,
            label = "settings",
            transitionSpec = {
                if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                else (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            },
        ) { sectionId ->
            val section = Schema.find(sectionId)
            if (section == null) Home(state, query, { query = it }, onAction = { sheet = it })
            else SectionPage(section, state, onAction = { sheet = it })
        }
        Sheets(sheet, state) { sheet = null }
    }
}

@Composable
private fun Home(state: LauncherState, query: String, onQuery: (String) -> Unit, onAction: (String) -> Unit) {
    val look = LocalRint.current
    val cfg = look.cfg
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.Bottom) {
                Text("Settings", fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = look.colors.text, modifier = Modifier.weight(1f))
                RinSprite(Pose.SIT, 56.dp)
            }
        }
        item { SettingsSearch(query, onQuery) }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            val found = Schema.sections.flatMap { sec -> sec.opts.filter { it !is Opt.Header && (it.title.lowercase().contains(q) || it.desc?.lowercase()?.contains(q) == true || dev.rint.launcher.ui.I18n.t(it.title).lowercase().contains(q) || it.desc?.let { d -> dev.rint.launcher.ui.I18n.t(d).lowercase().contains(q) } == true) }.map { sec to it } }
            if (found.isEmpty()) item {
                Text("nothing called “$query” — yet.", color = look.colors.subtext, fontFamily = look.font, modifier = Modifier.padding(24.dp))
            }
            found.groupBy { it.first }.forEach { (sec, pairs) ->
                item {
                    GroupLabel(sec.title)
                    GroupCard { pairs.forEachIndexed { i, (_, o) -> if (i > 0) RowDivider(); OptionRow(o, cfg, onAction) } }
                }
            }
            return@LazyColumn
        }
        item {
            GroupCard(Modifier.padding(top = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(look.colors.accent), contentAlignment = Alignment.Center) { RinSprite(Pose.HEAD, 46.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RintOS", fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = look.colors.text)
                        Text("${Schema.optionCount} settings · every one applies live", fontFamily = look.font, fontSize = 13.sp, color = look.colors.subtext)
                    }
                }
                RowDivider()
                Box(Modifier.padding(12.dp)) { LivePreview() }
            }
        }
        item {
            GroupLabel("Presets")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Presets.all.forEach { PresetCard(it) }
            }
        }
        val groups = listOf(
            "Look" to listOf("look", "icons", "labels", "home", "dock", "clock"),
            "Features" to listOf("ai", "music", "lock", "notch", "mascot", "power", "danger"),
            "Behavior" to listOf("drawer", "search", "gestures", "motion"),
            "System" to listOf("system", "backup"),
        )
        groups.forEach { (title, ids) ->
            item {
                GroupLabel(title)
                GroupCard {
                    ids.mapNotNull { Schema.find(it) }.forEachIndexed { i, sec ->
                        if (i > 0) RowDivider(inset = 60.dp)
                        SectionRow(sec) { state.settingsSection = sec.id }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RinSprite(Pose.SLEEP, 56.dp)
                Text("RintOS 1.3", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext)
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val look = LocalRint.current
    Text(text.uppercase(), fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp, color = look.colors.subtext,
        modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val look = LocalRint.current
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(look.colors.panelStrong)) { content() }
}

@Composable
private fun RowDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    val look = LocalRint.current
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.6.dp).background(look.colors.stroke))
}

@Composable
private fun SectionRow(s: Section, onClick: () -> Unit) {
    val look = LocalRint.current
    val v = rememberHaptic()
    Row(
        Modifier.fillMaxWidth().pressable(PressEffect.NONE) { Haptics.tick(v); onClick() }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(s.tint), contentAlignment = Alignment.Center) {
            Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, fontFamily = look.font, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = look.colors.text)
            Text(s.blurb, fontFamily = look.font, fontSize = 12.sp, color = look.colors.subtext, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = look.colors.subtext.copy(alpha = 0.6f))
    }
}

@Composable
private fun SettingsSearch(q: String, onQuery: (String) -> Unit) {
    val look = LocalRint.current
    Row(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(look.colors.panel).border(1.dp, look.colors.stroke, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = look.colors.subtext, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("search settings…", color = look.colors.subtext, fontFamily = look.font, fontSize = 15.sp)
            BasicTextField(q, onQuery, singleLine = true, textStyle = TextStyle(color = look.colors.text, fontFamily = look.font, fontSize = 15.sp), cursorBrush = SolidColor(look.colors.accent), modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = look.colors.subtext, modifier = Modifier.size(18.dp).clickable { onQuery("") })
    }
}

@Composable
private fun PresetCard(p: Preset) {
    val look = LocalRint.current
    val v = rememberHaptic()
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(22.dp)).background(look.colors.panel).border(1.dp, look.colors.stroke, RoundedCornerShape(22.dp))
            .pressable(PressEffect.BOUNCE) { Haptics.confirm(v); store.update { p.apply(it) } }.padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))) {
            p.swatch.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it.color())) }
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = look.colors.text)
        Text(p.tagline, fontFamily = look.font, fontSize = 11.sp, color = look.colors.subtext, maxLines = 2, minLines = 2, lineHeight = 14.sp)
    }
}

/** A tiny live home screen that redraws with every change. */
@Composable
private fun LivePreview() {
    val look = LocalRint.current
    val cfg = look.cfg
    val apps by RintApp.instance.apps.apps.collectAsState()
    val sample = remember(apps) { apps.shuffled(java.util.Random(7)).take(8) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0B1226), look.colors.accent.copy(alpha = 0.55f), Color(0xFF05070D))))
                .border(1.dp, look.colors.stroke, RoundedCornerShape(30.dp))
        ) {
            dev.rint.launcher.home.Wallpaper()
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dev.rint.launcher.widgets.ClockFace(cfg.clock, Modifier.weight(1f).height(52.dp), compact = true)
                    if (cfg.notch.enabled) Box(Modifier.size((cfg.notch.width * 0.5f).dp, (cfg.notch.height * 0.6f).dp).clip(RoundedCornerShape(50)).background(cfg.notch.color.color()))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    sample.take(4).forEach { AppIconView(it.key, 42.dp) }
                }
                Spacer(Modifier.height(10.dp))
                if (cfg.dock.enabled) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape((cfg.dock.corner * 0.6f).dp)).background(look.colors.panel.copy(alpha = cfg.dock.opacity)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    sample.drop(4).take(cfg.dock.count.coerceAtMost(4)).forEach { AppIconView(it.key, 34.dp) }
                }
            }
            Text("LIVE", fontFamily = RintFonts.Pixel, fontSize = 8.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color.Red.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun SectionPage(s: Section, state: LauncherState, onAction: (String) -> Unit) {
    val look = LocalRint.current
    val cfg = look.cfg
    // split into iOS-style groups at each header
    val groups = remember(s) {
        val out = ArrayList<Pair<String?, List<Opt>>>()
        var title: String? = null
        var cur = ArrayList<Opt>()
        s.opts.forEach { o ->
            if (o is Opt.Header) {
                if (cur.isNotEmpty()) out += title to cur
                title = o.title; cur = ArrayList()
            } else cur += o
        }
        if (cur.isNotEmpty()) out += title to cur
        out
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp).pressable(PressEffect.SHRINK) { state.settingsSection = null }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "back", tint = look.colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(4.dp))
                Text("Settings", color = look.colors.accent, fontFamily = look.font, fontSize = 16.sp)
            }
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(s.tint), contentAlignment = Alignment.Center) {
                    Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(s.title, fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = look.colors.text)
                    Text(s.blurb, fontFamily = look.font, fontSize = 13.sp, color = look.colors.subtext)
                }
            }
            if (s.id in listOf("look", "icons", "labels", "home", "dock", "clock", "notch")) Box(Modifier.padding(top = 16.dp)) { LivePreview() }
        }
        groups.forEachIndexed { gi, (title, opts) ->
            item(key = "g$gi") {
                if (title != null) GroupLabel(title) else Spacer(Modifier.height(18.dp))
                GroupCard {
                    opts.forEachIndexed { i, o ->
                        if (i > 0) RowDivider()
                        OptionRow(o, cfg, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(o: Opt, cfg: RintConfig, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        when (o) {
            is Opt.Toggle -> ToggleRow(o, cfg)
            is Opt.Slider -> SliderRow(o, cfg)
            is Opt.Choice<*> -> ChoiceRow(o, cfg)
            is Opt.ColorPick -> ColorRow(o, cfg)
            is Opt.TextField -> TextRow(o, cfg)
            is Opt.Gesture -> GestureRow(o, cfg)
            is Opt.Action -> ActionRow(o, onAction)
            is Opt.GestureList -> GestureListRow(o, cfg)
            is Opt.Secret -> SecretRow(o)
            is Opt.Header -> Unit
        }
    }
}

@Composable
private fun Title(o: Opt, trailing: @Composable () -> Unit = {}) {
    val look = LocalRint.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(o.title, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = look.colors.text)
            o.desc?.let { Text(it, fontFamily = look.font, fontSize = 12.sp, color = look.colors.subtext, lineHeight = 15.sp) }
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(o: Opt.Toggle, cfg: RintConfig) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val on = o.get(cfg)
    val x by animateFloatAsState(if (on) 1f else 0f, RintSprings.pop(), label = "t")
    val track by animateColorAsState(if (on) look.colors.accent else look.colors.text.copy(alpha = 0.15f), label = "c")
    Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { Haptics.tick(v); store.update { o.set(it, !on) } }) {
        Title(o) {
            Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(50)).background(track).padding(4.dp)) {
                Box(Modifier.offset(x = (20 * x).dp).size(24.dp).graphicsLayer { val s = 1f + 0.15f * (1f - kotlin.math.abs(x * 2 - 1)); scaleX = s }.clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun SliderRow(o: Opt.Slider, cfg: RintConfig) {
    val look = LocalRint.current
    val v = rememberHaptic()
    var local by remember { mutableFloatStateOf(Float.NaN) }
    val value = if (local.isNaN()) o.get(cfg) else local
    Title(o) {
        Text(o.fmt(value), fontFamily = RintFonts.Terminal, fontSize = 20.sp, color = look.colors.accent)
    }
    Slider(
        value = value.coerceIn(o.range),
        onValueChange = {
            if (o.steps > 0 && o.fmt(it) != o.fmt(value)) Haptics.tick(v)
            local = it
            store.update { c -> o.set(c, it) }
        },
        onValueChangeFinished = { local = Float.NaN },
        valueRange = o.range,
        steps = o.steps,
        colors = SliderDefaults.colors(
            thumbColor = look.colors.accent, activeTrackColor = look.colors.accent, inactiveTrackColor = look.colors.text.copy(alpha = 0.12f),
            activeTickColor = look.colors.onAccent.copy(alpha = 0.4f), inactiveTickColor = look.colors.text.copy(alpha = 0.2f),
        ),
    )
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ChoiceRow(o: Opt.Choice<*>, cfg: RintConfig) {
    val oo = o as Opt.Choice<Enum<*>>
    val cur = oo.get(cfg)
    Title(o)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        oo.values.forEach { e ->
            if (e is IconShape) ShapeChip(e, e == cur) { store.update { c -> oo.set(c, e) } }
            else Pill(oo.label(e), selected = e == cur) { store.update { c -> oo.set(c, e) } }
        }
    }
}

@Composable
private fun ShapeChip(s: IconShape, selected: Boolean, onClick: () -> Unit) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, RintSprings.pop(), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable(PressEffect.BOUNCE) { Haptics.tick(v); onClick() }) {
        Box(
            Modifier.size(46.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(Shapes.icon(s)).background(if (selected) look.colors.accent else look.colors.text.copy(alpha = 0.15f))
        )
        Text(pretty(s.name), fontSize = 10.sp, color = if (selected) look.colors.accent else look.colors.subtext, fontFamily = look.font)
    }
}

private val swatches = listOf(
    0xFF2F6BFFL, 0xFF00B3FFL, 0xFF1FD1C1L, 0xFF36D399L, 0xFF39FF88L, 0xFFFFD60AL, 0xFFFF9F1CL,
    0xFFFF5A6AL, 0xFFFF6FB5L, 0xFF9B8CFFL, 0xFF7C5CFFL, 0xFFFFFFFFL, 0xFF8A94B8L, 0xFF0A0E1EL, 0xFF000000L,
)

@Composable
private fun ColorRow(o: Opt.ColorPick, cfg: RintConfig) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val cur = o.get(cfg)
    var custom by remember { mutableStateOf(false) }
    Title(o) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(cur.color()).border(2.dp, look.colors.stroke, CircleShape))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        swatches.forEach { sw ->
            val sel = sw == cur
            val s by animateFloatAsState(if (sel) 1.15f else 1f, RintSprings.pop(), label = "sw")
            Box(
                Modifier.size(34.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(sw.color())
                    .border(if (sel) 3.dp else 1.dp, if (sel) look.colors.text else look.colors.stroke, CircleShape)
                    .clickable { Haptics.tick(v); store.update { o.set(it, sw) } }
            )
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                .clickable { custom = !custom }
        )
    }
    AnimatedVisibility(custom, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val hsv = remember(cur) { FloatArray(3).also { android.graphics.Color.colorToHSV(cur.toInt(), it) } }
        val alpha = ((cur ushr 24) and 0xFF) / 255f
        Column(Modifier.padding(top = 10.dp)) {
            listOf("hue" to 0, "saturation" to 1, "brightness" to 2).forEach { (name, i) ->
                Text(name, fontSize = 11.sp, color = look.colors.subtext, fontFamily = look.font)
                Slider(
                    value = hsv[i], valueRange = if (i == 0) 0f..360f else 0f..1f,
                    onValueChange = { nv ->
                        val h = hsv.copyOf().also { it[i] = nv }
                        val col = android.graphics.Color.HSVToColor((alpha * 255).toInt(), h)
                        store.update { o.set(it, col.toLong() and 0xFFFFFFFFL) }
                    },
                    colors = SliderDefaults.colors(thumbColor = cur.color().copy(alpha = 1f), activeTrackColor = look.colors.accent, inactiveTrackColor = look.colors.text.copy(alpha = 0.12f)),
                )
            }
            Text("opacity", fontSize = 11.sp, color = look.colors.subtext, fontFamily = look.font)
            Slider(
                value = alpha, onValueChange = { a ->
                    val c = cur.color().copy(alpha = a)
                    store.update { o.set(it, c.argbLong()) }
                },
                colors = SliderDefaults.colors(thumbColor = look.colors.accent, activeTrackColor = look.colors.accent, inactiveTrackColor = look.colors.text.copy(alpha = 0.12f)),
            )
            Text("#%08X".format(cur.toInt()), fontFamily = RintFonts.Terminal, fontSize = 18.sp, color = look.colors.text)
        }
    }
}

@Composable
private fun TextRow(o: Opt.TextField, cfg: RintConfig) {
    val look = LocalRint.current
    Title(o)
    Spacer(Modifier.height(8.dp))
    var text by remember { mutableStateOf(o.get(cfg)) }
    BasicTextField(
        text,
        { text = it; store.update { c -> o.set(c, it) } },
        singleLine = true,
        textStyle = TextStyle(color = look.colors.text, fontFamily = look.font, fontSize = 15.sp),
        cursorBrush = SolidColor(look.colors.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(look.colors.text.copy(alpha = 0.06f)).padding(12.dp),
    )
}

@Composable
private fun GestureRow(o: Opt.Gesture, cfg: RintConfig) {
    val look = LocalRint.current
    val b = o.get(cfg)
    var open by remember { mutableStateOf(false) }
    val appName = b.app?.let { RintApp.instance.apps.find(it)?.label }
    Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { open = !open }) {
        Title(o) {
            Text(if (b.action == GestureAction.LAUNCH_APP) appName ?: "pick app" else pretty(b.action.name), fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = look.colors.accent)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = look.colors.subtext, modifier = Modifier.graphicsLayer { rotationZ = if (open) 90f else 0f })
        }
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.padding(top = 10.dp)) {
            val rows = GestureAction.entries.chunked(3)
            rows.forEach { row ->
                Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { a -> Pill(pretty(a.name), selected = a == b.action) { store.update { c -> o.set(c, Binding(a, b.app)) } } }
                }
            }
            if (b.action == GestureAction.LAUNCH_APP) {
                val apps by RintApp.instance.apps.apps.collectAsState()
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(apps, key = { it.key }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { store.update { c -> o.set(c, Binding(GestureAction.LAUNCH_APP, e.key)) } }
                                .background(if (e.key == b.app) look.colors.accent.copy(alpha = 0.15f) else Color.Transparent).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconView(e.key, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(e.label, color = look.colors.text, fontFamily = look.font, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureListRow(o: Opt.GestureList, cfg: RintConfig) {
    val look = LocalRint.current
    val list = o.get(cfg)
    var editing by remember { mutableStateOf(-1) }
    Title(o) { Text("${list.size}/${o.max}", fontFamily = RintFonts.Terminal, fontSize = 20.sp, color = look.colors.accent) }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        list.forEachIndexed { i, b ->
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (editing == i) look.colors.accent else look.colors.text.copy(alpha = 0.1f))
                    .clickable { editing = if (editing == i) -1 else i },
                contentAlignment = Alignment.Center,
            ) {
                when (b.action) {
                    GestureAction.ASSISTANT -> RinSprite(Pose.HEAD, 30.dp)
                    GestureAction.LAUNCH_APP -> b.app?.let { AppIconView(it, 30.dp, showBadge = false) }
                    else -> Icon(dev.rint.launcher.lock.actionIcon(b.action), null, tint = if (editing == i) look.colors.onAccent else look.colors.text)
                }
            }
        }
        if (list.size < o.max) Pill("+ add") {
            store.update { c -> o.set(c, list + Binding(GestureAction.FLASHLIGHT)) }
            editing = list.size
        }
    }
    val b = list.getOrNull(editing) ?: return
    Spacer(Modifier.height(10.dp))
    Text("SHORTCUT ${editing + 1}", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext)
    Spacer(Modifier.height(6.dp))
    fun setAt(nb: Binding?) = store.update { c ->
        val cur = o.get(c).toMutableList()
        if (editing in cur.indices) { if (nb == null) cur.removeAt(editing) else cur[editing] = nb }
        o.set(c, cur)
    }
    GestureAction.entries.filter { it != GestureAction.NONE }.chunked(3).forEach { row ->
        Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { a -> Pill(pretty(a.name), selected = a == b.action) { setAt(Binding(a, b.app)) } }
        }
    }
    if (b.action == GestureAction.LAUNCH_APP) {
        val apps by RintApp.instance.apps.apps.collectAsState()
        LazyColumn(Modifier.heightIn(max = 220.dp).padding(top = 6.dp)) {
            items(apps, key = { it.key }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { setAt(Binding(GestureAction.LAUNCH_APP, e.key)) }
                        .background(if (e.key == b.app) look.colors.accent.copy(alpha = 0.15f) else Color.Transparent).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconView(e.key, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(e.label, color = look.colors.text, fontFamily = look.font, fontSize = 14.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Pill("remove") { setAt(null); editing = -1 }
}

@Composable
private fun SecretRow(o: Opt.Secret) {
    val look = LocalRint.current
    val stores = RintApp.instance.stores
    val saved = stores.secret(o.name)
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(saved == null) }
    Title(o) {
        if (saved != null) Text("•••• " + saved.takeLast(4), fontFamily = RintFonts.Terminal, fontSize = 18.sp, color = look.colors.accent)
    }
    Spacer(Modifier.height(8.dp))
    if (editing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                text, { text = it },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                textStyle = TextStyle(color = look.colors.text, fontFamily = RintFonts.Terminal, fontSize = 18.sp),
                cursorBrush = SolidColor(look.colors.accent),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(look.colors.text.copy(alpha = 0.06f)).padding(12.dp),
            )
            Spacer(Modifier.width(8.dp))
            Pill("save", selected = true) { stores.setSecret(o.name, text); text = ""; editing = false }
        }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("change") { editing = true }
        Pill("remove") { stores.setSecret(o.name, ""); editing = true }
    }
}

@Composable
private fun ActionRow(o: Opt.Action, onAction: (String) -> Unit) {
    val look = LocalRint.current
    Box(Modifier.pressable(PressEffect.SHRINK) { onAction(o.id) }) {
        Title(o) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = look.colors.accent) }
    }
}

@Composable
private fun Sheets(sheet: String?, state: LauncherState, close: () -> Unit) {
    val ctx = LocalContext.current
    val stores = RintApp.instance.stores
    when (sheet) {
        null -> return
        "wallpaper" -> { close(); SystemActions.openWallpaperPicker(ctx); return }
        "photo" -> { close(); PhotoPickRequest.launch?.invoke(); return }
        "assistant" -> {
            close()
            val i = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }.onFailure { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            return
        }
        "saver" -> { close(); state.settingsOpen = false; dev.rint.launcher.system.BatteryWatch.saver.value = true; return }
        "live" -> {
            close()
            stores.config.update { it.copy(look = it.look.copy(wallpaper = dev.rint.launcher.core.WallpaperMode.SYSTEM)) }
            runCatching { ctx.startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        "a11y" -> { close(); SystemActions.openAccessibilitySettings(ctx); return }
        "overlay" -> {
            close()
            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        "notif" -> { close(); SystemActions.openNotificationAccess(ctx); return }
        "home" -> { close(); SystemSettingsLinks.defaultHome(ctx); return }
        "lockpreview" -> { close(); dev.rint.launcher.lock.LockActivity.show(ctx); return }
        "voicetest" -> {
            close()
            RintApp.instance.assistant.speak(dev.rint.launcher.ui.I18n.t("hi! I'm Rin. this is my voice. pretty cool, right?"))
            return
        }
        "guide" -> { close(); state.settingsOpen = false; state.guideStep = 0; return }
        "guitar" -> { close(); state.settingsOpen = false; dev.rint.launcher.mascot.GuitarShow.open = true; return }
        in dev.rint.launcher.mascot.Joke.entries.map { "joke:" + it.name } -> {
            close(); state.settingsOpen = false
            dev.rint.launcher.mascot.Chaos.joke = dev.rint.launcher.mascot.Joke.valueOf(sheet.removePrefix("joke:"))
            return
        }
        "intro" -> { close(); state.settingsOpen = false; stores.config.update { it.copy(onboarded = false) }; return }
        "resetlook" -> { close(); stores.config.update { Presets.all.first().apply(it) }; state.say("fresh look applied"); return }
        "export" -> {
            close()
            val bundle = buildJsonObject {
                put("config", RintJson.parseToJsonElement(stores.config.export()))
                put("layout", RintJson.parseToJsonElement(stores.layout.export()))
            }.toString()
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, bundle).putExtra(Intent.EXTRA_SUBJECT, "My RintOS setup")
            runCatching { ctx.startActivity(Intent.createChooser(i, "Export RintOS setup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
    }
    SheetFrame(close) {
        when (sheet) {
            "iconpack" -> IconPackSheet(close)
            "hidden" -> HiddenAppsSheet()
            "musicapp" -> MusicAppSheet(close)
            "import" -> ImportSheet(state, close)
            "resetall" -> ConfirmReset(state, close)
        }
    }
}

@Composable
private fun SheetFrame(close: () -> Unit, content: @Composable () -> Unit) {
    val look = LocalRint.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(remember { MutableInteractionSource() }, null, onClick = close), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(look.colors.panelStrong)
                .clickable(remember { MutableInteractionSource() }, null) {}.navigationBarsPadding().imePadding().padding(20.dp)
        ) { content() }
    }
}

@Composable
private fun SheetTitle(t: String) {
    val look = LocalRint.current
    Text(t, fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = look.colors.text, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun IconPackSheet(close: () -> Unit) {
    val look = LocalRint.current
    val ctx = LocalContext.current
    val packs = remember { IconPack.installed(ctx) }
    val cur = look.cfg.icons.iconPack
    SheetTitle("Icon pack")
    LazyColumn {
        item {
            SheetRow("None — use RintOS styles", cur == null) {
                store.update { it.copy(icons = it.icons.copy(iconPack = null)) }; RintApp.instance.apps.setIconPack(null); close()
            }
        }
        items(packs) { p ->
            SheetRow(p.label, cur == p.packageName) {
                store.update { it.copy(icons = it.icons.copy(iconPack = p.packageName)) }; RintApp.instance.apps.setIconPack(p.packageName); close()
            }
        }
        if (packs.isEmpty()) item { Text("No icon packs installed. Grab any ADW/Nova-compatible pack from the Play Store.", color = look.colors.subtext, fontFamily = look.font, modifier = Modifier.padding(8.dp)) }
    }
}

@Composable
private fun SheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalRint.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) look.colors.accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = look.colors.text, fontFamily = look.font, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(look.colors.accent))
    }
}

@Composable
private fun HiddenAppsSheet() {
    val look = LocalRint.current
    val hidden = look.cfg.hiddenApps.toList()
    SheetTitle("Hidden apps")
    if (hidden.isEmpty()) Text("Nothing hidden. Long-press any app → Hide from drawer.", color = look.colors.subtext, fontFamily = look.font)
    LazyColumn {
        items(hidden) { k ->
            val e = RintApp.instance.apps.find(k)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconView(k, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(e?.label ?: k, color = look.colors.text, fontFamily = look.font, modifier = Modifier.weight(1f))
                Pill("unhide") { store.update { it.copy(hiddenApps = it.hiddenApps - k) } }
            }
        }
    }
}

@Composable
private fun MusicAppSheet(close: () -> Unit) {
    val look = LocalRint.current
    val apps = rememberMusicApps()
    SheetTitle("Play songs with")
    Text("RintOS asks this app to play what you search, then follows along with live lyrics.", color = look.colors.subtext, fontFamily = look.font, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    LazyColumn {
        items(apps) { a ->
            SheetRow(a.label, look.cfg.music.preferredApp == a.pkg) {
                store.update { it.copy(music = it.music.copy(preferredApp = a.pkg)) }; close()
            }
        }
        if (apps.isEmpty()) item { Text("No compatible music apps found. Spotify, YouTube Music, Deezer and most players work.", color = look.colors.subtext, fontFamily = look.font) }
    }
}

@Composable
private fun ImportSheet(state: LauncherState, close: () -> Unit) {
    val look = LocalRint.current
    val stores = RintApp.instance.stores
    var text by remember { mutableStateOf("") }
    SheetTitle("Import setup")
    BasicTextField(
        text, { text = it },
        textStyle = TextStyle(color = look.colors.text, fontFamily = RintFonts.Terminal, fontSize = 14.sp),
        cursorBrush = SolidColor(look.colors.accent),
        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(look.colors.text.copy(alpha = 0.06f)).padding(12.dp),
    )
    Spacer(Modifier.height(12.dp))
    Pill("import", selected = true) {
        val ok = runCatching {
            val o = RintJson.parseToJsonElement(text).jsonObject
            val c = (o["config"] as? JsonObject)?.toString()
            val l = (o["layout"] as? JsonObject)?.toString()
            (c != null && stores.config.import(c)) and (l == null || stores.layout.import(l))
        }.getOrDefault(false)
        state.say(if (ok) "setup imported ✓" else "that doesn't look like a RintOS setup")
        if (ok) close()
    }
}

@Composable
private fun ConfirmReset(state: LauncherState, close: () -> Unit) {
    val look = LocalRint.current
    val stores = RintApp.instance.stores
    SheetTitle("Reset everything?")
    Text("Your layout, widgets and every setting go back to day one. Rin will forget you (he'll get over it).", color = look.colors.subtext, fontFamily = look.font)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("cancel") { close() }
        Pill("reset", selected = true) {
            stores.layout.replace(dev.rint.launcher.core.HomeLayout())
            stores.widgets.replace(emptyMap())
            stores.config.replace(RintConfig(lang = stores.config.value.lang, onboarded = true, guideSeen = true))
            dev.rint.launcher.home.seedLayoutIfNeeded()
            state.settingsOpen = false
            close()
        }
    }
}


/** Lets settings rows trigger the photo picker registered by [SettingsScreen]. */
object PhotoPickRequest { var launch: (() -> Unit)? = null }
