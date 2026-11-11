package io.github.icenux.ccc.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import kotlinx.coroutines.launch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import io.github.icenux.ccc.ui.theme.effects
import androidx.compose.foundation.layout.height

import io.github.icenux.ccc.ui.theme.CccFonts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.icenux.ccc.data.Elapsed
import io.github.icenux.ccc.data.Permission
import io.github.icenux.ccc.data.PromptPreset
import io.github.icenux.ccc.data.mentionAt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.ConversationStatus
import io.github.icenux.ccc.data.Message
import io.github.icenux.ccc.ui.theme.CccColors

private const val PREVIEW_LINES = 3

/** What a conversation can do, besides showing [ConversationState]. */
class ConversationActions(
    val back: () -> Unit,
    val send: (String, () -> Unit) -> Unit,
    val retry: () -> Unit,
    val loadOlder: () -> Unit,
    val decide: (Permission, String) -> Unit,
    val archive: (Boolean) -> Unit,
    val delete: () -> Unit,
    val setGroup: (String?) -> Unit,
    val export: () -> Unit,
    val usePreset: (PromptPreset, (String) -> Unit) -> Unit,
    val mention: (String?) -> Unit,
    val tasks: () -> Unit,
    val agents: () -> Unit,
    val terminal: () -> Unit,
    /** Answer Claude Code's question in the terminal with option n (null: Esc). */
    val answerPrompt: (io.github.icenux.ccc.data.TerminalPrompt, Int?) -> Unit = { _, _ -> },
    /** While Claude works: into the terminal at once instead of the queue (interactive conversations). */
    val sendNow: (String, () -> Unit) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: ConversationState,
    presets: List<PromptPreset>,
    ownGroups: List<String>,
    snackbar: SnackbarHostState,
    actions: ConversationActions,
) {
    val detail = state.detail
    var draft by rememberSaveable(state.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    // A "send at once" preset is only sent after this confirmation (a stray tap must not send).
    var confirmPreset by remember { mutableStateOf<PromptPreset?>(null) }
    val archived = detail?.archived == true
    // Claude is busy (and not stopped on an approval): the screen shows it moving.
    val working = detail?.state == ConversationStatus.RUNNING && detail.permissions.isEmpty() && state.prompt == null
    val list = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // reverseLayout: item 0 is the newest, at the bottom. New messages scroll into view only while the reader
    // is at the bottom; whoever scrolled up to read is left where they are. "At the bottom" is noted when a
    // scroll stops (a new item being inserted is not the reader scrolling).
    var atBottom by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { moving -> if (!moving) atBottom = !list.canScrollBackward }
    }
    val newest = state.messages.lastOrNull()?.index
    val queuedCount = detail?.queued?.size ?: 0
    val took = remember(state.messages, detail?.state) { Elapsed.turns(state.messages, running = detail?.state == ConversationStatus.RUNNING) }
    val since = remember(state.messages) { Elapsed.runningSince(state.messages) }
    LaunchedEffect(newest, queuedCount, working) {
        if (atBottom) list.animateScrollToItem(0)
    }
    Scaffold(
        topBar = {
          Column {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FrameIconButton(actions.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = CccColors.Cyan) }
                Column(Modifier.weight(1f)) {
                    Text(detail?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, color = CccColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    detail?.let {
                        val approval = it.permissions.isNotEmpty() || state.prompt != null
                        val color = statusColor(it.state, approval)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowDot(color, size = 7.dp, pulse = pulseOf(it.state, approval))
                            Spacer(Modifier.width(7.dp))
                            Text(statusLabel(it.state).uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
                        }
                    }
                }
                if (detail != null && detail.state in RETRYABLE && !archived) {
                    FrameIconButton(actions.retry) { Icon(Icons.Filled.Refresh, stringResource(R.string.retry), tint = CccColors.Cyan) }
                }
                if (detail != null) {
                    Box {
                        FrameIconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more), tint = CccColors.Dim) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (detail.deliveryMode == "interactive" || detail.deliveryMode == "remote_control") {
                                DropdownMenuItem(text = { Text(stringResource(R.string.terminal)) },
                                    onClick = { menu = false; actions.terminal() })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.tasks) + " (${detail.pending})") },
                                onClick = { menu = false; actions.tasks() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.agents)) },
                                onClick = { menu = false; actions.agents() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.group_put)) },
                                onClick = { menu = false; dialog = "group" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export)) },
                                onClick = { menu = false; actions.export() })
                            DropdownMenuItem(
                                text = { Text(stringResource(if (archived) R.string.restore else R.string.archive_it)) },
                                onClick = { menu = false; actions.archive(!archived) },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = CccColors.Error) },
                                onClick = { menu = false; dialog = "delete" })
                        }
                    }
                }
            }
          }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.bottomBarInsets().padding(8.dp)) {
                if (archived) {
                    CccButton(onClick = { actions.archive(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.restore))
                    }
                    return@Column
                }
                detail?.permissions?.forEach { permission ->
                    ApprovalCard(
                        permission,
                        detail.name,
                        onDecide = { actions.decide(permission, it) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                state.prompt?.let { prompt ->
                    PromptCard(prompt, actions.answerPrompt, actions.terminal, Modifier.padding(bottom = 8.dp))
                }
                Mentions(state.mentions) { path ->
                    val at = mentionAt(draft.text)?.first ?: return@Mentions
                    val text = draft.text.substring(0, at) + "@$path "
                    draft = TextFieldValue(text, TextRange(text.length))
                    actions.mention(null)
                }
                if (presets.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.forEach { preset ->
                            val shape = RoundedCornerShape(5.dp)
                            Row(
                                Modifier
                                    .clip(shape)
                                    .background(CccColors.Surface, shape)
                                    .border(1.dp, CccColors.LineBright, shape)
                                    .clickable {
                                        if (preset.sendNow) confirmPreset = preset else actions.usePreset(preset) { text ->
                                            val joined = if (draft.text.isBlank()) text else draft.text.trimEnd() + " " + text
                                            draft = TextFieldValue(joined, TextRange(joined.length))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (preset.sendNow) "↵" else "✎", color = CccColors.Cyan, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(8.dp))
                                Text(preset.name, color = CccColors.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                }
                if (working && draft.text.isNotBlank() && !state.sending &&
                    (detail?.deliveryMode == "interactive" || detail?.deliveryMode == "remote_control")) {
                    TextButton(
                        onClick = { actions.sendNow(draft.text) { draft = TextFieldValue("") } },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.send_immediately), color = CccColors.Amber) }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft,
                        keyboardOptions = SentenceKeyboard,
                        onValueChange = {
                            draft = it
                            actions.mention(mentionAt(it.text.substring(0, it.selection.end))?.second)
                        },
                        placeholder = { Text(stringResource(R.string.message_hint), color = CccColors.Faint) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp, max = 160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = CccColors.LineBright,
                            focusedBorderColor = CccColors.Cyan,
                            unfocusedContainerColor = CccColors.Surface,
                            focusedContainerColor = CccColors.Surface,
                        ),
                    )
                    val canSend = draft.text.isNotBlank() && !state.sending
                    val sendShape = RoundedCornerShape(6.dp)
                    Box(
                        Modifier
                            .size(56.dp)
                            .then(if (canSend) Modifier.glowRect(CccColors.Cyan, corner = 6.dp, spread = 8.dp, alpha = 0.4f) else Modifier)
                            .clip(sendShape)
                            .background(if (canSend) CccColors.Cyan else CccColors.SurfaceHigh, sendShape)
                            .clickable(enabled = canSend) { actions.send(draft.text) { draft = TextFieldValue("") } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.sending) CircularProgressIndicator(Modifier.size(22.dp), color = CccColors.Cyan, strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send), tint = if (canSend) CccColors.Background else CccColors.Faint)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = list,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            if (working) item(key = "working") { WorkingIndicator(since, Modifier.animateItem()) }
            detail?.queued?.reversed()?.let { queued ->
                items(queued, key = { "queued:${it.id}" }) { QueuedBubble(it.text, Modifier.animateItem()) }
            }
            items(state.messages.reversed(), key = { it.index }) { message ->
                val streaming = message.index == newest && message.kind == "assistant_text" && detail?.state == ConversationStatus.RUNNING
                MessageItem(message, streaming, took[message.index], Modifier.animateItem())
            }
            if (detail != null && state.messages.isEmpty() && detail.queued.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.no_messages), color = CccColors.Muted, modifier = Modifier.padding(16.dp))
                }
            }
            if (state.hasOlder) {
                item(key = "older") {
                    LaunchedEffect(state.messages.firstOrNull()?.index) { actions.loadOlder() }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = actions.loadOlder) { Text(stringResource(R.string.load_older)) }
                    }
                }
            }
            detail?.let { header ->
                item(key = "header") { Header(header) }
            }
        }
        // Scrolled up while news came in: a way back down.
        androidx.compose.animation.AnimatedVisibility(
            visible = !atBottom && !list.isScrollInProgress,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
        ) {
            FrameIconButton({ scope.launch { list.animateScrollToItem(0) } }) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.jump_to_newest), tint = CccColors.Cyan)
            }
        }
        }
    }
    confirmPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { confirmPreset = null },
            title = { Text(stringResource(R.string.preset_send_title, preset.name)) },
            text = { Text(preset.text) },
            confirmButton = {
                TextButton(onClick = { confirmPreset = null; actions.usePreset(preset) {} }) {
                    Text(stringResource(R.string.send))
                }
            },
            dismissButton = { TextButton(onClick = { confirmPreset = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    when (dialog) {
        "group" -> GroupPicker(detail?.group?.title, ownGroups, actions.setGroup) { dialog = null }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text, detail?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { dialog = null; actions.delete() }) {
                    Text(stringResource(R.string.delete), color = CccColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Mentions(files: List<String>, onPick: (String) -> Unit) {
    if (files.isEmpty()) return
    Card(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CccColors.Surface),
        border = BorderStroke(1.dp, CccColors.Line),
    ) {
        files.forEach { path ->
            Text(
                "@$path",
                Modifier.fillMaxWidth().clickable { onPick(path) }.padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = CccFonts.Mono,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Put the conversation into one of your own groups, a new one, or none. */
@Composable
private fun GroupPicker(current: String?, ownGroups: List<String>, onPick: (String?) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.group_put)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (listOf<String?>(null) + ownGroups).forEach { group ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(group); onClose() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = group == current, onClick = { onPick(group); onClose() })
                        Text(group ?: stringResource(R.string.group_none))
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_new)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(name.trim()); onClose() }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

private val RETRYABLE = setOf(ConversationStatus.BLOCKED_ERROR, ConversationStatus.PAUSED_LIMIT, ConversationStatus.WAITING_RETRY)

@Composable
private fun Header(detail: io.github.icenux.ccc.data.ConversationDetail) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(detail.path, style = MaterialTheme.typography.bodySmall, color = CccColors.Muted, fontFamily = CccFonts.Mono)
        detail.context?.let { context ->
            Text(
                stringResource(R.string.context_short, context.text) + context.sizes.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = contextColor(context),
            )
        }
        if (detail.flags.isNotEmpty()) {
            Text(detail.flags, style = MaterialTheme.typography.bodySmall, color = CccColors.Muted)
        }
        detail.remoteControlUrl?.let {
            Text("${stringResource(R.string.remote_control_link)}: $it", style = MaterialTheme.typography.bodySmall,
                color = CccColors.Title)
        }
        detail.lastError?.takeIf { detail.state == ConversationStatus.BLOCKED_ERROR }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = CccColors.Error)
        }
    }
}

@Composable
private fun MessageItem(message: Message, streaming: Boolean, took: Long?, modifier: Modifier) {
    when (message.kind) {
        "human" -> Bubble(stringResource(R.string.you), message.text, shortTime(message.time), CccColors.Human, modifier, end = true)
        "assistant_text" -> Bubble(stringResource(R.string.claude), message.text, shortTime(message.time), CccColors.Claude, modifier,
            markdown = true, cursor = streaming, took = took?.let { stringResource(R.string.took_for, Elapsed.text(it)) })
        else -> ToolLine(message, modifier)
    }
}

@Composable
private fun Bubble(
    who: String, text: String, time: String?, color: androidx.compose.ui.graphics.Color, modifier: Modifier, end: Boolean = false,
    markdown: Boolean = false, cursor: Boolean = false, took: String? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(if (end) 0.88f else 1f)
                .clip(shape)
                .background(CccColors.Surface, shape)
                .border(1.dp, color.copy(alpha = 0.9f), shape)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(who.uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = color)
                time?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = CccColors.Faint)
                }
                took?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("· $it", style = MaterialTheme.typography.labelMedium, color = CccColors.Faint)
                }
            }
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                if (markdown) MarkdownText(text) else Text(text, style = MaterialTheme.typography.bodyMedium, color = CccColors.Text)
            }
            if (cursor) BlinkingCursor(color)
        }
    }
}

