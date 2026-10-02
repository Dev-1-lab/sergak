package uz.sergak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.sergak.Routes
import uz.sergak.core.Knowledge
import uz.sergak.core.RiskLevel
import uz.sergak.data.AppScanner
import uz.sergak.data.Checklist
import uz.sergak.data.ChecklistItem
import uz.sergak.data.Intents
import uz.sergak.data.Prefs
import uz.sergak.goTab
import uz.sergak.guard.GuardStatus
import uz.sergak.ui.theme.Danger
import uz.sergak.ui.theme.Good
import uz.sergak.ui.theme.Warn

private data class HomeState(
    val score: Int,
    val checklistProgress: Int,
    val dangerousApps: Int,
    val suspiciousApps: Int,
    val nextStep: ChecklistItem?,
)

@Composable
fun HomeScreen(nav: NavHostController, resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var state by remember { mutableStateOf<HomeState?>(null) }
    var guardOn by remember { mutableStateOf(GuardStatus.isListenerEnabled(context)) }
    var notifTick by remember { mutableIntStateOf(0) }
    val notifGranted = remember(resumeTick, notifTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifTick++ }

    LaunchedEffect(resumeTick) {
        guardOn = GuardStatus.isListenerEnabled(context)
        state = withContext(Dispatchers.Default) {
            val trusted = prefs.trustedApps
            val apps = runCatching { AppScanner(context).scanAll() }.getOrDefault(emptyList())
                .filter { it.facts.packageName !in trusted }
            val dangerous = apps.count { it.verdict.level == RiskLevel.DANGEROUS }
            val suspicious = apps.count { it.verdict.level == RiskLevel.SUSPICIOUS }
            val progress = Checklist.progress(context, prefs)
            val next = Checklist.items.sortedByDescending { it.weight }.firstOrNull { !Checklist.isDone(context, it, prefs) }
            val score = (progress - dangerous * 20 - suspicious * 5).coerceIn(0, 100)
            HomeState(score, progress, dangerous, suspicious, next)
        }
    }

    val tip = remember { Knowledge.tips.random() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Sergak", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Telefoningiz, kartangiz va Telegram akkauntingiz himoyachisi",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Himoya darajasi
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(state?.score ?: 0)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Himoya darajasi", style = MaterialTheme.typography.titleMedium)
                    val s = state
                    if (s == null) {
                        Text("Tekshirilmoqda…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Himoya rejasi: ${s.checklistProgress}% bajarildi", style = MaterialTheme.typography.bodySmall)
                        when {
                            s.dangerousApps > 0 -> Text("Xavfli ilovalar: ${s.dangerousApps}", color = Danger, fontWeight = FontWeight.SemiBold)
                            s.suspiciousApps > 0 -> Text("Shubhali ilovalar: ${s.suspiciousApps}", color = Warn, fontWeight = FontWeight.SemiBold)
                            else -> Text("Xavfli ilova topilmadi", color = Good)
                        }
                    }
                }
            }
            state?.nextStep?.let { next ->
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    Text("Keyingi qadam:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(next.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { nav.goTab(Routes.PROTECT) }) { Text("Himoya rejasini ochish") }
                }
            }
        }

        // Real vaqt himoyasi
        if (!guardOn || !notifGranted) {
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Real vaqt himoyasini yoqing", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Telegram yoki SMS'da \"Sud qarori.apk\", soxta gov.uz havola yoki \"kodni ayting\" degan xabar kelsa, Sergak darhol ogohlantiradi. Xabarlar faqat telefoningiz ichida tekshiriladi — Sergak'da internet ruxsati umuman yo'q.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
                            OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("1. Bildirishnomalar")
                            }
                        }
                        if (!guardOn) {
                            Button(onClick = { Intents.notificationListenerSettings(context) }) {
                                Text(if (!notifGranted && Build.VERSION.SDK_INT >= 33) "2. Himoyani yoqish" else "Himoyani yoqish")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                Icons.Filled.ManageSearch, "Tekshirish", "Xabar yoki havolani firibgarlikka tekshiring",
                Modifier.weight(1f),
            ) { nav.goTab(Routes.CHECK) }
            ActionTile(
                Icons.Filled.AppRegistration, "Skaner",
                state?.let { "${it.dangerousApps + it.suspiciousApps} ta e'tibor talab ilova" } ?: "Ilovalarni tekshirish",
                Modifier.weight(1f),
            ) { nav.goTab(Routes.SCAN) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.Filled.Shield, "Himoya rejasi", "Telegram, Click/Payme va telefon sozlamalari", Modifier.weight(1f)) {
                nav.goTab(Routes.PROTECT)
            }
            ActionTile(Icons.Filled.School, "O'rganish", "Firibgarlar qanday aldaydi", Modifier.weight(1f)) {
                nav.goTab(Routes.LEARN)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { nav.navigate(Routes.SOS) },
            colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Filled.Sos, null)
            Spacer(Modifier.width(10.dp))
            Text("Meni aldashdi yoki buzishdi — nima qilay?", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Text(tip, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
