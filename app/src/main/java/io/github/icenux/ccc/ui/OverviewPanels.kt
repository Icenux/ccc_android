package io.github.icenux.ccc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Overview
import io.github.icenux.ccc.data.OverviewChanges
import io.github.icenux.ccc.data.OverviewErrors
import io.github.icenux.ccc.data.OverviewNow
import io.github.icenux.ccc.data.OverviewUsage
import io.github.icenux.ccc.data.overviewTime
import io.github.icenux.ccc.ui.theme.CccColors

/** The files the Changes panel lists before it says "and more". */
private const val FILES_SHOWN = 40

private fun stateColor(state: String): Color = when (state) {
    "working" -> CccColors.Cyan
    "waiting_approval", "waiting_limit", "paused_limit" -> CccColors.Amber
    "blocked" -> CccColors.Red
    else -> CccColors.Dim
}

@Composable
private fun kindLabel(kind: String): String = when (kind) {
    "human" -> stringResource(R.string.overview_kind_you)
    "assistant_text" -> "Claude"
    "tool_call" -> stringResource(R.string.overview_kind_tool)
    "tool_result" -> stringResource(R.string.overview_kind_result)
    else -> kind
}

/** What Claude does now: the state, what waits in the queue and the last thing that happened. */
@Composable
fun OverviewNowPanel(now: OverviewNow) {
    HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(14.dp)) {
        Caption(stringResource(R.string.overview_now))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CccChip(now.stateLabel.uppercase(), color = stateColor(now.state), dot = true)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.overview_queued, now.pending), style = MaterialTheme.typography.labelLarge, color = CccColors.Dim)
        }
        now.nextRetryAt?.let {
            Text(stringResource(R.string.overview_next_retry, overviewTime(it)), style = MaterialTheme.typography.labelMedium, color = CccColors.Amber)
        }
        Spacer(Modifier.height(8.dp))
        val event = now.lastEvent
        if (event == null) {
            Text(stringResource(R.string.overview_no_activity), style = MaterialTheme.typography.bodySmall, color = CccColors.Faint)
        } else {
            Text(
                "${overviewTime(event.time)} · ${kindLabel(event.kind)}",
                style = MaterialTheme.typography.labelMedium, color = CccColors.Faint,
            )
            Text(event.text, style = MaterialTheme.typography.bodySmall, color = CccColors.Text, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The branch and the files with uncommitted changes; null [changes]: ccc may not read git there. */
@Composable
fun OverviewChangesPanel(changes: OverviewChanges?) {
    HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(14.dp)) {
        Caption(stringResource(R.string.detail_changes))
        Spacer(Modifier.height(6.dp))
        if (changes == null) {
            Text(stringResource(R.string.overview_no_git), style = MaterialTheme.typography.bodySmall, color = CccColors.Faint)
            return@HudPanel
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                changes.branch ?: "–", Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("+${changes.added}", style = MaterialTheme.typography.labelLarge, color = CccColors.Cyan)
            Spacer(Modifier.width(10.dp))
            Text("−${changes.removed}", style = MaterialTheme.typography.labelLarge, color = CccColors.Red)
            val sync = listOfNotNull(changes.ahead?.takeIf { it > 0 }?.let { "↑$it" }, changes.behind?.takeIf { it > 0 }?.let { "↓$it" })
            if (sync.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(sync.joinToString(" "), style = MaterialTheme.typography.labelLarge, color = CccColors.Dim)
            }
        }
        if (changes.files.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.overview_no_changes), style = MaterialTheme.typography.bodySmall, color = CccColors.Faint)
            return@HudPanel
        }
        Spacer(Modifier.height(8.dp))
        changes.files.take(FILES_SHOWN).forEach { file ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.path, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = CccColors.Text,
                    maxLines = 1, overflow = TextOverflow.StartEllipsis,
                )
                Spacer(Modifier.width(8.dp))
                when {
                    file.status == "new" -> Text(stringResource(R.string.overview_file_new), style = MaterialTheme.typography.labelSmall, color = CccColors.Cyan)
                    file.added == null -> Text(stringResource(R.string.overview_file_binary), style = MaterialTheme.typography.labelSmall, color = CccColors.Faint)
                    else -> Text("+${file.added} −${file.removed}", style = MaterialTheme.typography.labelSmall, color = CccColors.Dim)
                }
            }
        }
        if (changes.files.size > FILES_SHOWN || changes.truncated) {
            Text(stringResource(R.string.overview_more_files), style = MaterialTheme.typography.labelSmall, color = CccColors.Faint)
        }
    }
}

/** The tokens of the last days as bars, today's and all-time numbers and the models used. */
@Composable
fun OverviewUsagePanel(usage: OverviewUsage) {
    HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(14.dp)) {
        Caption(stringResource(R.string.overview_usage))
        Spacer(Modifier.height(8.dp))
        val top = usage.days.maxOfOrNull { it.tokens } ?: 0L
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            usage.days.forEach { day ->
                val share = if (top > 0) day.tokens.toFloat() / top else 0f
                Box(
                    Modifier.weight(1f).fillMaxHeight(share.coerceAtLeast(0.04f))
                        .background(if (day.tokens > 0) CccColors.Cyan else CccColors.Line, RoundedCornerShape(2.dp)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.overview_usage_totals, tokenText(usage.today), tokenText(usage.total)),
            style = MaterialTheme.typography.labelMedium, color = CccColors.Dim,
        )
        usage.models.forEach { model ->
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(model.model, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tokenText(model.tokens), style = MaterialTheme.typography.bodySmall, color = CccColors.Dim)
            }
        }
    }
}

/** What blocks the conversation now and the latest runs that failed. */
@Composable
fun OverviewErrorsPanel(errors: OverviewErrors) {
    HudPanel(brackets = false, accent = if (errors.current != null) CccColors.Red else CccColors.Line, contentPadding = PaddingValues(14.dp)) {
        Caption(stringResource(R.string.overview_errors))
        Spacer(Modifier.height(6.dp))
        errors.current?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CccColors.Red, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        }
        if (errors.recent.isEmpty()) {
            Text(stringResource(R.string.overview_no_errors), style = MaterialTheme.typography.bodySmall, color = CccColors.Faint)
            return@HudPanel
        }
        Text(stringResource(R.string.overview_failed, errors.count, errors.lastDays), style = MaterialTheme.typography.labelMedium, color = CccColors.Faint)
        errors.recent.forEach { run ->
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("${overviewTime(run.time)} · ${run.status}", style = MaterialTheme.typography.labelSmall, color = CccColors.Faint)
                Text(run.text, style = MaterialTheme.typography.bodySmall, color = CccColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The four panels, in the order of the TUI's queue detail. */
fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(overview: Overview) {
    item(key = "overview-now") { OverviewNowPanel(overview.now) }
    item(key = "overview-changes") { OverviewChangesPanel(overview.changes) }
    item(key = "overview-usage") { OverviewUsagePanel(overview.usage) }
    item(key = "overview-errors") { OverviewErrorsPanel(overview.errors) }
}
