package uz.sergak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import uz.sergak.data.Checklist
import uz.sergak.data.ChecklistGroup
import uz.sergak.data.ChecklistItem
import uz.sergak.data.Intents
import uz.sergak.data.Prefs
import uz.sergak.guard.GuardStatus
import uz.sergak.ui.theme.Good

@Composable
fun ProtectScreen(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var tick by remember { mutableIntStateOf(0) }
    val doneMap = remember(resumeTick, tick) {
        Checklist.items.associate { it.id to Checklist.isDone(context, it, prefs) }
    }
    val guardOn = remember(resumeTick) { GuardStatus.isListenerEnabled(context) }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Himoya rejasi")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            // ---- Real vaqt himoyasi ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (guardOn) Good.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (guardOn) "Real vaqt himoyasi yoqilgan ✅" else "Real vaqt himoyasi o'chiq",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Maxfiylik: Sergak'da internet ruxsati yo'q. Xabarlar faqat telefon ichida tekshiriladi, hech qayerga yuborilmaydi va saqlanmaydi.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (guardOn) {
                        Spacer(Modifier.height(8.dp))
                        ToggleRow("Xavfli xabarlar (APK, fishing, kod so'rash)", prefs.alertScam) { prefs.alertScam = it; tick++ }
                        ToggleRow("SMS / Telegram kodi kelganda eslatma", prefs.alertOtp) { prefs.alertOtp = it; tick++ }
                        ToggleRow("Yangi o'rnatilgan xavfli ilovalar", prefs.alertInstall) { prefs.alertInstall = it; tick++ }
                        OutlinedButton(onClick = { Intents.notificationListenerSettings(context) }) { Text("Ruxsat sozlamalari") }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { Intents.notificationListenerSettings(context) }) { Text("Himoyani yoqish") }
                    }
                }
            }

            ChecklistGroup.entries.forEach { group ->
                val groupItems = Checklist.items.filter { it.group == group }
                val done = groupItems.count { doneMap[it.id] == true }
                SectionTitle(group.title)
                Text(group.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (groupItems.isEmpty()) 0f else done.toFloat() / groupItems.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$done / ${groupItems.size} bajarildi", style = MaterialTheme.typography.labelSmall)
                groupItems.forEach { item ->
                    ChecklistCard(
                        item = item,
                        done = doneMap[item.id] == true,
                        onToggle = { checked -> prefs.setDone(item.id, checked); tick++ },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}

@Composable
private fun ChecklistCard(item: ChecklistItem, done: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) Good.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
    ) {
        Column(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.autoCheck == null) {
                    Checkbox(checked = done, onCheckedChange = onToggle)
                } else {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (done) "Bajarilgan" else "Bajarilmagan",
                        tint = if (done) Good else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (item.autoCheck != null) {
                        Text("Avtomatik tekshiriladi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (expanded || !done) {
                Column(Modifier.padding(start = 12.dp)) {
                    Text(item.why, style = MaterialTheme.typography.bodyMedium)
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        item.steps.forEachIndexed { i, s ->
                            Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val action = item.action
                    if (action != null) {
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { action(context) }) { Text(item.actionLabel ?: "Ochish") }
                            if (!expanded) {
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.TextButton(onClick = { expanded = true }) { Text("Qanday?") }
                            }
                        }
                    } else if (!expanded) {
                        androidx.compose.material3.TextButton(onClick = { expanded = true }) { Text("Qanday qilinadi?") }
                    }
                }
            }
        }
    }
    HorizontalDivider(color = androidx.compose.ui.graphics.Color.Transparent)
}
