package dev.rint.launcher.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

private val json = Json { ignoreUnknownKeys = true }

private fun describe(code: Int): Pair<String, ImageVector> = when (code) {
    0 -> "clear" to Icons.Rounded.WbSunny
    1, 2 -> "partly cloudy" to Icons.Rounded.WbCloudy
    3 -> "overcast" to Icons.Rounded.Cloud
    45, 48 -> "fog" to Icons.Rounded.Dehaze
    in 51..67, in 80..82 -> "rain" to Icons.Rounded.Grain
    in 71..77, 85, 86 -> "snow" to Icons.Rounded.AcUnit
    in 95..99 -> "storm" to Icons.Rounded.Thunderstorm
    else -> "—" to Icons.Rounded.Cloud
}

private suspend fun fetchWeather(city: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val geo = json.parseToJsonElement(
            URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name=${URLEncoder.encode(city, "UTF-8")}").readText()
        ).jsonObject["results"]!!.jsonArray[0].jsonObject
        val lat = geo["latitude"]!!.jsonPrimitive.double
        val lon = geo["longitude"]!!.jsonPrimitive.double
        val name = geo["name"]!!.jsonPrimitive.content
        val w = json.parseToJsonElement(
            URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,apparent_temperature&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1").readText()
        ).jsonObject
        val cur = w["current"]!!.jsonObject
        val daily = w["daily"]!!.jsonObject
        listOf(
            name,
            cur["temperature_2m"]!!.jsonPrimitive.double.roundToInt(),
            cur["weather_code"]!!.jsonPrimitive.int,
            cur["apparent_temperature"]!!.jsonPrimitive.double.roundToInt(),
            daily["temperature_2m_max"]!!.jsonArray[0].jsonPrimitive.double.roundToInt(),
            daily["temperature_2m_min"]!!.jsonArray[0].jsonPrimitive.double.roundToInt(),
        ).joinToString("|")
    }.getOrNull()
}

@Composable
fun WeatherWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val city = ctx.state("city", "")
    val data = ctx.state("data", "")
    val at = ctx.state("at", "0").toLongOrNull() ?: 0
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(city) }

    LaunchedEffect(city) {
        if (city.isNotBlank() && System.currentTimeMillis() - at > 30 * 60_000) {
            fetchWeather(city)?.let { ctx.set("data", it); ctx.set("at", System.currentTimeMillis().toString()) }
        }
    }

    Box(Modifier.fillMaxSize().padding(14.dp)) {
        if (city.isBlank() || editing) {
            Column(Modifier.fillMaxSize()) {
                Text("WEATHER", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(look.colors.text.copy(alpha = 0.07f)).padding(10.dp)) {
                    if (draft.isEmpty()) Text("type your city…", color = look.colors.subtext, fontSize = 14.sp, fontFamily = look.font)
                    BasicTextField(
                        draft, { draft = it }, singleLine = true,
                        textStyle = TextStyle(color = look.colors.text, fontSize = 14.sp, fontFamily = look.font),
                        cursorBrush = SolidColor(look.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (draft.isNotBlank()) { ctx.set("city", draft.trim()); ctx.set("at", "0"); editing = false }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            return@Box
        }
        val parts = data.split("|")
        if (parts.size < 6) {
            Text("fetching sky…", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = look.colors.subtext, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val (name, temp, code, feels, hi, lo) = parts
        val (desc, icon) = describe(code.toIntOrNull() ?: -1)
        Row(Modifier.fillMaxSize().clickable { editing = true; draft = city }, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = look.colors.accent, modifier = Modifier.size(if (ctx.h >= 2) 48.dp else 28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$temp°", fontFamily = RintFonts.Terminal, fontSize = if (ctx.h >= 2) 46.sp else 30.sp, color = look.colors.text)
                if (ctx.h >= 2) Text(desc, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = look.colors.text)
            }
            if (ctx.w >= 3) Column(horizontalAlignment = Alignment.End) {
                Text(name, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = look.colors.text)
                Text("H $hi°  L $lo°", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = look.colors.subtext)
                if (ctx.h >= 2) Text("feels $feels°", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = look.colors.subtext)
            }
        }
    }
}

private operator fun <T> List<T>.component6(): T = this[5]
