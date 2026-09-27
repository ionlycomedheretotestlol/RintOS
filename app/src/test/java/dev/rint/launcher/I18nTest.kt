package dev.rint.launcher

import dev.rint.launcher.settings.Opt
import dev.rint.launcher.settings.Schema
import dev.rint.launcher.settings.pretty
import dev.rint.launcher.ui.I18n
import dev.rint.launcher.ui.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class I18nTest {
    @Before fun pt() { I18n.lang = Lang.PT }
    @After fun en() { I18n.lang = Lang.EN }

    @Test fun exactAndCase() {
        assertEquals("Configurações", I18n.t("Settings"))
        assertEquals("RECENTES", I18n.t("RECENT"))
        assertEquals("COMEÇAR", I18n.t("START"))
        assertEquals("pronto — mais alguma coisa?", I18n.t("done — anything else?"))
    }

    @Test fun patterns() {
        assertEquals("bateria em 5%", I18n.t("battery's at 5%"))
        assertEquals("cada forma.", I18n.t("every shape."))
        assertEquals("rolando pra baixo", I18n.t("scrolling down"))
        assertEquals("tocar “Home”", I18n.t("play “Home”"))
        assertEquals("  [ ok ] 250 configurações", I18n.t("  [ ok ] 250 settings"))
    }

    @Test fun englishIsUntouched() {
        I18n.lang = Lang.EN
        assertEquals("Settings", I18n.t("Settings"))
    }

    @Test fun everySettingIsTranslated() {
        val missing = Schema.sections.flatMap { s ->
            listOf(s.title, s.blurb) + s.opts.flatMap { o ->
                listOfNotNull(o.title, o.desc) + ((o as? Opt.Choice<*>)?.values?.map { pretty(it.name) } ?: emptyList())
            }
        }.distinct().filter { I18n.t(it) == it && it.any(Char::isLetter) }
        val allowed = setOf("Rin", "Dock", "Notch", "Layout", "Widget", "Presets", "Rint Music", "auto", "inter", "pixel", "terminal", "mono",
            "zoom", "squircle", "rint", "original", "normal", "google", "duckduckgo", "brave", "bing", "startpage", "gemini", "groq", "claude",
            "openrouter", "android", "rin", "ask", "app", "local", "stream", "amoled", "musica", "Kore, Puck, Leda, Zephyr, Aoede, Charon…", "pixels",
            "notch", "widgets", "“5 km to mi”, “70 f to c”", "en", "pt", "console.groq.com", "console.anthropic.com", "openrouter.ai/keys")
        val real = missing.filter { it !in allowed }
        assertTrue("untranslated: $real", real.isEmpty())
    }
}
