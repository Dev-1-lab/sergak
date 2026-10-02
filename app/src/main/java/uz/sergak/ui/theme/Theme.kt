package uz.sergak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import uz.sergak.core.RiskLevel

val Teal = Color(0xFF0E5E6F)
val TealDark = Color(0xFF0A3D48)
val Amber = Color(0xFFF2A900)
val Danger = Color(0xFFC62828)
val Warn = Color(0xFFEF8F00)
val Good = Color(0xFF2E7D32)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBF0),
    onPrimaryContainer = TealDark,
    secondary = Amber,
    onSecondary = Color(0xFF2B1F00),
    secondaryContainer = Color(0xFFFFE6A8),
    onSecondaryContainer = Color(0xFF2B1F00),
    error = Danger,
    background = Color(0xFFF7FAFA),
    surface = Color(0xFFF7FAFA),
    surfaceVariant = Color(0xFFE3ECEE),
    onSurfaceVariant = Color(0xFF3F4A4C),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0DF),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0E5E6F),
    onPrimaryContainer = Color(0xFFCDEBF0),
    secondary = Color(0xFFFFC94D),
    onSecondary = Color(0xFF3F2E00),
    error = Color(0xFFFF8A80),
    background = Color(0xFF0F1415),
    surface = Color(0xFF0F1415),
    surfaceVariant = Color(0xFF263133),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
)

@Composable
fun SergakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

fun RiskLevel.color(): Color = when (this) {
    RiskLevel.DANGEROUS -> Danger
    RiskLevel.SUSPICIOUS -> Warn
    RiskLevel.SAFE -> Good
}
