package com.joy.reelcounter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Black = Color(0xFF080808)
private val Panel = Color(0xFF171717)
private val White = Color(0xFFF5F5F5)
private val Muted = Color(0xFF9A9A9A)
private val Accent = Color(0xFFD7FF3F)
private val platforms = listOf("Instagram", "Facebook Lite")

class MainActivity : ComponentActivity() {
    var resumeTick by mutableIntStateOf(0)
        private set

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reel_limit", "Reel limit alerts", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alerts when the daily Reel count reaches your limit." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        setContent { ReelCounterApp(this) }
    }
}

internal fun countKey(date: LocalDate, platform: String) =
    "count_${date}_${platform.lowercase(Locale.ROOT).replace(" ", "_")}"

@Composable
private fun ReelCounterApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("reel_counter", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf("TODAY") }
    var selectedPlatform by remember { mutableStateOf("Instagram") }
    var showSettings by remember { mutableStateOf(false) }
    var showPermissionSetup by remember { mutableStateOf(true) }
    val resumeTick = (context as? MainActivity)?.resumeTick ?: 0
    val overlayGranted = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
    val notificationGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val accessibilityEnabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(context.packageName + "/.ReelAccessibilityService", ignoreCase = true) == true
    var targetText by remember { mutableStateOf(prefs.getInt("target", 30).toString()) }
    var limitText by remember { mutableStateOf(prefs.getInt("limit", 50).toString()) }
    val today = LocalDate.now()
    val target = remember(refresh) { prefs.getInt("target", 30).coerceAtLeast(1) }
    val limit = remember(refresh) { prefs.getInt("limit", 50).coerceAtLeast(1) }
    val selectedCount = remember(refresh, selectedPlatform) {
        prefs.getInt(countKey(today, selectedPlatform), 0)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun saveCount(platform: String, newCount: Int) {
        val safeCount = newCount.coerceAtLeast(0)
        val key = countKey(today, platform)
        val previousCount = prefs.getInt(key, 0)
        prefs.edit().putInt(key, safeCount).apply()
        refresh++
        val alreadyAlerted = prefs.getString("alert_date_$platform", "") == today.toString()
        if (previousCount < limit && safeCount >= limit && safeCount > 0 && !alreadyAlerted) {
            prefs.edit().putString("alert_date_$platform", today.toString()).apply()
            val hasPermission = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val notification = NotificationCompat.Builder(context, "reel_limit")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("$platform Reel limit reached")
                    .setContentText("You have manually counted $safeCount Reels today. Consider taking a break.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(if (platform == "Instagram") 201 else 202, notification)
                } catch (_: SecurityException) { }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, onPrimary = Black, background = Black,
        surface = Panel, onSurface = White, secondary = Accent
    )) {
        Surface(Modifier.fillMaxSize(), color = Black, contentColor = White) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("REEL / COUNTER", fontSize = 11.sp, color = Muted, letterSpacing = 3.sp)
                        Text("SHORT FORM LOG", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp)
                    }
                    Box(Modifier.size(42.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("R", color = Black, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("TODAY", "HISTORY", "WEEK").forEach { item ->
                        val selected = tab == item
                        Box(
                            Modifier.weight(1f).background(if (selected) Accent else Panel, RoundedCornerShape(11.dp))
                                .clickable { tab = item }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item, color = if (selected) Black else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    "TODAY" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            platforms.forEach { platform ->
                                val selected = platform == selectedPlatform
                                Box(
                                    Modifier.weight(1f).background(if (selected) Color(0xFF30351A) else Panel, RoundedCornerShape(12.dp))
                                        .clickable { selectedPlatform = platform }.padding(horizontal = 9.dp, vertical = 13.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(platform.uppercase(Locale.ENGLISH), color = if (selected) Accent else Muted,
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(today.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)).uppercase(),
                            color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(22.dp)).padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("REELS COUNTED", color = Muted, fontSize = 10.sp, letterSpacing = 3.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(selectedCount.toString().padStart(3, '0'), fontSize = 78.sp, fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace, letterSpacing = (-4).sp)
                                Text(selectedPlatform.uppercase(Locale.ENGLISH), color = Muted, fontSize = 11.sp, letterSpacing = 3.sp)
                                Spacer(Modifier.height(20.dp))
                                LinearProgressIndicator(
                                    progress = { (selectedCount.toFloat() / target).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(5.dp),
                                    color = Accent, trackColor = Color(0xFF343434)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("$selectedCount / $target DAILY TARGET", color = White, fontSize = 12.sp, letterSpacing = 1.sp)
                                Text("${((selectedCount.toFloat() / target) * 100).toInt()}% of target", color = Muted, fontSize = 11.sp)
                                Spacer(Modifier.height(22.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CounterButton("−1", enabled = selectedCount > 0, primary = false) {
                                        saveCount(selectedPlatform, selectedCount - 1)
                                    }
                                    CounterButton("+1", enabled = true, primary = true) {
                                        saveCount(selectedPlatform, selectedCount + 1)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Tap +1 after each Reel you watch", color = Muted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val insta = prefs.getInt(countKey(today, "Instagram"), 0)
                        val facebook = prefs.getInt(countKey(today, "Facebook Lite"), 0)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            StatTile("INSTAGRAM", "$insta", Modifier.weight(1f))
                            StatTile("FACEBOOK LITE", "$facebook", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            StatTile("LIMIT", "$limit EACH", Modifier.weight(1f))
                            StatTile("TOTAL TODAY", "${insta + facebook}", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { targetText = target.toString(); limitText = limit.toString(); showSettings = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
                            border = BorderStroke(1.dp, Color(0xFF444444))
                        ) { Text("TARGET & LIMIT SETTINGS", letterSpacing = 1.sp) }
                    }
                    "HISTORY" -> {
                        Text("LAST 30 DAYS · BY PLATFORM", fontSize = 11.sp, color = Muted, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            items((0..29).map { today.minusDays(it.toLong()) }) { date ->
                                val instagram = prefs.getInt(countKey(date, "Instagram"), 0)
                                val facebook = prefs.getInt(countKey(date, "Facebook Lite"), 0)
                                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(date.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)).uppercase(),
                                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("IG  $instagram     FB LITE  $facebook", color = Muted, fontSize = 11.sp)
                                    }
                                    Text((instagram + facebook).toString().padStart(3, '0'), fontFamily = FontFamily.Monospace,
                                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
                                }
                            }
                        }
                    }
                    else -> {
                        val days = (0..6).map { today.minusDays(it.toLong()) }
                        val instagram = days.sumOf { prefs.getInt(countKey(it, "Instagram"), 0) }
                        val facebook = days.sumOf { prefs.getInt(countKey(it, "Facebook Lite"), 0) }
                        val total = instagram + facebook
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                StatTile("WEEK TOTAL", "$total", Modifier.weight(1f))
                                StatTile("DAILY AVG", String.format(Locale.ENGLISH, "%.1f", total / 7.0), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                StatTile("INSTAGRAM", "$instagram", Modifier.weight(1f))
                                StatTile("FACEBOOK LITE", "$facebook", Modifier.weight(1f))
                            }
                            Text("DAILY BREAKDOWN", fontSize = 11.sp, color = Muted, letterSpacing = 2.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            items(days.reversed()) { date ->
                                val ig = prefs.getInt(countKey(date, "Instagram"), 0)
                                val fb = prefs.getInt(countKey(date, "Facebook Lite"), 0)
                                val dayTotal = ig + fb
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(13.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(date.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)).uppercase(),
                                            color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text("$dayTotal REELS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(7.dp))
                                    LinearProgressIndicator(
                                        progress = { (dayTotal.toFloat() / (target * 2)).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = Accent, trackColor = Color(0xFF343434)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("Instagram $ig  ·  Facebook Lite $fb", color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                        Text("Based on manually recorded counts.", color = Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("COUNT MINDFULLY  •  TAKE BREAKS", modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF666666), fontSize = 9.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            }
        }
    }


    LaunchedEffect(resumeTick) {
        if (overlayGranted && notificationGranted && accessibilityEnabled) showPermissionSetup = false
    }

    if (showPermissionSetup && (!overlayGranted || !notificationGranted || !accessibilityEnabled)) {
        AlertDialog(
            onDismissRequest = { showPermissionSetup = false },
            containerColor = Panel,
            title = { Text("PERMISSIONS SETUP", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enable these permissions for the floating counter, automatic scroll detection and limit alerts.", color = Muted, fontSize = 12.sp)
                    Text((if (overlayGranted) "✓ " else "○ ") + "Display over other apps", color = if (overlayGranted) Accent else White)
                    if (!overlayGranted) TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 23) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
                    }) { Text("GRANT OVERLAY PERMISSION", color = Accent) }
                    Text((if (accessibilityEnabled) "✓ " else "○ ") + "Accessibility auto counter", color = if (accessibilityEnabled) Accent else White)
                    if (!accessibilityEnabled) TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("ENABLE AUTO COUNTER", color = Accent) }
                    Text((if (notificationGranted) "✓ " else "○ ") + "Notifications for usage alerts", color = if (notificationGranted) Accent else White)
                    if (!notificationGranted) TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text("ALLOW NOTIFICATIONS", color = Accent) }
                    Text("Android requires you to confirm overlay and Accessibility access in Settings.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showPermissionSetup = false }) { Text("NOT NOW", color = White) } }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("TARGET & LIMIT", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Daily target and alert threshold apply separately to each platform.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(targetText, { targetText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Daily target (per platform)") }, singleLine = true)
                    OutlinedTextField(limitText, { limitText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Alert threshold (per platform)") }, singleLine = true)
                    if (Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("ENABLE NOTIFICATIONS", color = Accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit()
                        .putInt("target", targetText.toIntOrNull()?.coerceAtLeast(1) ?: 30)
                        .putInt("limit", limitText.toIntOrNull()?.coerceAtLeast(1) ?: 50)
                        .apply()
                    refresh++
                    showSettings = false
                }) { Text("SAVE", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("CANCEL", color = White) } }
        )
    }
}

@Composable
private fun CounterButton(label: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(if (primary) 74.dp else 58.dp)
        .background(if (primary) Accent else Color(0xFF292929), CircleShape)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (primary) Black else if (enabled) White else Muted,
            fontSize = if (primary) 24.sp else 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(13.dp)).padding(13.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = .7.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
