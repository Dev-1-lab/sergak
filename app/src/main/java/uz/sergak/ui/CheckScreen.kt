package uz.sergak.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.sergak.core.Finding
import uz.sergak.core.LinkAnalyzer
import uz.sergak.core.MessageAnalyzer
import uz.sergak.core.ThreatIntel
import uz.sergak.core.Verdict
import uz.sergak.data.ApkFileChecker
import uz.sergak.data.IntelRepository

private val samples = listOf(
    "APK fayl" to "Assalomu alaykum! Sudga chaqiruv qog'ozingiz: Sud qarori.PDF (8).apk",
    "Soxta gov.uz" to "Davlat tomonidan sizga 30 250 000 so'm kompensatsiya ajratildi. Olish uchun: https://my-gov-uz.online/tolov",
    "Kod so'rash" to "Assalomu alaykum, men bank xavfsizlik xizmati xodimiman. Kartangiz bloklanmoqda, SMS orqali kelgan kodni ayting.",
    "Ovoz berish" to "Iltimos, jiyanim uchun tanlovda ovoz berib yuboring, bir necha ovoz yetmayapti: konkurs-uz.site/vote",
)

private sealed interface CloudState {
    data object Off : CloudState
    data object Loading : CloudState
    data class Done(val checked: Int) : CloudState
    data object Failed : CloudState
}

