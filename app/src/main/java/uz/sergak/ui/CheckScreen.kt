package uz.sergak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import uz.sergak.core.LinkAnalyzer
import uz.sergak.core.MessageAnalyzer
import uz.sergak.core.Verdict

private val samples = listOf(
    "APK fayl" to "Assalomu alaykum! Sudga chaqiruv qog'ozingiz: Sud qarori.PDF (8).apk",
    "Soxta gov.uz" to "Davlat tomonidan sizga 30 250 000 so'm kompensatsiya ajratildi. Olish uchun: https://my-gov-uz.online/tolov",
    "Kod so'rash" to "Assalomu alaykum, men bank xavfsizlik xizmati xodimiman. Kartangiz bloklanmoqda, SMS orqali kelgan kodni ayting.",
    "Ovoz berish" to "Iltimos, jiyanim uchun tanlovda ovoz berib yuboring, bir necha ovoz yetmayapti: konkurs-uz.site/vote",
)

@Suppress("DEPRECATION")
@Composable
fun CheckScreen(initialText: String?, onConsumed: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var kindLabel by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    fun analyzeNow() {
        val t = text.trim()
        if (t.isEmpty()) return
        focus.clearFocus()
        val singleUrl = !t.contains(' ') && !t.contains('\n') && LinkAnalyzer.parse(t) != null &&
            !t.lowercase().endsWith(".apk")
        if (singleUrl) {
            kindLabel = "Havola tekshiruvi"
            verdict = LinkAnalyzer.analyze(t)
        } else {
            kindLabel = "Xabar tekshiruvi"
            val r = MessageAnalyzer.analyze(t)
            verdict = r.verdict
            if (r.otp != null && r.verdict.findings.isEmpty()) {
                kindLabel = "Bu xizmatdan kelgan bir martalik kod. Uni hech kimga bermang!"
            }
        }
    }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) {
            text = initialText
            analyzeNow()
            onConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Xabar yoki havolani tekshirish")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(
                "Shubhali xabar, havola yoki fayl nomini shu yerga qo'ying. Tekshiruv internetsiz, faqat telefoningiz ichida bajariladi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Maslahat: Telegram'da xabarni bosib turing → \"Ulashish\" → Sergak. Yoki matnni belgilab → \"Sergak'da tekshirish\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; verdict = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("Masalan: https://my-gov-uz.online yoki \"kodni ayting\"…") },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.getText()?.text?.let { text = it; analyzeNow() }
                }) {
                    Icon(Icons.Filled.ContentPaste, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Joylash")
                }
                if (text.isNotEmpty()) {
                    OutlinedButton(onClick = { text = ""; verdict = null }) {
                        Icon(Icons.Filled.Clear, null)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { analyzeNow() }, enabled = text.isNotBlank()) {
                    Icon(Icons.Filled.ManageSearch, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tekshirish")
                }
            }

            val v = verdict
            if (v != null) {
                SectionTitle(kindLabel)
                VerdictCard(v)
            } else {
                SectionTitle("Namunalar bilan sinab ko'ring")
                samples.forEach { (label, sample) ->
                    AssistChip(
                        onClick = { text = sample; analyzeNow() },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
