package io.github.icenux.ccc.ui

import io.github.icenux.ccc.ui.theme.CccFonts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Task
import io.github.icenux.ccc.ui.theme.CccColors

@Composable
internal fun Bar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    HudHeader(title, subtitle, onBack) { actions() }
}

/** What the task queue screen can do. */
class TasksActions(
    val back: () -> Unit,
    val add: (String, String) -> Unit,
    val edit: (Int, String, String) -> Unit,
    val remove: (Int) -> Unit,
    val move: (Int, Int) -> Unit,
    val paused: (Boolean) -> Unit,
    val autoContinue: (Boolean) -> Unit,
)

/** The conversation's task queue (t in ccc): add, edit, reorder, remove; pause it, auto-continue. */
@Composable
fun TasksScreen(state: TasksState, name: String, snackbar: SnackbarHostState, actions: TasksActions) {
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    val queue = state.tasks
    Scaffold(
        topBar = { Bar(stringResource(R.string.tasks), name, actions.back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = CccColors.Claude, contentColor = Color.Black) {
                Icon(Icons.Filled.Add, stringResource(R.string.task_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (queue == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                return@LazyColumn
            }
            item {
                Column {
                    SwitchLine(stringResource(R.string.task_paused), queue.paused, actions.paused)
                    SwitchLine(stringResource(R.string.auto_continue), queue.autoContinue, actions.autoContinue)
                }
            }
            if (queue.tasks.isEmpty()) {
                item { Text(stringResource(R.string.tasks_empty), color = CccColors.Muted, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(queue.tasks, key = { _, task -> task.id }) { position, task ->
                TaskCard(position, task, last = position == queue.tasks.lastIndex, actions) { editing = task }
            }
        }
    }
    editing?.let { task ->
        TaskDialog(task, onClose = { editing = null }) { text, whenText ->
            actions.edit(task.id, text, whenText)
            editing = null
        }
    }
    if (adding) {
        TaskDialog(null, onClose = { adding = false }) { text, whenText ->
            actions.add(text, whenText)
            adding = false
        }
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TaskCard(position: Int, task: Task, last: Boolean, actions: TasksActions, onEdit: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = CccColors.Surface),
        border = BorderStroke(1.dp, CccColors.Line),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${position + 1}.", color = CccColors.Title, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(task.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                val notes = listOfNotNull(
                    shortTime(task.notBefore)?.let { stringResource(R.string.task_at, it) },
                    stringResource(R.string.task_by_routine).takeIf { task.byRoutine },
                )
                if (notes.isNotEmpty()) {
                    Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = CccColors.Waiting)
                }
            }
            Column {
                IconButton(onClick = { actions.move(task.id, -1) }, enabled = position > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                }
                IconButton(onClick = { actions.move(task.id, 1) }, enabled = !last) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                }
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                IconButton(onClick = { actions.remove(task.id) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = CccColors.Error)
                }
            }
        }
    }
}

/** A task's text and when it may start (empty: as soon as possible; HH:MM, YYYY-MM-DD HH:MM, reset). */
@Composable
private fun TaskDialog(task: Task?, onClose: () -> Unit = {}, onSave: (String, String) -> Unit) {
    var text by rememberSaveable(task?.id) { mutableStateOf(task?.text.orEmpty()) }
    var whenText by rememberSaveable(task?.id) {
        mutableStateOf(task?.notBefore?.take(16)?.replace('T', ' ').orEmpty())
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(if (task == null) R.string.task_add else R.string.task_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, keyboardOptions = SentenceKeyboard, maxLines = 10,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = whenText,
                    onValueChange = { whenText = it },
                    label = { Text(stringResource(R.string.task_when)) },
                    placeholder = { Text(stringResource(R.string.task_when_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim(), whenText.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
