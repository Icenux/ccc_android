package io.github.icenux.ccc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.CccFonts
import io.github.icenux.ccc.ui.theme.effects

// --- glow -------------------------------------------------------------------------------------------------

/** A soft radial glow behind a small thing (a status dot, a button): cheaper than a coloured Modifier.shadow. */
@Composable
fun Modifier.glowBehind(color: Color, radius: Dp = 12.dp, alpha: Float = 0.5f): Modifier =
    if (!effects().glow) this else this.drawBehind {
        val r = radius.toPx()
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = r),
            radius = r,
            center = center,
        )
    }

/** A glow around a rectangular panel or button: a few faint, widening outlines. */
@Composable
fun Modifier.glowRect(color: Color, corner: Dp = 6.dp, spread: Dp = 10.dp, alpha: Float = 0.30f): Modifier =
    if (!effects().glow || alpha <= 0f) this else this.drawBehind {
        val steps = 4
        for (i in steps downTo 1) {
            val grow = spread.toPx() * i / steps
            drawRoundRect(
                color = color.copy(alpha = alpha * (1f - i / (steps + 1f)) * 0.5f),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(corner.toPx() + grow),
            )
        }
    }

// --- status dot --------------------------------------------------------------------------------------------

/** How a status dot moves: running = pulsing glow, needs approval = slow blink, error and idle = still. */
enum class Pulse { NONE, GLOW, BLINK }

fun pulseOf(status: io.github.icenux.ccc.data.ConversationStatus, needsApproval: Boolean): Pulse = when {
    needsApproval -> Pulse.BLINK
    status == io.github.icenux.ccc.data.ConversationStatus.RUNNING -> Pulse.GLOW
    else -> Pulse.NONE
}

@Composable
fun GlowDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp, pulse: Pulse = Pulse.NONE) {
    val loops = effects().loops
    val level = if (pulse != Pulse.NONE && loops) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = if (pulse == Pulse.BLINK) 1f else 0.35f,
            targetValue = if (pulse == Pulse.BLINK) 0.25f else 1f,
            animationSpec = infiniteRepeatable(
                tween(if (pulse == Pulse.BLINK) 1400 else 900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "dot-level",
        ).value
    } else 1f
    Box(
        modifier
            .size(size)
            .glowBehind(color, radius = size * 2.2f, alpha = 0.55f * (if (pulse == Pulse.GLOW) level else 1f))
            .alpha(if (pulse == Pulse.BLINK) level else 1f)
            .background(color, CircleShape),
    )
}

// --- the panel with corner brackets ---------------------------------------------------------------------------

private fun DrawScope.brackets(color: Color, arm: Float, inset: Float, stroke: Float) {
    val w = size.width
    val h = size.height
    val s = Stroke(stroke)
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val p = Path().apply {
            moveTo(x, y + dy * arm); lineTo(x, y); lineTo(x + dx * arm, y)
        }
        drawPath(p, color, style = s)
    }
    corner(inset, inset, 1f, 1f)
    corner(w - inset, inset, -1f, 1f)
    corner(inset, h - inset, 1f, -1f)
    corner(w - inset, h - inset, -1f, -1f)
}

/** A light that runs once around the panel's frame every few seconds. */
private fun DrawScope.orbit(progress: Float, color: Color, corner: Float) {
    val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    val span = length * 0.18f
    val start = progress * length
    fun segment(from: Float, to: Float) {
        val part = Path()
        measure.getSegment(from, to, part, true)
        drawPath(part, color.copy(alpha = 0.22f), style = Stroke(7.dp.toPx()))
        drawPath(part, color.copy(alpha = 0.95f), style = Stroke(1.6.dp.toPx()))
    }
    if (start + span <= length) segment(start, start + span) else {
        segment(start, length)
        segment(0f, start + span - length)
    }
}

/**
 * A HUD panel: dark surface, a thin frame, optionally corner brackets, a glow and a light running around
 * the frame ([orbit]). With [onClick] the whole panel taps.
 */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    accent: Color = CccColors.LineBright,
    brackets: Boolean = true,
    glow: Boolean = false,
    orbit: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val loops = effects().loops
    val progress = if (orbit && loops) {
        rememberInfiniteTransition(label = "orbit").animateFloat(
            0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "orbit-progress",
        ).value
    } else -1f
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glow) Modifier.glowRect(accent, corner = 8.dp, spread = 12.dp, alpha = 0.35f) else Modifier)
            .clip(shape)
            .background(CccColors.Surface, shape)
            .border(1.dp, accent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawWithContent {
                drawContent()
                if (brackets) brackets(accent, arm = 12.dp.toPx(), inset = 9.dp.toPx(), stroke = 2.dp.toPx())
                if (progress >= 0f) orbit(progress, accent, 8.dp.toPx())
            }
            .padding(contentPadding),
        content = content,
    )
}

// --- usage bar, numbers, chips --------------------------------------------------------------------------------

/** Over 80 % amber, over 90 % red. */
fun usageColor(percent: Double): Color = when {
    percent >= 90 -> CccColors.Red
    percent >= 80 -> CccColors.Amber
    else -> CccColors.Cyan
}

