package uz.sergak.ui

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
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import uz.sergak.Routes
import uz.sergak.core.Knowledge
import uz.sergak.core.Lesson
import uz.sergak.data.Prefs
import uz.sergak.ui.theme.Danger
import uz.sergak.ui.theme.Good

@Composable
fun LearnScreen(nav: NavHostController) {
    val context = LocalContext.current
    val best = remember { Prefs(context).quizBest }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Firibgarlar qanday aldaydi")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(
                onClick = { nav.navigate(Routes.QUIZ) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Quiz, null, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("O'zingizni sinang", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (best >= 0) "Eng yaxshi natija: $best / ${Knowledge.quiz.size}" else "${Knowledge.quiz.size} ta real vaziyat. Siz aldanmaysizmi?",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            SectionTitle("Eng ko'p uchraydigan sxemalar")
            Knowledge.lessons.forEach { l ->
                Card(
                    onClick = { nav.navigate(Routes.lesson(l.id)) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l.emoji, fontSize = 30.sp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(l.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(l.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun LessonScreen(id: String, onBack: () -> Unit) {
    val lesson: Lesson? = Knowledge.lessons.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(lesson?.title ?: "Dars", onBack = onBack)
        if (lesson == null) {
            Text("Dars topilmadi", Modifier.padding(16.dp))
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text(lesson.emoji, fontSize = 48.sp)
                Text(lesson.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                LessonBlock("Qanday ishlaydi", lesson.howItWorks, numbered = true)
                LessonBlock("Belgilari", lesson.signs, color = Danger)
                LessonBlock("Nima qilish kerak", lesson.whatToDo, color = Good)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LessonBlock(title: String, lines: List<String>, numbered: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    SectionTitle(title)
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            lines.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        if (numbered) "${i + 1}." else "•",
                        color = color ?: MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun QuizScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val questions = Knowledge.quiz
    var index by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var correct by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("O'zingizni sinang", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (finished) {
                Spacer(Modifier.height(24.dp))
                Text("Natija: $correct / ${questions.size}", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        correct == questions.size -> "Ajoyib! Firibgarlar sizni osonlikcha aldolmaydi. Endi bu bilimni oilangiz bilan ulashing — ayniqsa keksa yaqinlaringiz bilan."
                        correct >= questions.size * 2 / 3 -> "Yaxshi natija! Xato qilgan savollaringiz bo'yicha darslarni o'qib chiqing."
                        else -> "Ehtiyot bo'ling — siz firibgarlar uchun oson nishon bo'lishingiz mumkin. \"O'rganish\" bo'limidagi darslarni o'qing."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { index = 0; picked = null; correct = 0; finished = false }) { Text("Qaytadan") }
            } else {
                val q = questions[index]
                Spacer(Modifier.height(8.dp))
                Text("Savol ${index + 1} / ${questions.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(q.question, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                q.options.forEachIndexed { i, opt ->
                    val p = picked
                    val container = when {
                        p == null -> MaterialTheme.colorScheme.surfaceVariant
                        i == q.correct -> Good.copy(alpha = 0.2f)
                        i == p -> Danger.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Card(
                        onClick = {
                            if (picked == null) {
                                picked = i
                                if (i == q.correct) correct++
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = container),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Text(opt, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val p = picked
                if (p != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (p == q.correct) "To'g'ri! ✅" else "Noto'g'ri ❌",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (p == q.correct) Good else Danger,
                    )
                    Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (index + 1 < questions.size) {
                            index++
                            picked = null
                        } else {
                            finished = true
                            val prefs = Prefs(context)
                            if (correct > prefs.quizBest) prefs.quizBest = correct
                        }
                    }) { Text(if (index + 1 < questions.size) "Keyingi savol" else "Natijani ko'rish") }
                } else {
                    OutlinedButton(onClick = onBack) { Text("Chiqish") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
