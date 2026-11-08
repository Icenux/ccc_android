package io.github.icenux.ccc.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Goal
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.CccFonts
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Seconds since an ISO time from ccc (with or without an offset), or null when it can't be read. */
private fun secondsSince(iso: String?): Long? {
    iso ?: return null
    val start = runCatching { OffsetDateTime.parse(iso).toInstant() }
        .recoverCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }
        .getOrNull() ?: return null
    return Duration.between(start, Instant.now()).seconds.coerceAtLeast(0)
}

private fun clock(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun goalColor(goal: Goal): Color = when (goal.status) {
    "running" -> CccColors.Cyan
    "passed" -> CccColors.Claude
    "stopped" -> CccColors.Dim
    else -> CccColors.Red
}

/** The progress ring: it turns smoothly to the new value, with a glow on the arc. */
@Composable
private fun GoalRing(fraction: Float, text: String, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "goal-ring")
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(96.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(CccColors.Line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color.copy(alpha = 0.25f), -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(stroke + 6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, color = CccColors.Text, fontWeight = FontWeight.Bold)
    }
}

/** One step of the loop: done (check), running (turning spinner) or waiting (clock). */
private enum class Step { DONE, RUNNING, QUEUED }

@Composable
private fun StepRow(title: String, note: String, trailing: String, step: Step, modifier: Modifier = Modifier) {
    val running = step == Step.RUNNING
    Row(
        modifier
            .fillMaxWidth()
            .background(if (running) CccColors.Cyan.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(
                when (step) { Step.DONE -> CccColors.CyanSoft; Step.RUNNING -> CccColors.Amber.copy(alpha = 0.14f); Step.QUEUED -> CccColors.SurfaceHigh },
                RoundedCornerShape(5.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (step) {
                Step.DONE -> Icon(HudIcons.Check, null, Modifier.size(20.dp), tint = CccColors.Cyan)
                Step.RUNNING -> SpinningIcon(Modifier.size(20.dp), CccColors.Amber)
                Step.QUEUED -> Icon(HudIcons.Clock, null, Modifier.size(20.dp), tint = CccColors.Faint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (step == Step.QUEUED) CccColors.Dim else CccColors.Text)
            Text(note, style = MaterialTheme.typography.labelMedium, color = if (running) CccColors.Amber else CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = if (running) CccColors.Amber else CccColors.Faint)
    }
}

/** The check's output, a line at a time: what ccc reported for the last round. */
@Composable
private fun CheckOutput(text: String, following: Boolean) {
    val lines = remember(text) { text.lines().filter { it.isNotBlank() }.take(10) }
    HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowDot(CccColors.Cyan, size = 6.dp)
            Spacer(Modifier.width(8.dp))
            Caption(stringResource(R.string.goal_check_output), Modifier.weight(1f), color = CccColors.Cyan)
            if (following) Text(stringResource(R.string.goal_following), style = MaterialTheme.typography.labelSmall, color = CccColors.Faint)
        }
        Spacer(Modifier.height(8.dp))
        lines.forEachIndexed { i, line ->
            val shown = remember(text, i) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(text) {
                delay(i * 90L)
                shown.animateTo(1f, tween(200))
            }
            val color = when {
                Regex("(?i)fail|error|✖|✕").containsMatchIn(line) -> CccColors.Red
                Regex("(?i)pass|ok\\b|✓|✔").containsMatchIn(line) -> CccColors.Cyan
                else -> CccColors.Dim
            }
            Text(
                line.trimEnd(),
                Modifier.alpha(shown.value),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = CccFonts.Mono),
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The goal loop at a glance: the ring with the round, what the goal is, the rounds so far and the one running,
 * the check's output, Stop and Open chat.
 */
@Composable
internal fun GoalLoop(goal: Goal, name: String, active: Boolean, onStop: () -> Unit, onOpenChat: () -> Unit) {
    val running = goal.status == "running"
    val color = goalColor(goal)
    // The clock ticks while the loop runs.
    val now = remember { mutableLongStateOf(secondsSince(goal.createdAt) ?: 0L) }
    LaunchedEffect(goal.id, running) {
        now.longValue = secondsSince(goal.createdAt) ?: 0L
        while (running) {
            delay(1000)
            now.longValue = secondsSince(goal.createdAt) ?: 0L
        }
    }
    val fraction = if (goal.maxRounds <= 0) 0f else (goal.round - (if (running) 0.5f else 0f)) / goal.maxRounds
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HudPanel(accent = CccColors.LineBright, glow = running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(fraction, "${goal.round}/${goal.maxRounds}", color)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Caption(stringResource(R.string.goal))
                    Text(goal.goal, style = MaterialTheme.typography.titleMedium, color = CccColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.goal_round_of, goal.round, goal.maxRounds) + " · " + stringResource(R.string.goal_elapsed, clock(now.longValue)),
                        style = MaterialTheme.typography.labelMedium, color = CccColors.Dim,
                    )
                    if (goal.checkCommand.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        CccChip(goal.checkCommand, color = CccColors.Dim)
                    }
                }
            }
        }
        HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(0.dp)) {
            val past = (goal.round - 1).coerceAtLeast(0)
            var first = true
            for (r in 1..past) {
                if (!first) HorizontalDivider(color = CccColors.Line)
                first = false
                StepRow(stringResource(R.string.goal_round, r), stringResource(R.string.goal_round_done), "", Step.DONE)
            }
            if (!first) HorizontalDivider(color = CccColors.Line)
            StepRow(
                stringResource(R.string.goal_round, goal.round),
                if (running) goal.phaseLabel.ifBlank { stringResource(R.string.goal_running) } else goal.statusLabel,
                if (running) clock(now.longValue) else "",
                if (running) Step.RUNNING else Step.DONE,
            )
            if (running && goal.round < goal.maxRounds) {
                HorizontalDivider(color = CccColors.Line)
                StepRow(stringResource(R.string.goal_round, goal.round + 1), stringResource(R.string.goal_queued), "–", Step.QUEUED)
            }
        }
        if (goal.lastReason.isNotBlank()) CheckOutput(goal.lastReason, following = running)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CccButton(onClick = onStop, enabled = active, kind = ButtonKind.DANGER, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.stop))
            }
            CccButton(onClick = onOpenChat, kind = ButtonKind.SECONDARY, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.goal_open_chat))
            }
        }
        HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(CccColors.CyanSoft, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(initials(name), style = MaterialTheme.typography.titleSmall, color = CccColors.Cyan, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goal_conversation, name), style = MaterialTheme.typography.titleSmall, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    shortTime(goal.createdAt)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = CccColors.Faint) }
                }
                CccChip("goal #${goal.id}", color = CccColors.Cyan)
            }
        }
    }
}
