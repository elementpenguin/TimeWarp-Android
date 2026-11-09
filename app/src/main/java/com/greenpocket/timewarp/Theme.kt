//
//  Theme.kt
//  Time Warp (Android)
//
//  Change (November 9, 2026): new file. Colours matching the iOS app's
//  system colours, plus light and dark themes.
//

package com.greenpocket.timewarp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** iOS system colours, so both apps look alike. */
object Ios {
    val blue = Color(0xFF007AFF)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)
    val orange = Color(0xFFFF9500)
    val pink = Color(0xFFFF2D55)
    val yellow = Color(0xFFFFCC00)
    val gray = Color(0xFF8E8E93)
}

@Composable
fun TimeWarpTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Ios.blue, secondary = Ios.blue)
    } else {
        lightColorScheme(primary = Ios.blue, secondary = Ios.blue)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
