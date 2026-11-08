package io.github.icenux.ccc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Subagent
import io.github.icenux.ccc.data.SubagentOptions
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.CccFonts

/** What the agents screen can do; every change needs an admin device (ccc answers 403 to the others). */
class SubagentsActions(
    val back: () -> Unit,
    /** Show the agents of this folder ("" = only the user's). */
    val folder: (String) -> Unit,
    /** Open one agent whole (the list has no prompts). */
    val edit: (Subagent) -> Unit,
    val closeEdit: () -> Unit,
    val save: (Subagent) -> Unit,
    /** Create; the callback says whether it worked (the dialog stays open when it did not). */
    val create: (Subagent, (Boolean) -> Unit) -> Unit,
    val delete: (Subagent) -> Unit,
    /** Description, access key, ask Claude to write it (else an outline) -> the instructions, or null. */
    val prompt: (String, String, Boolean, (String?) -> Unit) -> Unit,
)

/** The name rule of a new agent (ccc checks it again): lowercase letters, digits and hyphens. */
internal val SUBAGENT_NAME = Regex("[a-z0-9][a-z0-9-]{0,63}")

private const val CUSTOM = "_custom"

/** "a, b" -> [a, b]; nothing -> null (every tool). */
internal fun toolsOf(text: String): List<String>? = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null }

/**
 * The Claude Code agents (A in ccc): helpers with one job that Claude calls by itself, kept as files in a project's
 * .claude/agents or in the user's. [folders] are the folders to show (path to label); "" is the user's agents only.
 */
