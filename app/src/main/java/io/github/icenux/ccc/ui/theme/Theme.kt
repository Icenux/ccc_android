package io.github.icenux.ccc.ui.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.icenux.ccc.R

/**
 * ccc's sci-fi / HUD palette (design #13): near-black navy, cyan as the primary colour, amber for what waits
 * for you, red for errors, blue for "waiting to retry", green only for Claude's messages.
 *
 * The old names (Background, Title, Muted …) stay, so every screen follows the new look; the new tokens
 * (Cyan, Dim, Faint, LineBright …) are for the new components.
 */
object CccColors {
    val Background = Color(0xFF040912)
    val Surface = Color(0xFF09131F)
    val SurfaceHigh = Color(0xFF0E1C2C)
    val Line = Color(0xFF16324A)
    val LineBright = Color(0xFF2A6B8C)
    val Cyan = Color(0xFF38E1FF)
    val CyanSoft = Color(0xFF0A2A3A)
    val Text = Color(0xFFE8F6FF)
    val Dim = Color(0xFF8FB0C6)
    val Faint = Color(0xFF52708A)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF5470)
    val Blue = Color(0xFF5B9DFF)
    val Glow = Color(0xFF0B2A3E)

    // What the existing screens use.
    val Title = Cyan
    val Selection = CyanSoft
    val Accent = Amber
    val Human = Cyan
    // The only green: a clear, darker one — the old mint (#5FE3A8) was so light that it blurred with amber.
    val Claude = Color(0xFF1FAE50)
    val Muted = Dim
    val Running = Cyan
    val Waiting = Amber
    val Error = Red
    val Approval = Amber
}

/** Chakra Petch for headings, JetBrains Mono for data and labels, Inter for message text (all OFL, in res/font). */
object CccFonts {
    val Heading = FontFamily(
        Font(R.font.chakra_petch_regular, FontWeight.Normal),
        Font(R.font.chakra_petch_medium, FontWeight.Medium),
        Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
        Font(R.font.chakra_petch_bold, FontWeight.Bold),
    )

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val Mono = FontFamily(
        listOf(400, 500, 700).map { w ->
            Font(R.font.jetbrains_mono_variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val Body = FontFamily(
        listOf(400, 500, 600, 700).map { w ->
            Font(R.font.inter_variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )
}

private fun heading(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = CccFonts.Heading, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = CccFonts.Body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun label(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = CccFonts.Mono, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val typography = Typography(
    displayLarge = heading(48, 56, FontWeight.Bold),
    displayMedium = heading(40, 48, FontWeight.Bold),
    displaySmall = heading(32, 40, FontWeight.Bold),
    headlineLarge = heading(30, 38),
    headlineMedium = heading(26, 34),
    headlineSmall = heading(22, 30),
    titleLarge = heading(20, 26),
    titleMedium = heading(17, 24, FontWeight.Medium),
    titleSmall = heading(15, 20, FontWeight.Medium),
    bodyLarge = body(16, 24),
    bodyMedium = body(14, 21),
    bodySmall = body(12, 17),
    labelLarge = label(13, 18, FontWeight.Medium),
    labelMedium = label(12, 16),
    labelSmall = label(10, 14),
)

/** Sharp corners, no capsules: chips 2–4 dp, cards and inputs 6 dp, panels 8 dp. */
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

private val scheme = darkColorScheme(
    primary = CccColors.Cyan,
    onPrimary = CccColors.Background,
    primaryContainer = CccColors.CyanSoft,
    onPrimaryContainer = CccColors.Text,
    secondary = CccColors.Amber,
    onSecondary = CccColors.Background,
    secondaryContainer = CccColors.CyanSoft,
    onSecondaryContainer = CccColors.Text,
    tertiary = CccColors.Claude,
    // Transparent: the screens show the backdrop CccTheme paints (near-black with a glow at the top).
    background = Color.Transparent,
    onBackground = CccColors.Text,
    surface = CccColors.Background,
    onSurface = CccColors.Text,
    surfaceVariant = CccColors.SurfaceHigh,
    onSurfaceVariant = CccColors.Dim,
    surfaceContainer = CccColors.Surface,
    surfaceContainerHigh = CccColors.SurfaceHigh,
    surfaceContainerHighest = CccColors.SurfaceHigh,
    surfaceContainerLow = CccColors.Surface,
    surfaceContainerLowest = CccColors.Background,
    outline = CccColors.LineBright,
    outlineVariant = CccColors.Line,
    error = CccColors.Red,
    scrim = Color(0xFF000000),
)

/**
 * Glow and endless animations. False when the person turned them off in the menu or the system has
 * animations off (ANIMATOR_DURATION_SCALE 0): then nothing loops and nothing is drawn only for the glow.
 */
data class Effects(val glow: Boolean = true, val loops: Boolean = true)

val LocalEffects = compositionLocalOf { Effects() }

/** The system's animation scale is 0 (developer options or "remove animations"). */
fun animationsOff(context: android.content.Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

/** Always dark, the HUD look; [effects] is the person's switch for glow and endless animations. */
@Composable
fun CccTheme(effects: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val off = remember(context) { animationsOff(context) }
    val current = Effects(glow = effects, loops = effects && !off)
    CompositionLocalProvider(LocalEffects provides current) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(CccColors.Background)
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(CccColors.Glow, Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.width * 1.1f,
                            ),
                        )
                    },
            ) { content() }
        }
    }
}

@Composable
@ReadOnlyComposable
fun effects(): Effects = LocalEffects.current
