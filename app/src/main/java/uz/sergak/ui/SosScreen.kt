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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import uz.sergak.Routes
import uz.sergak.core.Knowledge
import uz.sergak.data.Intents
import uz.sergak.goTab
import uz.sergak.ui.theme.Danger

private class SosStep(val title: String, val body: String, val button: String? = null, val onClick: (() -> Unit)? = null)

@Composable
fun SosScreen(nav: NavHostController, onBack: () -> Unit) {
    val context = LocalContext.current
    val steps = listOf(
        SosStep(
            "Internetni o'chiring",
            "APK faylni ochib qo'ygan bo'lsangiz — darhol \"Samolyot rejimi\"ni yoqing. Virus firibgarga SMS-kodlarni yubora olmay qoladi.",
            "Samolyot rejimi", { Intents.airplaneMode(context) },
        ),
        SosStep(
            "Bankka qo'ng'iroq qilib kartani bloklang",
            "Kartangiz orqasidagi raqamga yoki bank ilovasidagi \"Kartani bloklash\" tugmasi orqali. Click/Payme'da ham kartani vaqtincha o'chiring. Qancha tez qilsangiz, shuncha ko'p pul saqlab qolasiz.",
        ),
        SosStep(
            "Shubhali ilovani o'chiring",
            "Sergak skaneri yaqinda o'rnatilgan, SMS o'qiydigan va belgisi yashirilgan ilovalarni ko'rsatadi. \"Qurilma administratori\" bo'lsa, avval o'sha huquqni o'chiring.",
            "Skanerni ochish", { nav.goTab(Routes.SCAN) },
        ),
        SosStep(
            "Telegram'ni qutqaring",
            "Sozlamalar → Qurilmalar → \"Boshqa barcha seanslarni yakunlash\". Keyin ikki bosqichli tasdiqlash (bulutli parol) o'rnating. Akkaunt o'g'irlangan bo'lsa, Telegram ilovasida \"Kirish\" orqali raqamingiz bilan qayta kiring.",
            "Telegram'ni ochish", { Intents.openTelegram(context) },
        ),
        SosStep(
            "Yaqinlaringizni ogohlantiring",
            "Akkauntingizdan ular ham fayl yoki pul so'rovi olgan bo'lishi mumkin. Telefon qilib yoki boshqa messenjer orqali ayting: \"Mening nomimdan kelgan fayllarni ochmang, pul o'tkazmang\".",
        ),
        SosStep(
            "Parollarni almashtiring",
            "Boshqa, toza qurilmadan: Google akkaunt, elektron pochta, bank ilovalari parollarini o'zgartiring.",
        ),
        SosStep(
            "Murojaat qiling",
            "Pul o'g'irlangan bo'lsa — 102 ga qo'ng'iroq qiling yoki ichki ishlar bo'limiga ariza yozing. Yozishmalar, fayl nomi, karta raqami va o'tkazma vaqtlarining skrinshotlarini saqlang. Ularni o'chirmang!",
        ),
        SosStep(
            "Eng ishonchli tozalash",
            "Virus o'chirilmasa yoki shubha qolsa — muhim ma'lumotlarni saqlab, telefonni zavod sozlamalariga qaytaring (Factory reset). Keyin ilovalarni faqat Google Play'dan o'rnating.",
        ),
    )

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Favqulodda yordam", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Vahimaga tushmang. Tartib bilan harakat qiling.", style = MaterialTheme.typography.titleMedium, color = Danger)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Birinchi 2 qadamni hoziroq bajaring — ular pulingizni saqlab qoladi.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (i < 2) Danger else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(5.dp))
                            Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(s.body, style = MaterialTheme.typography.bodyMedium)
                        val action = s.onClick
                        if (s.button != null && action != null) {
                            Spacer(Modifier.height(6.dp))
                            FilledTonalButton(onClick = action) { Text(s.button) }
                        }
                    }
                }
            }

            SectionTitle("Qayerga murojaat qilish")
            Knowledge.contacts.forEach { c ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(c.description, style = MaterialTheme.typography.bodySmall)
                            c.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                        }
                        val phone = c.phone
                        val link = c.link
                        if (phone != null) {
                            FilledTonalButton(onClick = { Intents.dial(context, phone) }) {
                                Icon(Icons.Filled.Call, null, Modifier.size(18.dp))
                            }
                        } else if (link != null) {
                            FilledTonalButton(onClick = { Intents.openUrl(context, link) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Text(
                "Raqamlar rasmiy manbalardan olingan (IIV, csec.uz, cbu.uz). Bank raqamini doim kartangiz orqasidan yoki bankning rasmiy ilovasidan oling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
