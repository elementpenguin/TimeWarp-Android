//
//  ClockScreen.kt
//  Time Warp (Android)
//
//  Change (November 9, 2026): new file. Android copy of ClockView from the
//  iOS app's Shared/Clock.swift. Big HH / MM / SS, stacked when the phone is
//  upright and side by side with colons when it's turned. Ticks exactly on
//  each new second.
//

package com.greenpocket.timewarp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun ClockScreen() {
    var now by remember { mutableStateOf(LocalTime.now()) }

    // Wait until the start of the next second each time, so the display
    // changes exactly when the second does (not up to a second late).
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            delay(1000 - ms % 1000 + 2)
            now = LocalTime.now()
        }
    }

    val h = two(now.hour)
    val m = two(now.minute)
    val s = two(now.second)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            val size = maxHeight * 0.36f
            Row(
                Modifier.padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockText(h, size)
                ClockText(":", size)
                ClockText(m, size)
                ClockText(":", size)
                ClockText(s, size)
            }
        } else {
            val size = min(maxWidth * 0.5f, maxHeight * 0.27f)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClockText(h, size)
                ClockText(m, size)
                ClockText(s, size)
            }
        }
    }
}

@Composable
private fun ClockText(text: String, size: Dp) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        softWrap = false,
    )
}
