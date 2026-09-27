package dev.rint.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import org.junit.Rule
import org.junit.Test

class RinPoses {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar", maxPercentDifference = 100.0)

    @Test fun closeUp() {
        paparazzi.snapshot {
            Row(Modifier.fillMaxSize().background(Color(0xFF1B2A55)).padding(8.dp)) {
                RinSprite(Pose.HEAD, 190.dp, animated = false, timeOffset = 0.3f)
                RinSprite(Pose.FRONT, 190.dp, animated = false, timeOffset = 0.3f)
            }
        }
    }

    @Test fun allPosesPixelPink() {
        dev.rint.launcher.ui.RintSprings.reduce = true
        paparazzi.snapshot {
            Column(Modifier.fillMaxSize().background(Color(0xFF3A4A7A)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Pose.entries.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { p ->
                            Column {
                                RinSprite(p, 60.dp, animated = false, timeOffset = 1.13f, talk = 0.8f, forcePixel = true, accentOverride = 0xFFFF4FA0.toInt())
                                Text(p.name, fontSize = 7.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun allPoses() {
        dev.rint.launcher.ui.RintSprings.reduce = true
        paparazzi.snapshot {
            Column(Modifier.fillMaxSize().background(Color(0xFF3A4A7A)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pose.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            Column {
                                RinSprite(p, 92.dp, animated = false, timeOffset = 0.3f, talk = 0.8f)
                                Text(p.name, fontSize = 9.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
