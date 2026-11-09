//
//  MainActivity.kt
//  Time Warp (Android)
//
//  Change (November 9, 2026): new file. Android copy of the iOS app's
//  TimeWarpApp.swift and TimeWarpSettingsView.swift: Calculator, Clock and
//  Settings tabs. Nothing here stores or sends any data, apart from the
//  Daylight Saving Help switch and the DST answer saved on the phone.
//

@file:OptIn(ExperimentalMaterial3Api::class)

package com.greenpocket.timewarp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            TimeWarpTheme { TimeWarpRoot() }
        }
    }
}

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    CALCULATOR("Calculator", "Time Calculator", Icons.Filled.History),
    CLOCK("Clock", "Clock", Icons.Filled.Schedule),
    SETTINGS("Settings", "Settings", Icons.Filled.Settings),
}

@Composable
private fun TimeWarpRoot() {
    var tab by remember { mutableStateOf(Tab.CALCULATOR) }
    val calc = remember { CalcState() }
    val inSubScreen = tab == Tab.CALCULATOR && calc.mode != CalcMode.MENU

    // Android's back gesture returns from a calculator screen to its menu.
    BackHandler(enabled = inSubScreen) { calc.mode = CalcMode.MENU }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tab.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    if (inSubScreen) {
                        IconButton(onClick = { calc.mode = CalcMode.MENU }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ios.blue)
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(
            Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (tab) {
                Tab.CALCULATOR -> TimeCalculatorScreen(calc)
                Tab.CLOCK -> ClockScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

/** Settings: the Daylight Saving Help switch and the version/build. */
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val info = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    var dstHelp by remember { mutableStateOf(DaylightSavingStore.isEnabled) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionHeader("TIME CALCULATOR")
        SettingsGroup {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Daylight Saving Help", Modifier.weight(1f))
                Switch(checked = dstHelp, onCheckedChange = {
                    dstHelp = it
                    DaylightSavingStore.isEnabled = it
                })
            }
        }
        Text(
            "Warns when footage is from before a clock change and helps adjust for DVRs that don't change their own clocks. Uses this phone's time zone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        SectionHeader("ABOUT")
        SettingsGroup {
            InfoRow("Version", info.versionName ?: "1.0")
            HorizontalDivider(Modifier.padding(start = 16.dp))
            InfoRow("Build", PackageInfoCompat.getLongVersionCode(info).toString())
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
