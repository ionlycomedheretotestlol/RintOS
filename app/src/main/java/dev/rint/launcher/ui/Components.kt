package dev.rint.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.apps.AppEntry
import dev.rint.launcher.apps.AppIcon
import dev.rint.launcher.core.IconShape
import dev.rint.launcher.core.IconStyle
import dev.rint.launcher.core.Icons
import dev.rint.launcher.core.LabelColor
import dev.rint.launcher.core.MonoBackground
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.system.Badges
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val look = LocalRint.current
    val s = shape ?: RoundedCornerShape(look.cfg.look.corner.dp)
    Box(
        if (strong) modifier.clip(s).background(look.colors.panelStrong).border(0.8.dp, look.colors.stroke, s)
        else modifier.glass(s, tint = look.cfg.look.panelOpacity / 0.6f),
        content = content,
    )
}

@Composable
fun Modifier.pressable(effect: PressEffect, onTap: (() -> Unit)? = null): Modifier {
    val scale = remember { Animatable(1f) }
    val rot = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(effect, onTap) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                scope.launch {
                    when (effect) {
                        PressEffect.SHRINK, PressEffect.BOUNCE -> scale.animateTo(0.86f, tween(90))
                        PressEffect.GLOW -> glow.animateTo(1f, tween(120))
                        PressEffect.WOBBLE -> rot.animateTo(-6f, tween(70))
                        PressEffect.NONE -> Unit
                    }
                }
                val up = waitForUpOrCancellation()
                scope.launch {
                    val bouncy = spring<Float>(dampingRatio = if (effect == PressEffect.BOUNCE) 0.32f else 1f, stiffness = Spring.StiffnessMediumLow)
                    launch { scale.animateTo(1f, bouncy) }
                    launch { glow.animateTo(0f, tween(260)) }
                    launch { rot.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessMedium)) }
                }
                if (up != null) onTap?.invoke()
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = rot.value
        }
        .drawBehind {
            if (glow.value > 0f) drawCircle(Color.White.copy(alpha = 0.25f * glow.value), radius = size.minDimension * 0.62f)
        }
}

