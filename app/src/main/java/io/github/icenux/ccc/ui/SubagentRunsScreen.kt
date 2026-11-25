package io.github.icenux.ccc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Elapsed
import io.github.icenux.ccc.data.SubagentRun
import io.github.icenux.ccc.data.SubagentRuns
import io.github.icenux.ccc.data.SubagentStep
import io.github.icenux.ccc.data.SubagentTimeline
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.CccFonts
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The colour and the mark of a run's state: green done, red failed or cut off, amber running. */
private fun runColor(run: SubagentRun): Color = when {
    run.running -> CccColors.Amber
    run.failed -> CccColors.Red
    else -> CccColors.Claude
}

private fun runMark(run: SubagentRun): String = when {
    run.running -> "●"
    run.failed -> "✗"
    else -> "✓"
}

@Composable
private fun runStateText(run: SubagentRun): String = stringResource(
    when (run.status) {
        "running" -> R.string.run_running
        "error" -> R.string.run_error
        "stopped" -> R.string.run_stopped
        else -> R.string.run_done
    },
)

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun startText(run: SubagentRun): String = Elapsed.instant(run.started)?.let { clock.format(it) } ?: "–"

/** The run's bar on the common axis: a thin track with the coloured stretch where it worked. */
@Composable
private fun RunBar(run: SubagentRun, bar: io.github.icenux.ccc.data.Bar?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CccColors.Line)) {
        if (bar != null) {
            Row(Modifier.fillMaxSize()) {
                if (bar.from > 0f) Spacer(Modifier.weight(bar.from))
                Box(Modifier.weight((bar.to - bar.from).coerceAtLeast(0.01f)).fillMaxHeight().background(runColor(run), RoundedCornerShape(3.dp)))
                if (bar.to < 1f) Spacer(Modifier.weight(1f - bar.to))
            }
        }
    }
}

@Composable
private fun StateBadge(run: SubagentRun, size: Int = 34) {
    val color = runColor(run)
    Box(
        Modifier.size(size.dp).background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(runMark(run), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

/** One run as a card: state, name, what it did, how long and how many tokens, its bar; a nested one is indented. */
@Composable
private fun RunCard(run: SubagentRun, bar: io.github.icenux.ccc.data.Bar?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = ((run.depth - 1).coerceIn(0, 4) * 18).dp)) {
        if (run.depth > 1) {
            Box(Modifier.padding(end = 6.dp).width(2.dp).height(48.dp).background(CccColors.Line))
        }
        HudPanel(
            Modifier.weight(1f),
            accent = if (run.running) CccColors.Amber else CccColors.Line,
            brackets = false,
            onClick = onClick,
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateBadge(run)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(run.description.ifBlank { run.kind }, style = MaterialTheme.typography.titleSmall, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${run.kind} · ${run.toolCount} ${stringResource(R.string.run_tools)}",
                        style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(SubagentTimeline.duration(run.durationMs), style = MaterialTheme.typography.labelLarge, color = runColor(run), fontFamily = CccFonts.Mono)
                    Text(SubagentTimeline.tokens(run.tokens), style = MaterialTheme.typography.labelSmall, color = CccColors.Faint, fontFamily = CccFonts.Mono)
                }
            }
            Spacer(Modifier.height(8.dp))
            RunBar(run, bar)
        }
    }
}

@Composable
private fun LiveChip() = CccChip(stringResource(R.string.runs_live), color = CccColors.Amber, dot = true)

@Composable
private fun SummaryRow(runs: SubagentRuns) {
    val s = runs.summary
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        SumStat(s.total.toString(), stringResource(R.string.runs_sum_total), Modifier.weight(1f))
        SumStat(s.running.toString(), stringResource(R.string.runs_sum_running), Modifier.weight(1f), if (s.running > 0) CccColors.Amber else CccColors.Text)
        SumStat(s.errors.toString(), stringResource(R.string.runs_sum_errors), Modifier.weight(1f), if (s.errors > 0) CccColors.Red else CccColors.Text)
        SumStat(SubagentTimeline.duration(s.spanMs), stringResource(R.string.runs_sum_time), Modifier.weight(1.3f))
        SumStat(SubagentTimeline.tokens(s.tokens), stringResource(R.string.runs_sum_tokens), Modifier.weight(1f))
    }
}

@Composable
private fun SumStat(value: String, label: String, modifier: Modifier, color: Color = CccColors.Text) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Every subagent run of the conversation on one timeline; a running one makes the screen live. */
@Composable
fun SubagentRunsScreen(conversationName: String, state: RunsState?, onBack: () -> Unit, onOpen: (SubagentRun) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HudHeader(stringResource(R.string.runs_title), conversationName, onBack, actions = {
            if ((state?.runs?.summary?.running ?: 0) > 0) LiveChip()
        })
        val runs = state?.runs
        when {
            runs == null && state?.failed == true -> Text(stringResource(R.string.runs_unavailable), color = CccColors.Muted, modifier = Modifier.padding(16.dp))
            runs == null -> ShimmerBlock(Modifier.padding(16.dp), height = 120.dp)
            runs.runs.isEmpty() -> Text(stringResource(R.string.runs_empty), color = CccColors.Muted, modifier = Modifier.padding(16.dp))
            else -> {
                val ordered = remember(runs) { SubagentTimeline.treeOrder(runs.runs) }
                val bars = remember(runs) { SubagentTimeline.bars(runs.runs) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "summary") { SummaryRow(runs) }
                    items(ordered, key = { "run:${it.id}" }) { run -> RunCard(run, bars[run.id], { onOpen(run) }) }
                }
            }
        }
    }
}