/** The caret at the end of the reply Claude is still writing. */
@Composable
private fun BlinkingCursor(color: androidx.compose.ui.graphics.Color) {
    val on: State<Float>? = if (effects().loops) {
        rememberInfiniteTransition(label = "cursor").animateFloat(
            1f, 0f, infiniteRepeatable(androidx.compose.animation.core.keyframes { durationMillis = 1000; 1f at 0; 1f at 480; 0f at 500; 0f at 980 }, RepeatMode.Restart),
            label = "cursor-alpha",
        )
    } else null
    Box(Modifier.padding(top = 4.dp).size(width = 8.dp, height = 16.dp).graphicsLayer { alpha = on?.value ?: 1f }.background(color))
}

@Composable
private fun QueuedBubble(text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.fillMaxWidth(0.88f).clip(shape).background(CccColors.Background, shape).border(1.dp, CccColors.Line, shape).padding(12.dp),
        ) {
            Row {
                Text(stringResource(R.string.you).uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = CccColors.Dim)
                Spacer(Modifier.width(8.dp))
                Text("· " + stringResource(R.string.queued).uppercase(), style = MaterialTheme.typography.labelLarge, color = CccColors.Dim)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = CccColors.Dim)
        }
    }
}

/** A tool call or its result: one muted line, the whole of it on a tap. */
@Composable
private fun ToolLine(message: Message, modifier: Modifier) {
    var open by rememberSaveable(message.index) { mutableStateOf(false) }
    val call = message.kind == "tool_call"
    val text = if (open) message.detail ?: message.text else message.text
    Row(
        modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (call) "⚙" else "↳", color = CccColors.Faint, modifier = Modifier.width(24.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = CccFonts.Mono,
            color = CccColors.Dim,
            maxLines = if (open) Int.MAX_VALUE else PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** "Claude is working" with three dots that jump one after another — under the newest message. */
@Composable
private fun WorkingIndicator(since: java.time.Instant?, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(since) {
        while (since != null) {
            now = java.time.Instant.now()
            kotlinx.coroutines.delay(1000)
        }
    }
    val loops = effects().loops
    val transition = rememberInfiniteTransition(label = "working")
    Row(modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val level: State<Float>? = if (loops) {
                transition.animateFloat(
                    0.25f, 1f,
                    infiniteRepeatable(androidx.compose.animation.core.tween(600, delayMillis = i * 160, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "working-dot-$i",
                )
            } else null
            GlowDot(CccColors.Cyan, Modifier.graphicsLayer { alpha = level?.value ?: 1f }.padding(end = 6.dp), size = 7.dp)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (since != null && now > since) stringResource(R.string.claude_working_for, Elapsed.text(now.epochSecond - since.epochSecond)) else stringResource(R.string.claude_working),
            style = MaterialTheme.typography.labelMedium, color = CccColors.Cyan,
        )
    }
}


/** Claude Code asks something in its terminal: the question, a button per answer, Esc, and the terminal itself. */
@Composable
private fun PromptCard(prompt: io.github.icenux.ccc.data.TerminalPrompt, onAnswer: (io.github.icenux.ccc.data.TerminalPrompt, Int?) -> Unit, onTerminal: () -> Unit, modifier: Modifier) {
    HudPanel(modifier, accent = CccColors.Amber, brackets = false, glow = true, contentPadding = PaddingValues(14.dp)) {
        Text(stringResource(R.string.prompt_title), style = MaterialTheme.typography.titleSmall, color = CccColors.Amber)
        if (prompt.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .background(CccColors.Background, RoundedCornerShape(6.dp))
                    .border(1.dp, CccColors.Line, RoundedCornerShape(6.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(prompt.body, fontFamily = CccFonts.Mono, style = MaterialTheme.typography.bodySmall, color = CccColors.Cyan)
            }
        }
        if (prompt.question.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text((if (prompt.truncated) "… " else "") + prompt.question, style = MaterialTheme.typography.bodyMedium, color = CccColors.Text)
        }
        if (prompt.truncated) {
            Text(stringResource(R.string.prompt_truncated), style = MaterialTheme.typography.labelMedium, color = CccColors.Amber)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            prompt.options.forEach { option ->
                CccButton(
                    onClick = { onAnswer(prompt, option.number) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = if (option.selected) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                ) { Text("${option.number}  ${option.label}", maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            CccButton(onClick = onTerminal, kind = ButtonKind.GHOST) { Text(stringResource(R.string.terminal)) }
            CccButton(onClick = { onAnswer(prompt, null) }, kind = ButtonKind.GHOST, color = CccColors.Red) { Text(stringResource(R.string.prompt_cancel)) }
        }
    }
}
