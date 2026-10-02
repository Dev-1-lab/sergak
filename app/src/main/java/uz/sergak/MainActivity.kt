package uz.sergak

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import uz.sergak.ui.CheckScreen
import uz.sergak.ui.HomeScreen
import uz.sergak.ui.LearnScreen
import uz.sergak.ui.LessonScreen
import uz.sergak.ui.ProtectScreen
import uz.sergak.ui.QuizScreen
import uz.sergak.ui.ScanScreen
import uz.sergak.ui.SosScreen
import uz.sergak.ui.theme.SergakTheme

class MainActivity : ComponentActivity() {

    /** Tashqaridan (ulashish, bildirishnoma) kelgan tekshiriladigan matn. */
    private var incomingText by mutableStateOf<String?>(null)
    private var incomingRoute by mutableStateOf<String?>(null)
    /** Telegram va boshqalardan "ulashilgan" yoki "bilan ochilgan" APK fayl. */
    private var incomingApk by mutableStateOf<Uri?>(null)

    /** Foydalanuvchi sozlamalardan qaytganda ekranlar holatni qayta tekshirishi uchun. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            SergakTheme {
                SergakRoot(
                    resumeTick = resumeTick,
                    incomingText = incomingText,
                    incomingRoute = incomingRoute,
                    incomingApk = incomingApk,
                    onIncomingConsumed = {
                        incomingText = null
                        incomingRoute = null
                        incomingApk = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val apk: Uri? = when {
            intent.action == Intent.ACTION_VIEW && intent.data != null -> intent.data
            intent.action == Intent.ACTION_SEND && intent.type?.contains("android.package-archive") == true ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (apk != null) {
            incomingApk = apk
            incomingRoute = Routes.CHECK
            return
        }
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> intent.getStringExtra(EXTRA_CHECK_TEXT)
        }
        if (!text.isNullOrBlank()) {
            incomingText = text
            incomingRoute = Routes.CHECK
        } else {
            intent.getStringExtra(EXTRA_ROUTE)?.let { incomingRoute = it }
        }
    }

    companion object {
        const val EXTRA_CHECK_TEXT = "uz.sergak.CHECK_TEXT"
        const val EXTRA_ROUTE = "uz.sergak.ROUTE"
    }
}

object Routes {
    const val HOME = "home"
    const val CHECK = "check"
    const val SCAN = "scan"
    const val PROTECT = "protect"
    const val LEARN = "learn"
    const val QUIZ = "quiz"
    const val SOS = "sos"
    fun lesson(id: String) = "lesson/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Asosiy", Icons.Filled.Home),
    Tab(Routes.CHECK, "Tekshirish", Icons.Filled.ManageSearch),
    Tab(Routes.SCAN, "Skaner", Icons.Filled.AppRegistration),
    Tab(Routes.PROTECT, "Himoya", Icons.Filled.Shield),
    Tab(Routes.LEARN, "O'rganish", Icons.Filled.School),
)

fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun SergakRoot(
    resumeTick: Int,
    incomingText: String?,
    incomingRoute: String?,
    incomingApk: Uri?,
    onIncomingConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    var pendingCheck by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var pendingApk by androidx.compose.runtime.remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(incomingText, incomingRoute, incomingApk) {
        if (incomingText != null) pendingCheck = incomingText
        if (incomingApk != null) pendingApk = incomingApk
        when {
            incomingRoute == null -> Unit
            incomingRoute.startsWith("learn/") -> nav.navigate(Routes.lesson(incomingRoute.removePrefix("learn/")))
            else -> nav.goTab(incomingRoute)
        }
        if (incomingText != null || incomingRoute != null || incomingApk != null) onIncomingConsumed()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(nav, resumeTick) }
            composable(Routes.CHECK) {
                CheckScreen(
                    initialText = pendingCheck,
                    initialApk = pendingApk,
                    onConsumed = { pendingCheck = null; pendingApk = null },
                )
            }
            composable(Routes.SCAN) { ScanScreen(resumeTick) }
            composable(Routes.PROTECT) { ProtectScreen(resumeTick) }
            composable(Routes.LEARN) { LearnScreen(nav) }
            composable("lesson/{id}") { entry ->
                LessonScreen(entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable(Routes.QUIZ) { QuizScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SOS) { SosScreen(nav, onBack = { nav.popBackStack() }) }
        }
    }
}
