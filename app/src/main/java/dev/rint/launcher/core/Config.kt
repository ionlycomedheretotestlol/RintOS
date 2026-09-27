package dev.rint.launcher.core

import kotlinx.serialization.Serializable

const val RINT_BLUE = 0xFF3B7CFFL
const val RINT_INK = 0xFF0A0E1EL

@Serializable
data class RintConfig(
    val onboarded: Boolean = false,
    val guideSeen: Boolean = false,
    val look: Look = Look(),
    val home: Home = Home(),
    val icons: Icons = Icons(),
    val labels: Labels = Labels(),
    val dock: Dock = Dock(),
    val drawer: Drawer = Drawer(),
    val search: SearchCfg = SearchCfg(),
    val gestures: Gestures = Gestures(),
    val notch: Notch = Notch(),
    val clock: ClockCfg = ClockCfg(),
    val mascot: MascotCfg = MascotCfg(),
    val motion: Motion = Motion(),
    val music: MusicCfg = MusicCfg(),
    val lock: LockCfg = LockCfg(),
    val ai: AiCfg = AiCfg(),
    val battery: BatteryCfg = BatteryCfg(),
    val hiddenApps: Set<String> = emptySet(),
    val renamedApps: Map<String, String> = emptyMap(),
)

enum class ThemeMode { AUTO, LIGHT, DARK, AMOLED }
enum class UiFont { INTER, SYSTEM, PIXEL, TERMINAL, SERIF, MONO }
enum class WallpaperMode { ART, SYSTEM, SOLID, GRADIENT, MESH, PHOTO }
enum class WallpaperArt { TIDE, PIXEL_NIGHT, PAPER }

@Serializable
data class Look(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Long = RINT_BLUE,
    val font: UiFont = UiFont.INTER,
    val corner: Float = 24f,
    val panelOpacity: Float = 0.6f,
    val blur: Float = 28f,
    val wallpaper: WallpaperMode = WallpaperMode.ART,
    val art: WallpaperArt = WallpaperArt.TIDE,
    val solidColor: Long = RINT_INK,
    val gradientA: Long = 0xFF0C1A5CL,
    val gradientB: Long = 0xFF7B3CFFL,
    val gradientC: Long = 0xFF00C8FFL,
    val gradientAngle: Float = 135f,
    val wallpaperDim: Float = 0f,
    val showStatusBar: Boolean = true,
    val showNavBar: Boolean = true,
    val textShadow: Boolean = true,
    val grain: Float = 0f,
    val photoVersion: Long = 0,
    val customColors: Boolean = false,
    val bgColor: Long = 0xFF08090EL,
    val panelColor: Long = 0xFF1B1E29L,
    val textColor: Long = 0xFFF4F6FFL,
    val subtextColor: Long = 0xFF9A9FAEL,
    val secondAccent: Long = 0xFFFF6FB5L,
)

enum class PageTransition { SLIDE, CUBE, STACK, ZOOM, FLIP, FADE, CAROUSEL, TILT }
enum class PageIndicator { DOTS, LINE, NUMBERS, PAW, NONE }
enum class SearchBarPos { TOP, BOTTOM, HIDDEN }
enum class SearchBarStyle { COMPACT, PILL, GLASS, UNDERLINE, TERMINAL }

@Serializable
data class Home(
    val columns: Int = 4,
    val rows: Int = 6,
    val transition: PageTransition = PageTransition.SLIDE,
    val indicator: PageIndicator = PageIndicator.DOTS,
    val searchBar: SearchBarPos = SearchBarPos.BOTTOM,
    val searchStyle: SearchBarStyle = SearchBarStyle.COMPACT,
    val searchHint: String = "Search",
    val sideMargin: Float = 18f,
    val topMargin: Float = 8f,
    val lockLayout: Boolean = false,
    val infinitePages: Boolean = false,
    val showMascotHint: Boolean = true,
)

enum class IconShape { SQUIRCLE, CIRCLE, ROUNDED, SQUARE, TEARDROP, HEXAGON, PEBBLE, CLOVER, DIAMOND, SYSTEM }
enum class IconStyle { RINT, ORIGINAL, GRAYSCALE, TINTED, OUTLINE }
enum class MonoBackground { WHITE, BLACK, ACCENT, GLASS, ALTERNATE, NONE }
enum class PressEffect { SHRINK, BOUNCE, GLOW, WOBBLE, NONE }