/** A label, a bar that grows from 0 to the value when it comes into view (600 ms) and the percent. */
@Composable
fun UsageRow(label: String, percent: Double, modifier: Modifier = Modifier, labelWidth: Dp = 72.dp) {
    val fraction = (percent / 100).toFloat().coerceIn(0f, 1f)
    val grown = remember { Animatable(0f) }
    LaunchedEffect(fraction) { grown.animateTo(fraction, tween(600, easing = FastOutSlowInEasing)) }
    val color by androidx.compose.animation.animateColorAsState(usageColor(percent), tween(400), label = "usage-colour")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(labelWidth), style = MaterialTheme.typography.labelMedium, color = CccColors.Dim)
        Box(
            Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(CccColors.Line.copy(alpha = 0.6f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(grown.value)
                    .height(5.dp)
                    .glowRect(color, corner = 1.dp, spread = 6.dp, alpha = 0.6f)
                    .background(color),
            )
        }
        Text(
            "${percent.toInt()} %",
            Modifier.width(56.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = CccColors.Text,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A number that counts up from 0 when it appears and whenever it changes. */
@Composable
fun rememberCountUp(target: Int): Int {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    return value.value.toInt().coerceAtMost(target.coerceAtLeast(0))
}

/** A big number over a small label (the Dashboard's counts). */
@Composable
fun StatCell(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    StatCell(rememberCountUp(value).toString(), label, color, modifier)
}

@Composable
fun StatCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = CccColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small tag: [color] for text and frame, [filled] fills it with the colour (selected). */
@Composable
fun CccChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CccColors.Dim,
    filled: Boolean = false,
    dot: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.12f), shape)
            .border(1.dp, if (filled) color else color.copy(alpha = 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).background(if (filled) CccColors.Background else color, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) CccColors.Background else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small spaced-out caps for the labels over a block ("FOLDER ON THE COMPUTER"). */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = CccColors.Faint) {
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// --- buttons ----------------------------------------------------------------------------------------------------

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, GHOST }

/** The HUD's buttons: sharp (4 dp), the primary one cyan with a glow that grows while pressed. */
@Composable
fun CccButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.PRIMARY,
    color: Color? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glowAlpha by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.75f else 0.35f, tween(160), label = "button-glow")
    val accent = color ?: when (kind) {
        ButtonKind.PRIMARY -> CccColors.Cyan
        ButtonKind.DANGER -> CccColors.Red
        else -> CccColors.Dim
    }
    val colors = when (kind) {
        ButtonKind.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = accent, contentColor = CccColors.Background,
            disabledContainerColor = CccColors.Line, disabledContentColor = CccColors.Faint,
        )
        ButtonKind.DANGER -> ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.14f), contentColor = accent,
            disabledContainerColor = CccColors.Surface, disabledContentColor = CccColors.Faint,
        )
        ButtonKind.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = CccColors.Surface, contentColor = color ?: CccColors.Text,
            disabledContainerColor = CccColors.Surface, disabledContentColor = CccColors.Faint,
        )
        ButtonKind.GHOST -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = color ?: CccColors.Cyan,
            disabledContainerColor = Color.Transparent, disabledContentColor = CccColors.Faint,
        )
    }
    val border = when (kind) {
        ButtonKind.PRIMARY, ButtonKind.GHOST -> null
        ButtonKind.DANGER -> BorderStroke(1.dp, accent.copy(alpha = 0.7f))
        ButtonKind.SECONDARY -> BorderStroke(1.dp, CccColors.LineBright)
    }
    Button(
        onClick = onClick,
        modifier = modifier.then(
            if (kind == ButtonKind.PRIMARY && enabled) Modifier.glowRect(accent, corner = 4.dp, spread = 9.dp, alpha = glowAlpha) else Modifier,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

// --- header -------------------------------------------------------------------------------------------------------

/** The HUD's square, framed icon button (back, bell, filters). */
@Composable
fun FrameIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(40.dp)
            .clip(shape)
            .background(CccColors.Surface, shape)
            .border(1.dp, CccColors.Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The top of a screen: a framed back button, the title in Chakra Petch with a line of data under it,
 * and the actions on the right. Replaces the Material app bar.
 */
@Composable
fun HudHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            FrameIconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = CccColors.Cyan) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = CccColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// --- loading ----------------------------------------------------------------------------------------------------

/** A shimmering placeholder block: what shows instead of content while ccc connects. */
@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val loops = effects().loops
    val shift = if (loops) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            -1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "shimmer-shift",
        ).value
    } else 0.5f
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.dp, CccColors.Line, shape)
            .drawBehind {
                drawRect(CccColors.Surface)
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, CccColors.Cyan.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(w * (shift - 0.3f), 0f),
                        end = Offset(w * (shift + 0.3f), size.height),
                    ),
                )
            },
    )
}

/** Entry: fades in and slides up a little, [index] × 30 ms after the first item of a list. Once per composition. */
@Composable
fun Modifier.staggeredEntry(index: Int, distance: Dp = 14.dp): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(10) * 30).toLong())
        progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance.toPx()
    }
}

// --- tabs ---------------------------------------------------------------------------------------------------------

/** A row of tabs in a frame: the chosen one is filled cyan. */
@Composable
fun HudTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(CccColors.Surface, shape)
            .border(1.dp, CccColors.Line, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val fill by androidx.compose.animation.animateColorAsState(if (on) CccColors.Cyan else Color.Transparent, tween(200), label = "tab-fill")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) CccColors.Background else CccColors.Dim, maxLines = 1)
            }
        }
    }
}

/** A spinner icon that turns; still when endless animations are off. */
@Composable
fun SpinningIcon(modifier: Modifier = Modifier, tint: Color = CccColors.Amber) {
    val angle = if (effects().loops) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin-angle",
        ).value
    } else 0f
    Icon(HudIcons.Spinner, null, modifier.graphicsLayer { rotationZ = angle }, tint = tint)
}
