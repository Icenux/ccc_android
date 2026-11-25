package io.github.icenux.ccc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Note
import io.github.icenux.ccc.ui.theme.CccColors

/** What the notes screen can do. */
class NotesActions(
    val back: () -> Unit,
    /** Filter the list: the path or the text has it ("" = all). */
    val search: (String) -> Unit,
    /** Open one note (the list has no text). */
    val open: (String) -> Unit,
    /** Start a note under this name (nothing exists on the computer until it is saved). */
    val create: (String) -> Unit,
    /** Leave the editor (it asks first when there is unsaved text). */
    val close: () -> Unit,
    /** Save a note with this text; the callback says whether it worked (the editor stays open when it did not). */
    val save: (Note, String, (Boolean) -> Unit) -> Unit,
    val rename: (Note, String) -> Unit,
    val delete: (Note) -> Unit,
)

/**
 * ccc's own notes (N in ccc): Markdown files in ccc's folder on the computer, shared with it, so what is written here
 * is in ccc's list a moment later and the other way round. The list on top, a note opens in an editor with a preview.
 */
@Composable
fun NotesScreen(state: NotesState, snackbar: SnackbarHostState, actions: NotesActions) {
    val open = state.open
    if (open != null) {
        NoteEditor(open, snackbar, actions)
        return
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf(state.query) }
    Scaffold(
        topBar = { Bar(stringResource(R.string.notes), null, actions.back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = CccColors.Waiting, contentColor = Color.Black) {
                Icon(Icons.Filled.Add, stringResource(R.string.note_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.notes_hint), style = MaterialTheme.typography.bodySmall, color = CccColors.Muted) }
            item {
                OutlinedTextField(query, { query = it; actions.search(it) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.notes_search)) })
            }
            val notes = state.notes
            if (notes == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                return@LazyColumn
            }
            if (notes.isEmpty()) {
                item {
                    Text(stringResource(if (query.isBlank()) R.string.notes_empty else R.string.notes_no_match),
                        color = CccColors.Muted, modifier = Modifier.padding(8.dp))
                }
            }
            items(notes, key = { it.path }) { note ->
                Card(
                    onClick = { actions.open(note.path) },
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CccColors.Surface),
                    border = BorderStroke(1.dp, CccColors.Line),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(note.title, color = CccColors.Title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text("${note.path} · ${note.modified.take(16).replace('T', ' ')}", style = MaterialTheme.typography.labelMedium,
                            color = CccColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (creating) {
        NoteNameDialog(
            title = stringResource(R.string.note_add),
            initial = "",
            onDismiss = { creating = false },
            onDone = { creating = false; actions.create(it) },
        )
    }
}

/** A name for a note: folders with / (ideas/api), the .md is added. */
@Composable
private fun NoteNameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.note_name)) }, supportingText = { Text(stringResource(R.string.note_name_hint)) })
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = Note.pathOf(name).isNotEmpty()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** One note: its text to write (or the rendered preview), save, rename, delete. */
@Composable
private fun NoteEditor(note: Note, snackbar: SnackbarHostState, actions: NotesActions) {
    var text by rememberSaveable(note.path, note.version) { mutableStateOf(note.content) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val dirty = text != note.content
    val leave = { if (dirty) discarding = true else actions.close() }
    BackHandler(onBack = leave)
    Scaffold(
        topBar = { Bar(note.title.ifEmpty { note.path }, note.path, leave) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { preview = !preview }) { Text(stringResource(if (preview) R.string.note_write else R.string.note_preview)) }
                TextButton(
                    onClick = { saving = true; actions.save(note, text) { saving = false } },
                    enabled = dirty && !saving && (text.isNotBlank() || !note.isNew),
                ) { Text(stringResource(R.string.save)) }
                TextButton(onClick = { renaming = true }, enabled = !dirty && !note.isNew) { Text(stringResource(R.string.note_rename)) }
                TextButton(onClick = { deleting = true }, enabled = !note.isNew) { Text(stringResource(R.string.delete), color = CccColors.Error) }
            }
            if (preview) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { MarkdownText(text) }
            } else {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f).fillMaxWidth(), keyboardOptions = SentenceKeyboard,
                    label = { Text(stringResource(R.string.note_text)) })
            }
        }
    }
    if (renaming) {
        NoteNameDialog(stringResource(R.string.note_rename), note.path.removeSuffix(".md"), { renaming = false }) {
            renaming = false
            actions.rename(note, it)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_text, note.path)) },
            confirmButton = { TextButton(onClick = { deleting = false; actions.delete(note) }) { Text(stringResource(R.string.delete), color = CccColors.Error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.note_discard_title)) },
            text = { Text(stringResource(R.string.note_discard_text)) },
            confirmButton = { TextButton(onClick = { discarding = false; actions.close() }) { Text(stringResource(R.string.note_discard), color = CccColors.Error) } },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