@Serializable
data class Icons(
    val shape: IconShape = IconShape.SQUIRCLE,
    val style: IconStyle = IconStyle.ORIGINAL,
    val monoBg: MonoBackground = MonoBackground.WHITE,
    val monoFg: Long = RINT_BLUE,
    val size: Float = 56f,
    val padding: Float = 0f,
    val shadow: Boolean = true,
    val badges: Boolean = true,
    val badgeColor: Long = RINT_BLUE,
    val press: PressEffect = PressEffect.BOUNCE,
    val iconPack: String? = null,
    val shapePackIcons: Boolean = false,
    val outlineWidth: Float = 0f,
    val outlineColor: Long = 0x33FFFFFFL,
)

enum class LabelColor { AUTO, WHITE, BLACK, ACCENT }

@Serializable
data class Labels(
    val show: Boolean = true,
    val size: Float = 11.5f,
    val color: LabelColor = LabelColor.AUTO,
    val maxLines: Int = 1,
    val uppercase: Boolean = false,
    val bold: Boolean = false,
)

enum class DockStyle { GLASS, SOLID, FLOATING, LINE, NONE }

@Serializable
data class Dock(
    val enabled: Boolean = true,
    val count: Int = 4,
    val style: DockStyle = DockStyle.GLASS,
    val height: Float = 84f,
    val corner: Float = 30f,
    val margin: Float = 14f,
    val opacity: Float = 0.55f,
    val labels: Boolean = false,
    val magnify: Boolean = true,
    val iconScale: Float = 1f,
)

enum class DrawerStyle { GRID, LIST, ALPHABET, PAGED }
enum class DrawerSort { ALPHA, INSTALL_DATE, COLOR, MOST_USED }

@Serializable
data class Drawer(
    val style: DrawerStyle = DrawerStyle.GRID,
    val columns: Int = 4,
    val sort: DrawerSort = DrawerSort.ALPHA,
    val opacity: Float = 0.82f,
    val autoKeyboard: Boolean = false,
    val showRecents: Boolean = true,
    val headers: Boolean = false,
    val scrollBar: Boolean = true,
)

enum class SearchEngine { GOOGLE, DUCKDUCKGO, BRAVE, BING, STARTPAGE, CUSTOM }

@Serializable
data class SearchCfg(
    val engine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val customUrl: String = "https://www.google.com/search?q=%s",
    val calculator: Boolean = true,
    val contacts: Boolean = true,
    val settings: Boolean = true,
    val webFallback: Boolean = true,
    val launchOnEnter: Boolean = true,
    val fuzzy: Boolean = true,
    val unitConverter: Boolean = true,
)

enum class GestureAction {
    NONE, DRAWER, NOTIFICATIONS, QUICK_SETTINGS, SEARCH, LOCK, RECENTS,
    SETTINGS, FLASHLIGHT, MUSIC, NOTCH, WIDGETS, FIRST_PAGE, LAUNCH_APP, MASCOT, CAMERA, ASSISTANT
}

@Serializable
data class Binding(val action: GestureAction, val app: String? = null)

@Serializable
data class Gestures(
    val swipeUp: Binding = Binding(GestureAction.DRAWER),
    val swipeDown: Binding = Binding(GestureAction.NOTIFICATIONS),
    val doubleTap: Binding = Binding(GestureAction.LOCK),
    val twoFingerDown: Binding = Binding(GestureAction.QUICK_SETTINGS),
    val homePress: Binding = Binding(GestureAction.FIRST_PAGE),
    val dockSwipeUp: Binding = Binding(GestureAction.SEARCH),
    val notchLongPress: Binding = Binding(GestureAction.ASSISTANT),
    val haptics: Boolean = true,
)

enum class NotchShape { PILL, ISLAND, TEARDROP, WIDE, DOT, TAB }
enum class NotchContent { TIME, BATTERY, NOW_PLAYING, MASCOT, NOTIFICATIONS, DATE }

@Serializable
data class Notch(
    val enabled: Boolean = true,
    val shape: NotchShape = NotchShape.ISLAND,
    val width: Float = 128f,
    val height: Float = 34f,
    val offsetY: Float = 10f,
    val color: Long = 0xFF000000L,
    val glow: Boolean = true,
    val left: NotchContent = NotchContent.TIME,
    val right: NotchContent = NotchContent.BATTERY,
    val liveActivity: Boolean = true,
    val expandOnTap: Boolean = true,
)

enum class ClockStyle { BLOCKS, THIN, STACKED, WORDS, ANALOG, PIXEL, NONE }
enum class Align { START, CENTER, END }