/** Duotone: dark pixels become [fg], light pixels become [bg]. */
private fun duotone(fg: Color, bg: Color): ColorMatrix {
    val dr = (fg.red - bg.red)
    val dg = (fg.green - bg.green)
    val db = (fg.blue - bg.blue)
    return ColorMatrix(
        floatArrayOf(
            -dr * 0.299f, -dr * 0.587f, -dr * 0.114f, 0f, fg.red * 255f,
            -dg * 0.299f, -dg * 0.587f, -dg * 0.114f, 0f, fg.green * 255f,
            -db * 0.299f, -db * 0.587f, -db * 0.114f, 0f, fg.blue * 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}

private val grayscale = ColorMatrix().apply { setToSaturation(0f) }

@Composable
fun rememberAppIcon(key: String): AppIcon? {
    val repo = RintApp.instance.apps
    val version by repo.iconVersion.collectAsState()
    val icon by produceState(repo.cachedIcon(key), key, version) { value = repo.icon(key) }
    return icon
}

data class IconPaint(val bg: Color, val fg: Color)

fun iconPaint(icons: Icons, key: String, colors: RintColors): IconPaint {
    val fg = icons.monoFg.color()
    return when (icons.monoBg) {
        MonoBackground.WHITE -> IconPaint(Color.White, fg)
        MonoBackground.BLACK -> IconPaint(Color(0xFF0A0E1E), fg)
        MonoBackground.ACCENT -> IconPaint(colors.accent, colors.onAccent)
        MonoBackground.GLASS -> IconPaint(Color.White.copy(alpha = 0.16f), Color.White)
        MonoBackground.ALTERNATE -> if (abs(key.hashCode()) % 3 == 0) IconPaint(Color(0xFF0A0E1E), fg) else IconPaint(Color.White, fg)
        MonoBackground.NONE -> IconPaint(Color.Transparent, fg)
    }
}

@Composable
fun AppIconView(
    key: String,
    size: Dp,
    modifier: Modifier = Modifier,
    icons: Icons = LocalRint.current.cfg.icons,
    showBadge: Boolean = true,
) {
    val look = LocalRint.current
    val icon = rememberAppIcon(key)
    val badges by Badges.counts.collectAsState()
    val pkg = key.substringBefore('/')
    val shape = Shapes.icon(icons.shape)
    val paint = iconPaint(icons, key, look.colors)
    Box(modifier.size(size)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding((icons.padding).dp)
                .then(
                    if (icons.shadow && icon != null && icons.style != IconStyle.OUTLINE)
                        Modifier.shadow(6.dp, shape, clip = false, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
                    else Modifier
                )
        ) {
            if (icon == null) {
                drawShape(shape, Color.White.copy(alpha = 0.08f))
                return@Canvas
            }
            drawAppIcon(icon, icons, shape, paint, look.colors)
        }
        if (showBadge && icons.badges && (badges[pkg] ?: 0) > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(2.dp, (-2).dp)
                    .size(size * 0.26f)
                    .background(icons.badgeColor.color(), CircleShape)
                    .border(2.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

private fun DrawScope.drawShape(shape: Shape, color: Color) {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color)
}

private fun DrawScope.drawShapeClipped(shape: Shape, block: DrawScope.() -> Unit) {
    val path = androidx.compose.ui.graphics.Path()
    when (val o = shape.createOutline(size, layoutDirection, this)) {
        is androidx.compose.ui.graphics.Outline.Generic -> path.addPath(o.path)
        is androidx.compose.ui.graphics.Outline.Rounded -> path.addRoundRect(o.roundRect)
        is androidx.compose.ui.graphics.Outline.Rectangle -> path.addRect(o.rect)
    }
    clipPath(path) { block() }
}

fun DrawScope.drawAppIcon(icon: AppIcon, icons: Icons, shape: Shape, paint: IconPaint, colors: RintColors) {
    val s = size.minDimension
    val px = s.toInt()
    fun layer(img: androidx.compose.ui.graphics.ImageBitmap, filter: ColorFilter? = null) {
        val big = (px * 1.5f).toInt()
        val off = ((px - big) / 2)
        drawImage(img, dstOffset = IntOffset(off, off), dstSize = IntSize(big, big), colorFilter = filter)
    }
    fun full(filter: ColorFilter? = null, scale: Float = 1f) {
        val d = (px * scale).toInt()
        val off = (px - d) / 2
        drawImage(icon.full, dstOffset = IntOffset(off, off), dstSize = IntSize(d, d), colorFilter = filter)
    }
    if (icon.fromPack && !icons.shapePackIcons) { full(); return }
    if (icons.shape == IconShape.SYSTEM && icons.style == IconStyle.ORIGINAL) { full(); return }

    when (icons.style) {
        IconStyle.ORIGINAL, IconStyle.GRAYSCALE -> {
            val f = if (icons.style == IconStyle.GRAYSCALE) ColorFilter.colorMatrix(grayscale) else null
            drawShapeClipped(shape) {
                if (icon.background != null || icon.foreground != null) {
                    icon.background?.let { layer(it, f) }
                    icon.foreground?.let { layer(it, f) }
                } else {
                    drawRect(Color.White)
                    full(f, 0.78f)
                }
            }
        }
        IconStyle.RINT, IconStyle.TINTED -> {
            val p = if (icons.style == IconStyle.TINTED)
                IconPaint(colors.accent.copy(alpha = 1f).compositeDark(), colors.accent.lighten())
            else paint
            drawShapeClipped(shape) {
                drawRect(p.bg)
                val mono = icon.mono
                when {
                    mono != null -> layer(mono, ColorFilter.tint(p.fg, BlendMode.SrcIn))
                    icon.foreground != null && icon.fgCoverage < 0.62f ->
                        layer(icon.foreground, ColorFilter.tint(p.fg, BlendMode.SrcIn))
                    else -> full(ColorFilter.colorMatrix(duotone(p.fg, if (p.bg.alpha < 0.5f) Color.White else p.bg)), 0.8f)
                }
            }
        }
        IconStyle.OUTLINE -> {
            val stroke = 2.2f * density
            val outline = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
            drawContext.canvas.save()
            drawContext.canvas.translate(stroke / 2, stroke / 2)
            drawOutline(outline, paint.fg, style = Stroke(stroke))
            drawContext.canvas.restore()
            val m = icon.mono ?: icon.foreground
            if (m != null) {
                val d = (px * 1.2f).toInt()
                val off = (px - d) / 2
                drawImage(m, dstOffset = IntOffset(off, off), dstSize = IntSize(d, d), colorFilter = ColorFilter.tint(paint.fg, BlendMode.SrcIn))
            } else full(ColorFilter.colorMatrix(duotone(paint.fg, Color.Transparent)), 0.6f)
        }
    }
    if (icons.outlineWidth > 0f) {
        val w = icons.outlineWidth * density
        val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
        drawContext.canvas.save()
        drawContext.canvas.translate(w / 2, w / 2)
        drawOutline(outline, icons.outlineColor.color(), style = Stroke(w))
        drawContext.canvas.restore()
    }
}

fun Color.compositeDark(): Color = Color(red * 0.22f, green * 0.22f, blue * 0.26f, 1f)
fun Color.lighten(): Color = Color(red + (1 - red) * 0.55f, green + (1 - green) * 0.55f, blue + (1 - blue) * 0.55f, 1f)

@Composable
fun labelColor(onDarkWallpaper: Boolean = true): Color {
    val look = LocalRint.current
    return when (look.cfg.labels.color) {
        LabelColor.AUTO -> if (onDarkWallpaper) Color.White else Color(0xFF0B0F1C)
        LabelColor.WHITE -> Color.White
        LabelColor.BLACK -> Color(0xFF0B0F1C)
        LabelColor.ACCENT -> look.colors.accent
    }
}

@Composable
fun AppTile(
    entry: AppEntry,
    modifier: Modifier = Modifier,
    iconSize: Dp = LocalRint.current.cfg.icons.size.dp,
    showLabel: Boolean = LocalRint.current.cfg.labels.show,
    labelOnDark: Boolean = true,
) {
    val look = LocalRint.current
    val lbl = look.cfg.labels
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppIconView(entry.key, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            val c = labelColor(labelOnDark)
            Text(
                text = if (lbl.uppercase) entry.label.uppercase() else entry.label,
                style = TextStyle(
                    fontFamily = look.font,
                    fontSize = lbl.size.sp,
                    fontWeight = if (lbl.bold) FontWeight.SemiBold else FontWeight.Medium,
                    color = c,
                    textAlign = TextAlign.Center,
                    shadow = if (look.cfg.look.textShadow && c.luminance() > 0.5f) Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 6f) else null,
                ),
                maxLines = lbl.maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(iconSize + 22.dp),
            )
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val bg = if (selected) look.colors.accent else look.colors.text.copy(alpha = 0.07f)
    val fg = if (selected) look.colors.onAccent else look.colors.text
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .pressable(PressEffect.SHRINK) { Haptics.tick(v); onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = look.font)
    }
}

@Composable
fun AnimatedEntrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current || RintSprings.reduce) {
        Box(modifier) { content() }
        return
    }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(18L * index.coerceAtMost(20))
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Box(modifier.graphicsLayer {
        alpha = a.value
        translationY = (1 - a.value) * 40f
    }) { content() }
}
