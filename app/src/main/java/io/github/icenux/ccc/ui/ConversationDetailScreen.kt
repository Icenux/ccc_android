package io.github.icenux.ccc.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Conversation
import io.github.icenux.ccc.data.Permission
import io.github.icenux.ccc.data.Run
import io.github.icenux.ccc.data.shortPath
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.CccFonts

class DetailActions(
    val back: () -> Unit,
    val chat: () -> Unit,
    val queue: () -> Unit,
    val runs: () -> Unit,
    val goal: () -> Unit,
    val decide: (Permission, String) -> Unit,
)

/** A conversation at a glance: its state and numbers, what waits for you, the latest runs and what changed. */
@Composable
fun ConversationDetailScreen(
    conversation: Conversation?,
    permissions: List<Permission>,
    runs: RunsState?,
    snackbar: SnackbarHostState,
    actions: DetailActions,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HudHeader(conversation?.name.orEmpty(), conversation?.git?.branch, actions.back)
            if (conversation == null) {
                ShimmerBlock(Modifier.padding(16.dp), height = 120.dp)
                return@Column
            }
            val state = conversation.state
            val color = statusColor(state, conversation.needsApproval)
            val list = runs?.runs.orEmpty()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "hero") {
                    HudPanel(accent = CccColors.LineBright, glow = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(60.dp).background(CccColors.CyanSoft, RoundedCornerShape(6.dp))
                                    .border(1.dp, CccColors.LineBright, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(initials(conversation.name), style = MaterialTheme.typography.headlineSmall, color = CccColors.Blue, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(conversation.name, style = MaterialTheme.typography.titleLarge, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(shortPath(conversation.path), style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            CccChip(
                                (if (conversation.needsApproval) stringResource(R.string.needs_approval) else statusLabel(state)).uppercase(),
                                color = color, dot = true,
                            )
                        }
                    }
                }
                item(key = "stats") {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        val git = conversation.git
                        DetailStat(tokenText(conversation.tokensToday), stringResource(R.string.dashboard_tokens), Modifier.weight(1f))
                        Divider()
                        DetailStat(conversation.pending.toString(), stringResource(R.string.stat_queued), Modifier.weight(1f))
                        Divider()
                        DetailStat(runs?.runs?.size?.toString() ?: "–", stringResource(R.string.detail_runs), Modifier.weight(1f))
                        Divider()
                        conversation.context?.let { context ->
                            DetailStat(context.text, stringResource(R.string.detail_context), Modifier.weight(1f), contextColor(context))
                            Divider()
                        }
                        DetailStat(
                            git?.let { "+${it.added} −${it.removed}" } ?: "–",
                            stringResource(R.string.detail_changes), Modifier.weight(1f),
                        )
                    }
                }
                item(key = "tabs") { DetailTabs(actions) }
                permissions.forEach { permission ->
                    item(key = "permission:${permission.id}") {
                        ApprovalCard(permission, conversation.name, onDecide = { actions.decide(permission, it) })
                    }
                }
                conversation.goal?.let { goal ->
                    item(key = "goal") {
                        HudPanel(accent = CccColors.Line, brackets = false, onClick = actions.goal, contentPadding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Caption(stringResource(R.string.goal))
                                    Text(
                                        stringResource(R.string.goal_line, goal.icon, goal.round, goal.maxRounds, if (goal.running) goal.phaseLabel else goal.statusLabel),
                                        style = MaterialTheme.typography.titleSmall, color = CccColors.Text,
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CccColors.Faint)
                            }
                        }
                    }
                }
                item(key = "runs-heading") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.runs), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = CccColors.Text)
                        Text(
                            stringResource(R.string.detail_view_all),
                            Modifier.clickable(onClick = actions.runs).padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = CccColors.Cyan,
                        )
                    }
                }
                item(key = "runs") {
                    when {
                        runs?.runs == null -> ShimmerBlock(height = 120.dp)
                        list.isEmpty() -> Text(stringResource(R.string.runs_empty), color = CccColors.Muted, modifier = Modifier.padding(8.dp))
                        else -> HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(0.dp)) {
                            list.take(3).forEachIndexed { i, run ->
                                if (i > 0) HorizontalDivider(color = CccColors.Line)
                                RunLine(run, Modifier.clickable(onClick = actions.runs))
                            }
                        }
                    }
                }
                conversation.git?.takeIf { it.added > 0 || it.removed > 0 || it.branch != null }?.let { git ->
                    item(key = "changes") {
                        HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Caption(stringResource(R.string.detail_changes))
                                    Text(git.branch.orEmpty(), style = MaterialTheme.typography.titleSmall, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("+${git.added}", style = MaterialTheme.typography.labelLarge, color = CccColors.Cyan)
                                Spacer(Modifier.width(10.dp))
                                Text("−${git.removed}", style = MaterialTheme.typography.labelLarge, color = CccColors.Red)
                                val sync = listOfNotNull(git.ahead?.takeIf { it > 0 }?.let { "↑$it" }, git.behind?.takeIf { it > 0 }?.let { "↓$it" })
                                if (sync.isNotEmpty()) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(sync.joinToString(" "), style = MaterialTheme.typography.labelLarge, color = CccColors.Dim)
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(CccColors.Line))
}

@Composable
private fun DetailStat(value: String, label: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = CccColors.Text) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Chat | Queue | Runs: the first is lit (this overview is the chat's front page); the others go to their screens. */
@Composable
private fun DetailTabs(actions: DetailActions) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).background(CccColors.Surface, shape).border(1.dp, CccColors.Line, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            Triple(R.string.nav_chats, true, actions.chat),
            Triple(R.string.detail_tab_queue, false, actions.queue),
            Triple(R.string.detail_tab_runs, false, actions.runs),
        ).forEach { (label, on, go) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (on) CccColors.Cyan else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(onClick = go),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (on) CccColors.Background else CccColors.Dim)
            }
        }
    }
}

@Composable
private fun RunLine(run: Run, modifier: Modifier) {
    val (mark, color) = when (run.status) {
        "success" -> "✓" to (if (run.revertedBy != null) CccColors.Blue else CccColors.Cyan)
        "rate_limited" -> "!" to CccColors.Amber
        else -> "✕" to CccColors.Red
    }
    Row(modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(mark, color = color, style = MaterialTheme.typography.titleMedium, fontFamily = CccFonts.Mono) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(run.task.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = CccColors.Text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(run.commit?.take(7), shortTime(run.finishedAt ?: run.startedAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1,
            )
        }
    }
}
