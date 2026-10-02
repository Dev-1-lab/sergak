package uz.sergak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.sergak.core.AppRisk
import uz.sergak.core.RiskLevel
import uz.sergak.data.AppScanner
import uz.sergak.data.DeviceAuditor
import uz.sergak.data.DeviceCheck
import uz.sergak.data.Intents
import uz.sergak.data.Prefs
import uz.sergak.ui.theme.Danger
import uz.sergak.ui.theme.Good
import uz.sergak.ui.theme.color

private class ScanResult(val device: List<DeviceCheck>, val apps: List<AppRisk>)

@Composable
fun ScanScreen(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var manualTick by remember { mutableIntStateOf(0) }
    var trusted by remember { mutableStateOf(prefs.trustedApps) }
    var showSafe by remember { mutableStateOf(false) }

    LaunchedEffect(resumeTick, manualTick) {
        result = withContext(Dispatchers.Default) {
            ScanResult(
                runCatching { DeviceAuditor(context).run() }.getOrDefault(emptyList()),
                runCatching { AppScanner(context).scanAll() }.getOrDefault(emptyList()),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Telefon skaneri", actions = {
            IconButton(onClick = { result = null; manualTick++ }) { Icon(Icons.Filled.Refresh, "Qayta skanerlash") }
        })
        val r = result
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Ilovalar va sozlamalar tekshirilmoqda…")
                }
            }
        } else {
        val flagged = r.apps.filter { it.verdict.level != RiskLevel.SAFE && it.facts.packageName !in trusted }
        val safe = r.apps.filter { it.verdict.level == RiskLevel.SAFE || it.facts.packageName in trusted }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { SectionTitle("Telefon sozlamalari") }
            items(r.device, key = { "d_" + it.id }) { c ->
                val fix = c.fix
                StatusRow(
                    c.state.icon(), c.state.color(), c.title, c.detail,
                    trailing = if (fix != null) {
                        { TextButton(onClick = { fix(context) }) { Text(c.fixLabel ?: "Tuzatish") } }
                    } else null,
                )
                HorizontalDivider()
            }

            item {
                SectionTitle(
                    if (flagged.isEmpty()) "Ilovalar: xavf topilmadi"
                    else "E'tibor talab qiladigan ilovalar: ${flagged.size}"
                )
                if (flagged.isEmpty()) {
                    StatusRow(
                        Icons.Filled.CheckCircle, Good,
                        "${r.apps.size} ta ilova tekshirildi",
                        "SMS-o'g'ri viruslarga xos belgilar topilmadi.",
                    )
                } else {
                    Text(
                        "Quyidagi ilovalarda viruslarga xos belgilar bor. Agar ilovani tanimasangiz yoki uni o'zingiz Google Play'dan o'rnatmagan bo'lsangiz — o'chiring.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(flagged, key = { "a_" + it.facts.packageName }) { app ->
                AppRiskCard(
                    app,
                    onUninstall = { Intents.uninstall(context, app.facts.packageName) },
                    onDetails = { Intents.appDetails(context, app.facts.packageName) },
                    onTrust = {
                        prefs.trustedApps = prefs.trustedApps + app.facts.packageName
                        trusted = prefs.trustedApps
                    },
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showSafe = !showSafe }) {
                    Text(if (showSafe) "Boshqa ilovalarni yashirish" else "Boshqa ilovalar (${safe.size}) ni ko'rsatish")
                }
            }
            if (showSafe) {
                items(safe, key = { "s_" + it.facts.packageName }) { app ->
                    val isTrusted = app.facts.packageName in trusted
                    StatusRow(
                        Icons.Filled.CheckCircle, Good, app.facts.label,
                        if (isTrusted) "Siz ishonchli deb belgilagansiz" else app.facts.packageName,
                        trailing = if (isTrusted) {
                            {
                                TextButton(onClick = {
                                    prefs.trustedApps = prefs.trustedApps - app.facts.packageName
                                    trusted = prefs.trustedApps
                                }) { Text("Bekor qilish") }
                            }
                        } else null,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

@Composable
private fun AppRiskCard(app: AppRisk, onUninstall: () -> Unit, onDetails: () -> Unit, onTrust: () -> Unit) {
    val c = app.verdict.level.color()
    var expanded by remember { mutableStateOf(app.verdict.level == RiskLevel.DANGEROUS) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.10f)),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(levelIcon(app.verdict.level), null, tint = c)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.facts.label, style = MaterialTheme.typography.titleMedium)
                    Text(app.facts.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (app.verdict.level == RiskLevel.DANGEROUS) "Xavfli" else "Shubhali",
                    color = c, fontWeight = FontWeight.Bold,
                )
            }
            if (expanded) {
                app.verdict.findings.forEach { f ->
                    Spacer(Modifier.height(8.dp))
                    Text("• " + f.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(f.explanation, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onUninstall,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    ) { Text("O'chirish") }
                    OutlinedButton(onClick = onDetails) { Text("Ruxsatlar") }
                }
                TextButton(onClick = onTrust) { Text("Men bu ilovani bilaman va ishonaman") }
            }
        }
    }
}