@Suppress("DEPRECATION")
@Composable
fun CheckScreen(initialText: String?, initialApk: Uri?, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val intel = remember { IntelRepository(context) }
    var text by rememberSaveable { mutableStateOf("") }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var kindLabel by remember { mutableStateOf("") }
    var cloud by remember { mutableStateOf<CloudState>(CloudState.Off) }
    var requestId by remember { mutableIntStateOf(0) }
    var apkLoading by remember { mutableStateOf(false) }
    var apkResult by remember { mutableStateOf<ApkFileChecker.Result?>(null) }
    var apkError by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    fun checkApk(uri: Uri) {
        apkLoading = true
        apkResult = null
        apkError = null
        verdict = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ApkFileChecker(context).check(uri) } }
            apkLoading = false
            r.onSuccess { apkResult = it }.onFailure { apkError = "Faylni o'qib bo'lmadi: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) checkApk(uri)
    }

    fun analyzeNow() {
        val t = text.trim()
        if (t.isEmpty()) return
        focus.clearFocus()
        apkResult = null
        val myId = ++requestId
        val singleUrl = !t.contains(' ') && !t.contains('\n') && LinkAnalyzer.parse(t) != null &&
            !t.lowercase().endsWith(".apk")
        val base: Verdict
        val urls: List<String>
        if (singleUrl) {
            kindLabel = "Havola tekshiruvi"
            base = LinkAnalyzer.analyze(t)
            urls = listOf(t)
        } else {
            kindLabel = "Xabar tekshiruvi"
            val r = MessageAnalyzer.analyze(t)
            base = r.verdict
            urls = r.urls
            if (r.otp != null && r.verdict.findings.isEmpty()) {
                kindLabel = "Bu xizmatdan kelgan bir martalik kod. Uni hech kimga bermang!"
            }
        }

        // Serverdan yuklangan fishing domenlar ro'yxati (oflayn ishlaydi)
        val feedHits = urls.mapNotNull { u ->
            val host = LinkAnalyzer.parse(u)?.host ?: return@mapNotNull null
            if (intel.isBlockedDomain(host)) Finding(
                "feed_domain", "Ma'lum fishing sayt: $host",
                "Bu domen Sergak tahdidlar ro'yxatida firibgarlik sayti sifatida qayd etilgan.", "Ochmang va hech narsa kiritmang.", 90,
            ) else null
        }
        verdict = if (feedHits.isEmpty()) base else Verdict.of(base.findings + feedHits)

        val toCheck = urls.filter { u -> LinkAnalyzer.parse(u)?.let { !LinkAnalyzer.isOfficial(it.host) } == true }.take(3)
        if (!intel.cloudReady || toCheck.isEmpty()) {
            cloud = CloudState.Off
            return
        }
        cloud = CloudState.Loading
        scope.launch {
            val reps = withContext(Dispatchers.IO) { toCheck.mapNotNull { intel.lookupUrl(it) } }
            if (myId != requestId) return@launch
            val extra = reps.mapNotNull { ThreatIntel.urlFinding(it) }
            val current = verdict
            if (current != null && extra.isNotEmpty()) verdict = Verdict.of(current.findings + extra)
            cloud = if (reps.isEmpty()) CloudState.Failed else CloudState.Done(reps.size)
        }
    }

    LaunchedEffect(initialText, initialApk) {
        if (initialApk != null) {
            checkApk(initialApk)
            onConsumed()
        } else if (!initialText.isNullOrBlank()) {
            text = initialText
            analyzeNow()
            onConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Xabar, havola yoki faylni tekshirish")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(
                if (intel.cloudReady) "Shubhali xabar, havola yoki APK faylni tekshiring. Havolalar telefonda va xavfsizlik bazalarida (VirusTotal, Google, URLhaus) tekshiriladi."
                else "Shubhali xabar, havola yoki APK faylni tekshiring. Tekshiruv telefoningiz ichida bajariladi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Maslahat: Telegram'da xabarni bosib turing → \"Ulashish\" → Sergak. APK faylni ham shunday Sergak'ka ulashing — o'rnatishdan oldin tekshiramiz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; verdict = null; cloud = CloudState.Off },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Masalan: https://my-gov-uz.online yoki \"kodni ayting\"…") },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    clipboard.getText()?.text?.let { text = it; analyzeNow() }
                }) {
                    Icon(Icons.Filled.ContentPaste, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Joylash")
                }
                if (text.isNotEmpty()) {
                    OutlinedButton(onClick = { text = ""; verdict = null; cloud = CloudState.Off }) {
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
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Android, null)
                Spacer(Modifier.width(8.dp))
                Text("APK faylni o'rnatishdan oldin tekshirish")
            }

            when (val c = cloud) {
                CloudState.Loading -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Xavfsizlik bazalarida tekshirilmoqda…", style = MaterialTheme.typography.bodySmall)
                }
                is CloudState.Done -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${c.checked} ta havola bulutli bazalarda ham tekshirildi", style = MaterialTheme.typography.bodySmall)
                }
                CloudState.Failed -> Text(
                    "Bulutli tekshiruv hozir ishlamadi (internet yoki server). Mahalliy tahlil natijasi ko'rsatildi.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp),
                )
                CloudState.Off -> Unit
            }

            if (apkLoading) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("APK fayl tekshirilmoqda…")
                }
            }
            apkError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            apkResult?.let { r -> ApkResultSection(r, intel.cloudReady) }

            val v = verdict
            if (v != null) {
                SectionTitle(kindLabel)
                VerdictCard(v)
            } else if (apkResult == null && !apkLoading) {
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

@Composable
private fun ApkResultSection(r: ApkFileChecker.Result, cloudReady: Boolean) {
    SectionTitle("APK fayl: ${r.fileName}")
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (r.isApk) {
                Text("Ilova nomi: ${r.risk.facts.label}", fontWeight = FontWeight.SemiBold)
                Text("Paket: ${r.risk.facts.packageName}", style = MaterialTheme.typography.bodySmall)
                val perms = r.risk.facts.requestedPermissions
                val sms = perms.count { it.contains("SMS") }
                Text(
                    "So'raydigan ruxsatlar: ${perms.size} ta" + if (sms > 0) " (shundan SMS: $sms ta!)" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("Bu fayl Android ilovasi (APK) emas yoki shikastlangan.", fontWeight = FontWeight.SemiBold)
            }
            r.risk.sha256?.let {
                Text("SHA-256: ${it.take(16)}…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                when {
                    r.cloudChecked -> "✓ VirusTotal va MalwareBazaar bazalarida tekshirildi (faqat xesh yuborildi)"
                    cloudReady -> "Bulutli bazalar javob bermadi — faqat telefon ichidagi tahlil"
                    else -> "Telefon ichidagi tahlil (bulutli tekshiruv o'chiq)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    VerdictCard(r.risk.verdict)
}