@Composable
fun SubagentsScreen(
    state: SubagentsState,
    folders: List<Pair<String, String>>,
    snackbar: SnackbarHostState,
    actions: SubagentsActions,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subagent?>(null) }
    val mine = stringResource(R.string.subagents_only_mine)
    val choices = listOf("" to mine) + folders
    LaunchedEffect(Unit) { if (state.agents == null) actions.folder(state.path.ifEmpty { folders.firstOrNull()?.first.orEmpty() }) }
    Scaffold(
        topBar = { Bar(stringResource(R.string.subagents), null, actions.back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = CccColors.Claude, contentColor = Color.Black) {
                Icon(Icons.Filled.Add, stringResource(R.string.subagent_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.subagents_hint), style = MaterialTheme.typography.bodySmall, color = CccColors.Muted) }
            item { Picker(stringResource(R.string.subagents_folder), state.path, choices) { actions.folder(it) } }
            val agents = state.agents
            if (agents == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                return@LazyColumn
            }
            if (agents.isEmpty()) item { Text(stringResource(R.string.subagents_empty), color = CccColors.Muted, modifier = Modifier.padding(8.dp)) }
            items(agents, key = { "${it.scope}/${it.name}/${it.file}" }) { agent ->
                Section(agent.name) {
                    Text(
                        listOf(
                            stringResource(if (agent.scope == Subagent.SCOPE_PROJECT) R.string.subagent_scope_project else R.string.subagent_scope_user),
                            agent.model.ifEmpty { stringResource(R.string.subagent_model_session) },
                            agent.tools?.joinToString(", ") ?: stringResource(R.string.subagent_all_tools),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = CccColors.Muted,
                    )
                    Text(agent.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (agent.problem.isNotEmpty()) {
                        Text(stringResource(R.string.subagent_skipped, agent.problem), color = CccColors.Error, style = MaterialTheme.typography.bodySmall)
                    } else if (agent.overridden) {
                        Text(stringResource(R.string.subagent_overridden), color = CccColors.Waiting, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { actions.edit(agent) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { deleting = agent }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = CccColors.Error)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { agent ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.subagent_delete_title)) },
            text = { Text(stringResource(R.string.subagent_delete_text, agent.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; actions.delete(agent) }) { Text(stringResource(R.string.delete), color = CccColors.Error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.editing?.let { agent -> SubagentEditDialog(agent, state.options, actions) }
    if (creating) {
        SubagentCreateDialog(state, actions, onClose = { creating = false })
    }
}

/** Change an agent: its description, tools, model and instructions (the name and the rest of the file stay). */
@Composable
private fun SubagentEditDialog(agent: Subagent, options: SubagentOptions?, actions: SubagentsActions) {
    var description by rememberSaveable(agent.name, agent.version) { mutableStateOf(agent.description) }
    var tools by rememberSaveable(agent.name, agent.version) { mutableStateOf(agent.tools?.joinToString(", ").orEmpty()) }
    var model by rememberSaveable(agent.name, agent.version) { mutableStateOf(agent.model) }
    var prompt by rememberSaveable(agent.name, agent.version) { mutableStateOf(agent.prompt) }
    val session = stringResource(R.string.subagent_model_session)
    val models = (options?.models.orEmpty() + agent.model).distinct().map { it to it.ifEmpty { session } }
    AlertDialog(
        onDismissRequest = actions.closeEdit,
        title = { Text(agent.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), minLines = 2, keyboardOptions = SentenceKeyboard,
                        label = { Text(stringResource(R.string.subagent_description)) })
                }
                item {
                    OutlinedTextField(tools, { tools = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.subagent_tools)) }, placeholder = { Text("Read, Grep, Glob") })
                }
                item { Picker(stringResource(R.string.subagent_model), model, models) { model = it } }
                item {
                    OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), minLines = 6, keyboardOptions = SentenceKeyboard,
                        label = { Text(stringResource(R.string.subagent_prompt)) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { actions.save(agent.copy(description = description, tools = toolsOf(tools), model = model, prompt = prompt)) },
                enabled = description.isNotBlank() && prompt.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = actions.closeEdit) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * A new agent: from the user's own words (what it is for, may it change anything, how thorough; the instructions an
 * outline or written by Claude) or from a template. The file is only written when Create is pressed.
 */
@Composable
private fun SubagentCreateDialog(state: SubagentsState, actions: SubagentsActions, onClose: () -> Unit) {
    val options = state.options
    val hasFolder = state.path.isNotEmpty()
    var start by rememberSaveable { mutableStateOf(CUSTOM) }
    var name by rememberSaveable { mutableStateOf("my-agent") }
    var scope by rememberSaveable { mutableStateOf(if (hasFolder) Subagent.SCOPE_PROJECT else Subagent.SCOPE_USER) }
    var description by rememberSaveable { mutableStateOf("") }
    var access by rememberSaveable { mutableStateOf(options?.defaultAccess ?: "read") }
    var effort by rememberSaveable { mutableStateOf(options?.defaultEffort ?: "balanced") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val template = options?.templates?.firstOrNull { it.key == start }
    val custom = template == null
    val customLabel = stringResource(R.string.subagent_start_custom)
    val scopes = (if (hasFolder) listOf(Subagent.SCOPE_PROJECT to stringResource(R.string.subagent_scope_project_here)) else emptyList()) +
        (Subagent.SCOPE_USER to stringResource(R.string.subagent_scope_user_all))
    val tools = if (custom) options?.access?.firstOrNull { it.key == access }?.tools else template?.tools
    val model = if (custom) options?.effort?.firstOrNull { it.key == effort }?.model.orEmpty() else template?.model.orEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.subagent_add)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options == null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    return@LazyColumn
                }
                item {
                    Picker(stringResource(R.string.subagent_start), start, listOf(CUSTOM to customLabel) + options.templates.map { it.key to it.title }) { picked ->
                        start = picked
                        val chosen = options.templates.firstOrNull { it.key == picked }
                        if (chosen != null) {
                            description = chosen.description
                            prompt = chosen.prompt
                            if (name == "my-agent" || options.templates.any { it.key == name }) name = chosen.key
                        } else if (options.templates.any { it.key == name }) name = "my-agent"
                    }
                }
                item {
                    OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), minLines = 2, keyboardOptions = SentenceKeyboard,
                        label = { Text(stringResource(R.string.subagent_what_for)) })
                }
                if (custom) {
                    item { Picker(stringResource(R.string.subagent_access), access, options.access.map { it.key to it.label }) { access = it } }
                    item { Picker(stringResource(R.string.subagent_effort), effort, options.effort.map { it.key to it.label }) { effort = it } }
                } else {
                    item {
                        Text(stringResource(R.string.subagent_template_summary, template?.tools?.joinToString(", ").orEmpty(), template?.model.orEmpty()),
                            style = MaterialTheme.typography.labelMedium, color = CccColors.Muted)
                    }
                }
                item {
                    OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), minLines = 5, keyboardOptions = SentenceKeyboard,
                        label = { Text(stringResource(R.string.subagent_prompt)) })
                }
                if (custom) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { busy = true; actions.prompt(description, access, false) { text -> busy = false; if (text != null) prompt = text } },
                                enabled = !busy) { Text(stringResource(R.string.subagent_outline)) }
                            TextButton(onClick = { busy = true; actions.prompt(description, access, true) { text -> busy = false; if (text != null) prompt = text } },
                                enabled = !busy && description.isNotBlank()) { Text(stringResource(R.string.subagent_write_claude)) }
                        }
                    }
                    if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                item {
                    OutlinedTextField(name, { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' } }, Modifier.fillMaxWidth(), singleLine = true,
                        isError = !SUBAGENT_NAME.matches(name), label = { Text(stringResource(R.string.subagent_name)) },
                        supportingText = { Text(stringResource(R.string.subagent_name_hint), fontFamily = CccFonts.Mono) })
                }
                item { Picker(stringResource(R.string.subagent_kept_in), scope, scopes) { scope = it } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    busy = true
                    actions.create(Subagent(name = name, description = description, scope = scope, tools = tools, model = model, prompt = prompt)) { ok ->
                        busy = false
                        if (ok) onClose()
                    }
                },
                enabled = options != null && !busy && SUBAGENT_NAME.matches(name) && description.isNotBlank() && prompt.isNotBlank() && tools != null,
            ) { Text(stringResource(R.string.subagent_create)) }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}