@Serializable
data class ClockCfg(
    val style: ClockStyle = ClockStyle.BLOCKS,
    val use24h: Boolean = true,
    val seconds: Boolean = false,
    val showDate: Boolean = true,
    val dateFormat: String = "EEEE, d MMMM",
    val useAccent: Boolean = false,
    val size: Float = 1f,
    val align: Align = Align.CENTER,
    val blinkColon: Boolean = true,
    val glow: Boolean = false,
    val greeting: Boolean = false,
)

enum class MascotPresence { SHY, NORMAL, CLINGY }
enum class MascotStyle { SMOOTH, PIXEL }

@Serializable
data class MascotCfg(
    val enabled: Boolean = true,
    val name: String = "Rin",
    val presence: MascotPresence = MascotPresence.NORMAL,
    val size: Float = 1f,
    val sleepsAtNight: Boolean = false,
    val reactsToCharging: Boolean = false,
    val inMusic: Boolean = true,
    val wanders: Boolean = false,
    val style: MascotStyle = MascotStyle.SMOOTH,
    val color: Long? = null,
    val greets: Boolean = false,
)

enum class OpenAnim { SYSTEM, SCALE_UP, CLIP_REVEAL, NONE }

@Serializable
data class Motion(
    val speed: Float = 1f,
    val bounce: Float = 0.62f,
    val openAnim: OpenAnim = OpenAnim.SCALE_UP,
    val reduce: Boolean = false,
    val parallax: Boolean = true,
)

enum class LyricsFont { TERMINAL, PIXEL, INTER }
enum class PlayVia { ASK, APP, LOCAL, STREAM }

@Serializable
data class MusicCfg(
    val lyricsFont: LyricsFont = LyricsFont.TERMINAL,
    val lyricsSize: Float = 34f,
    val bgBlur: Float = 36f,
    val bgDim: Float = 0.45f,
    val align: Align = Align.START,
    val upcoming: Int = 2,
    val highlight: Long = 0xFFFFFFFFL,
    val playVia: PlayVia = PlayVia.STREAM,
    val lyricsOnWidget: Boolean = true,
    val preferredApp: String? = null,
    val offsetMs: Long = 0,
    val kenBurns: Boolean = true,
)

enum class LockStyle { CLASSIC, BLOCKS, STACKED, WORDS, ANALOG, TERMINAL, MINIMAL, POSTER, MUSIC, RIN }
enum class UnlockAnim { SLIDE_UP, FADE, ZOOM, SPLIT, PIXELS }

@Serializable
data class LockCfg(
    val enabled: Boolean = false,
    val style: LockStyle = LockStyle.CLASSIC,
    val shortcuts: List<Binding> = listOf(Binding(GestureAction.FLASHLIGHT), Binding(GestureAction.CAMERA)),
    val notifications: Boolean = true,
    val music: Boolean = true,
    val rin: Boolean = true,
    val battery: Boolean = true,
    val unlockAnim: UnlockAnim = UnlockAnim.SLIDE_UP,
    val dim: Float = 0.25f,
    val message: String = "",
    val accentClock: Boolean = false,
)

enum class AiProvider { GEMINI, GROQ, CLAUDE, OPENROUTER }
enum class VoiceEngine { GEMINI, ANDROID }

@Serializable
data class AiCfg(
    val provider: AiProvider = AiProvider.GEMINI,
    val models: Map<String, String> = emptyMap(),
    val voice: Boolean = true,
    val voiceEngine: VoiceEngine = VoiceEngine.GEMINI,
    val voiceName: String = "Kore",
    val ttsModel: String = "gemini-2.5-flash-preview-tts",
    val handsFree: Boolean = false,
    val automation: Boolean = true,
    val confirmRisky: Boolean = true,
    val maxSteps: Int = 25,
    val personality: String = "",
) {
    fun model(p: AiProvider = provider): String = models[p.name]?.takeIf { it.isNotBlank() } ?: defaultModel(p)

    companion object {
        fun defaultModel(p: AiProvider) = when (p) {
            AiProvider.GEMINI -> "gemini-2.5-flash"
            AiProvider.GROQ -> "meta-llama/llama-4-scout-17b-16e-instruct"
            AiProvider.CLAUDE -> "claude-opus-5"
            AiProvider.OPENROUTER -> "google/gemini-2.5-flash"
        }
    }
}

@Serializable
data class BatteryCfg(
    val saver: Boolean = true,
    val saverAt: Int = 15,
    val alerts: Boolean = true,
    val emergencyAlerts: Boolean = true,
)
