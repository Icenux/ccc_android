package io.github.icenux.ccc.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Conversation
import io.github.icenux.ccc.ui.theme.CccColors

private val PURPLE = Color(0xFFA78BFA)

/** Colours for the conversation tags: the same conversation always gets the same one. */
private val TAGS = listOf(CccColors.Red, CccColors.Blue, PURPLE, CccColors.Amber, CccColors.Cyan, CccColors.Claude)

private fun tagColor(conversationId: Int): Color = TAGS[Math.floorMod(conversationId, TAGS.size)]

private val BAR_HEIGHTS = listOf(0.45f, 0.75f, 0.55f, 0.95f, 0.6f, 1f, 0.5f, 0.8f, 0.7f, 0.9f)

/** The Tasks tab: everything waiting in every conversation's queue, with filters. A tap opens that queue. */
@Composable
internal fun QueueTab(state: HomeState, actions: ConversationsActions) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val queue = state.queue
    val all = queue.orEmpty()
    val shown = when (filter) {
        1 -> all.filter { it.task.byRoutine }
        2 -> all.filter { !it.task.byRoutine }
        3 -> all.filter { it.paused }
        else -> all
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") { QueueSummary(all) }
        item(key = "filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.queue_all) + " ${all.size}",
                    stringResource(R.string.queue_routines) + " ${all.count { it.task.byRoutine }}",
                    stringResource(R.string.queue_manual) + " ${all.count { !it.task.byRoutine }}",
                    stringResource(R.string.queue_paused) + all.count { it.paused }.takeIf { it > 0 }?.let { " $it" }.orEmpty(),
                ).forEachIndexed { i, label ->
                    CccChip(label, color = if (filter == i) CccColors.Cyan else CccColors.Dim, filled = filter == i) { filter = i }
                }
            }
        }
        if (queue == null) items(3) { ShimmerBlock(height = 104.dp) }
        if (queue != null && shown.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.queue_empty), Modifier.fillMaxWidth().padding(32.dp), color = CccColors.Muted)
            }
        }
        items(shown, key = { "${it.conversationId}:${it.task.id}" }) { entry ->
            val index = all.indexOf(entry)
            TaskEntry(entry, number = index + 1, next = index == 0 && entry.task.notBefore == null && !entry.paused,
                Modifier.animateItem().staggeredEntry(index)) { actions.openTasks(entry.conversationId) }
        }
    }
}

@Composable
private fun QueueSummary(all: List<QueueEntry>) {
    val nextRun = all.firstNotNullOfOrNull { it.task.notBefore }?.let(::shortTime)
    val paused = all.isNotEmpty() && all.all { it.paused }
    HudPanel(accent = CccColors.LineBright) {
        Caption(stringResource(R.string.queue_caption))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rememberCountUp(all.size).toString(), style = MaterialTheme.typography.displayMedium, color = CccColors.Cyan, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.queue_waiting_word), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyLarge, color = CccColors.Dim)
                }
                Text(
                    listOfNotNull(nextRun?.let { stringResource(R.string.queue_next_run, it) },
                        stringResource(if (paused) R.string.queue_is_paused else R.string.queue_not_paused)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = CccColors.Faint,
                )
            }
            Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                all.take(BAR_HEIGHTS.size).forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .width(6.dp)
                            .fillMaxHeight(BAR_HEIGHTS[i])
                            .background(if (i == 0) CccColors.Cyan else CccColors.LineBright.copy(alpha = 0.55f), RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskEntry(entry: QueueEntry, number: Int, next: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val task = entry.task
    val scheduled = task.notBefore != null
    val statusColor = when {
        entry.paused -> CccColors.Amber
        else -> CccColors.Cyan
    }
    val status = stringResource(when {
        entry.paused -> R.string.queue_status_paused
        scheduled -> R.string.queue_status_scheduled
        else -> R.string.queue_status_queued
    })
    val time = shortTime(task.notBefore)
    HudPanel(modifier, brackets = false, accent = CccColors.Line, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$number", style = MaterialTheme.typography.labelMedium, color = CccColors.Faint)
            Spacer(Modifier.width(8.dp))
            GlowDot(statusColor, size = 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor, modifier = Modifier.weight(1f))
            Text(
                if (next) stringResource(R.string.queue_next) else time ?: "–",
                style = MaterialTheme.typography.labelMedium, color = CccColors.Faint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(task.text, style = MaterialTheme.typography.bodyLarge, color = CccColors.Text, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CccChip(entry.conversation, color = tagColor(entry.conversationId), dot = true, modifier = Modifier.widthIn(max = 150.dp))
            CccChip(
                stringResource(if (task.byRoutine) R.string.queue_from_routine_tag else R.string.queue_manual_tag),
                color = if (task.byRoutine) PURPLE else CccColors.Amber,
                dot = true,
            )
            Spacer(Modifier.weight(1f))
            when {
                scheduled && time != null -> CccChip(time, color = CccColors.Amber)
                next -> CccChip(stringResource(R.string.queue_next).uppercase(), color = CccColors.Red)
                else -> CccChip(stringResource(R.string.queue_later).uppercase(), color = CccColors.Dim)
            }
        }
    }
}

/** A task from the Tasks tab: for which conversation, what, and when it may start (empty: as soon as possible). */
@Composable
internal fun NewTaskDialog(conversations: List<Conversation>, onClose: () -> Unit, onAdd: (Int, String, String) -> Unit) {
    var id by rememberSaveable { mutableIntStateOf(conversations.firstOrNull()?.id ?: -1) }
    var text by rememberSaveable { mutableStateOf("") }
    var whenText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.task_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    stringResource(R.string.queue_for_conversation), id.toString(),
                    conversations.map { it.id.toString() to it.name },
                ) { id = it.toInt() }
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, keyboardOptions = SentenceKeyboard, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = whenText, onValueChange = { whenText = it }, singleLine = true,
                    label = { Text(stringResource(R.string.task_when)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(id, text.trim(), whenText.trim()); onClose() }, enabled = text.isNotBlank() && id >= 0) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