/** The conversation detail's section: the latest three runs with a mini bar, "Show all" opens [SubagentRunsScreen]. */
fun LazyListScope.subagentsSection(runs: SubagentRuns?, onShowAll: () -> Unit, onOpen: (SubagentRun) -> Unit) {
    if (runs == null || runs.runs.isEmpty()) return
    val latest = runs.runs.takeLast(3).reversed()
    val bars = SubagentTimeline.bars(runs.runs)
    item(key = "subagents-head") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caption(stringResource(R.string.runs_title), Modifier.weight(1f))
            if (runs.summary.running > 0) { LiveChip(); Spacer(Modifier.width(10.dp)) }
            Text(stringResource(R.string.runs_show_all), style = MaterialTheme.typography.labelLarge, color = CccColors.Cyan, modifier = Modifier.clickable(onClick = onShowAll))
        }
    }
    items(latest, key = { "subagents-run:${it.id}" }) { run -> RunCard(run.copy(depth = 1), bars[run.id], { onOpen(run) }) }
}

/** One run: its state, the Prompt it was given, the Steps it took and its Result. */
@Composable
fun SubagentRunScreen(conversationName: String, state: RunsState?, onBack: () -> Unit, onOpenRun: (String) -> Unit) {
    val run = state?.run ?: state?.runs?.runs?.firstOrNull { it.id == state.runId }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        HudHeader(run?.description?.ifBlank { run.kind } ?: stringResource(R.string.runs_title), conversationName, onBack, actions = {
            if (run?.running == true) LiveChip()
        })
        if (run == null) {
            ShimmerBlock(Modifier.padding(16.dp), height = 120.dp)
            return@Column
        }
        val parent = state?.runs?.runs?.firstOrNull { it.id == run.parentId }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "state") {
                HudPanel(accent = runColor(run), glow = run.running, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateBadge(run, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(runStateText(run).uppercase(), color = runColor(run), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.run_by, parent?.description?.ifBlank { parent.kind } ?: stringResource(R.string.run_by_conversation)),
                                style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Fact(startText(run), stringResource(R.string.run_started), Modifier.weight(1f))
                        Fact(SubagentTimeline.duration(run.durationMs), stringResource(R.string.run_took), Modifier.weight(1f))
                        Fact(run.toolCount.toString(), stringResource(R.string.run_tools), Modifier.weight(0.8f))
                        Fact(SubagentTimeline.tokens(run.tokens), stringResource(R.string.run_tokens), Modifier.weight(0.9f))
                    }
                    if (run.model.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("${stringResource(R.string.run_model)}: ${run.model}", style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, fontFamily = CccFonts.Mono)
                    }
                }
            }
            item(key = "tabs") {
                Segments(listOf(R.string.run_tab_prompt, R.string.run_tab_steps, R.string.run_tab_result), tab) { tab = it }
            }
            when (tab) {
                0 -> item(key = "prompt") { TextBlock(run.prompt.ifBlank { "–" }) }
                2 -> item(key = "result") { TextBlock(run.result.ifBlank { stringResource(R.string.run_no_result) }) }
                else -> {
                    if (run.steps.isEmpty()) item(key = "no-steps") { Text(stringResource(R.string.run_no_steps), color = CccColors.Muted) }
                    items(run.steps, key = { "step:${it.index}" }) { step -> StepRow(step, onOpenRun) }
                }
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = CccColors.Text, fontFamily = CccFonts.Mono, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CccColors.Faint, maxLines = 1)
    }
}

@Composable
private fun Segments(labels: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(shape).background(CccColors.Surface, shape).border(1.dp, CccColors.Line, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (on) CccColors.Cyan else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (on) CccColors.Background else CccColors.Dim) }
        }
    }
}

@Composable
private fun TextBlock(text: String) {
    HudPanel(accent = CccColors.Line, brackets = false, contentPadding = PaddingValues(14.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CccColors.Text)
    }
}

/** A step: when (since the start), the tool, its argument and how long; an error in red, an `Agent` step leads on. */
@Composable
private fun StepRow(step: SubagentStep, onOpenRun: (String) -> Unit) {
    val nested = step.agentId.isNotEmpty()
    val color = when {
        step.error.isNotEmpty() -> CccColors.Red
        nested -> CccColors.Amber
        else -> CccColors.Text
    }
    HudPanel(
        accent = if (step.error.isNotEmpty()) CccColors.Red else CccColors.Line,
        brackets = false,
        onClick = if (nested) ({ onOpenRun(step.agentId) }) else null,
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("+${SubagentTimeline.duration(step.offsetMs)}", style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, fontFamily = CccFonts.Mono, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text(step.tool, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
                if (step.arg.isNotEmpty()) Text(step.arg, style = MaterialTheme.typography.labelMedium, color = CccColors.Dim, fontFamily = CccFonts.Mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (step.error.isNotEmpty()) Text(step.error, style = MaterialTheme.typography.labelMedium, color = CccColors.Red, fontFamily = CccFonts.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nested) Text(stringResource(R.string.run_step_nested), style = MaterialTheme.typography.labelMedium, color = CccColors.Amber)
            }
            Text(step.durationMs?.let { SubagentTimeline.duration(it) } ?: "…", style = MaterialTheme.typography.labelMedium, color = CccColors.Dim, fontFamily = CccFonts.Mono)
        }
    }
}
