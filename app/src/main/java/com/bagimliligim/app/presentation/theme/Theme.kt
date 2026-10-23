package com.bagimliligim.app.presentation.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Emerald = Color(0xFF9BC5AE)
val Amber = Color(0xFFE4BD83)
val Crimson = Color(0xFFE3989D)
val Lavender = Color(0xFFC9B4C6)

private val Dark = darkColorScheme(
    primary = Color(0xFFE6C69B), onPrimary = Color(0xFF30251C), primaryContainer = Color(0xFFE6C69B),
    onPrimaryContainer = Color(0xFF30251C), secondary = Lavender,
    secondaryContainer = Color(0xFF3C3039), onSecondaryContainer = Color(0xFFE9CADB),
    background = Color(0xFF171518), onBackground = Color(0xFFF7F1EA),
    surface = Color(0xFF171518), onSurface = Color(0xFFF7F1EA),
    surfaceContainer = Color(0xFF242125), surfaceContainerHigh = Color(0xFF302B31),
    surfaceVariant = Color(0xFF383138), onSurfaceVariant = Color(0xFFBEB4BA),
    outline = Color(0xFF8E818A), outlineVariant = Color(0xFF3D343C),
)
private val Light = lightColorScheme(
    primary = Color(0xFF705336), onPrimary = Color.White,
    primaryContainer = Color(0xFFF1D9B8), onPrimaryContainer = Color(0xFF30251C),
    background = Color(0xFFF9F5EF), surface = Color(0xFFF9F5EF),
    surfaceContainer = Color(0xFFF0EAE3), surfaceContainerHigh = Color(0xFFE7DED5),
    secondary = Color(0xFF755970), onSurfaceVariant = Color(0xFF71646D),
    secondaryContainer = Color(0xFFF2DEE8), onSecondaryContainer = Color(0xFF392332),
)
private val Type = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 64.sp, letterSpacing = (-3).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-.7).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun SwipeMeterTheme(dark: Boolean = true, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = Type, content = content)
}
