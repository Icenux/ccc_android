package io.github.icenux.ccc.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import io.github.icenux.ccc.data.GitState
import io.github.icenux.ccc.ui.theme.CccFonts
import io.github.icenux.ccc.ui.theme.effects
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.icenux.ccc.data.LiveSession

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.icenux.ccc.data.AppRelease
import io.github.icenux.ccc.data.Conversation
import io.github.icenux.ccc.data.ConversationStatus
import io.github.icenux.ccc.data.Group
import io.github.icenux.ccc.data.ListDetail
import io.github.icenux.ccc.data.ListFilter
import io.github.icenux.ccc.data.UpdateInfo
import io.github.icenux.ccc.data.shortPath
import io.github.icenux.ccc.ui.theme.CccColors

/** What the list can do, besides showing [HomeState]. */
class ConversationsActions(
    val open: (Int) -> Unit,
    val approvals: () -> Unit,
    val settings: () -> Unit,
    val new: () -> Unit,
    val refresh: () -> Unit,
    val search: () -> Unit,
    val tab: (HomeTab) -> Unit,
    val filter: (String) -> Unit,
    val filterState: (Char) -> Unit,
    val nextWaiting: () -> Unit,
    val renameGroup: (String, String) -> Unit,
    val deleteGroup: (String) -> Unit,
    val routines: () -> Unit,
    val analytics: () -> Unit,
    /** A prompt for the live session (id, text). */
    val promptSession: (String, String) -> Unit = { _, _ -> },
    val applyUpdate: () -> Unit = {},
    val restartCcc: () -> Unit = {},
    /** The long-press menu of a card: archive (true) or restore (false), delete. */
    val archive: (Int, Boolean) -> Unit = { _, _ -> },
    val delete: (Int) -> Unit = {},
    val updateApp: () -> Unit = {},
    /** A Dashboard row: the conversation's overview. */
    val openQueue: (Int) -> Unit = {},
    /** A waiting task: its conversation's queue. */
    val openTasks: (Int) -> Unit = {},
    /** A new task for a conversation (id, text, when). */
    val addTask: (Int, String, String) -> Unit = { _, _, _ -> },
)

private val MARKERS = listOf(ListFilter.WORKING, ListFilter.APPROVAL, ListFilter.ERROR, ListFilter.UNSEEN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    state: HomeState,
    listDetails: Set<String>,
    newVersion: AppRelease? = null,
    snackbar: SnackbarHostState,
    actions: ConversationsActions,
    sessions: List<LiveSession>? = null,
    appUpdate: AppUpdateState = AppUpdateState(),
) {
    var groupMenu by remember { mutableStateOf<String?>(null) }
    var newTask by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf<Conversation?>(null) }
    val onCardMenu: (Conversation) -> Unit = { cardMenu = it }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            when (state.tab) {
                HomeTab.DASHBOARD -> DashboardHeader(state, actions)
                HomeTab.CONVERSATIONS, HomeTab.ARCHIVE -> HudHeader(
                    stringResource(if (state.tab == HomeTab.ARCHIVE) R.string.archive else R.string.conversations),
                    stringResource(R.string.conversations_count, if (state.tab == HomeTab.ARCHIVE) state.archived.size else state.conversations.size),
                ) {
                    FrameIconButton(actions.search) { Icon(Icons.Filled.Search, stringResource(R.string.search), tint = CccColors.Cyan) }
                    ApprovalsBell(state, actions)
                }
                HomeTab.TASKS -> QueueHeader(state) { actions.tab(HomeTab.DASHBOARD) }
                HomeTab.SESSIONS -> HudHeader(stringResource(R.string.live_sessions), sessions?.let { stringResource(R.string.sessions_count, it.size) })
            }
            OfflineBanner(state)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.tab) {
                    HomeTab.DASHBOARD -> Dashboard(state, listDetails, newVersion, actions, appUpdate, onCardMenu)
                    HomeTab.SESSIONS -> LiveSessionsList(sessions, actions.promptSession)
                    HomeTab.TASKS -> QueueTab(state, actions)
                    else -> ConversationList(state, listDetails, newVersion, actions, appUpdate, onCardMenu) { groupMenu = it }
                }
            }
        }
        if (state.tab != HomeTab.ARCHIVE && state.tab != HomeTab.SESSIONS) {
            val tasks = state.tab == HomeTab.TASKS
            CccButton(
                onClick = { if (tasks) newTask = true else actions.new() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp).height(50.dp),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (tasks) R.string.queue_new_task else R.string.new_conversation), style = MaterialTheme.typography.titleSmall)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    groupMenu?.let { name -> GroupDialog(name, actions, onClose = { groupMenu = null }) }
    cardMenu?.let { conversation -> CardMenuDialog(conversation, actions, onClose = { cardMenu = null }) }
    if (newTask) NewTaskDialog(state.conversations, onClose = { newTask = false }, onAdd = actions.addTask)
}

@Composable
private fun ApprovalsBell(state: HomeState, actions: ConversationsActions) {
    FrameIconButton(actions.approvals) {
        BadgedBox(badge = {
            if (state.permissions.isNotEmpty()) Badge(containerColor = CccColors.Red) { Text(state.permissions.size.toString()) }
        }) { Icon(Icons.Filled.Notifications, stringResource(R.string.approvals), tint = CccColors.Dim) }
    }
}

/** Under the header: not connected — retrying…, with dots that run. */
@Composable
private fun OfflineBanner(state: HomeState) {
    if (state.live || !state.loaded) return
    val loops = effects().loops
    val step = if (loops) {
        rememberInfiniteTransition(label = "dots").animateFloat(
            0f, 4f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "dots-step",
        ).value.toInt()
    } else 3
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CccColors.Amber.copy(alpha = 0.10f))
            .border(1.dp, CccColors.Amber.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowDot(CccColors.Amber, pulse = Pulse.BLINK)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.offline), style = MaterialTheme.typography.labelMedium, color = CccColors.Amber)
        Text(".".repeat(step.coerceIn(0, 3)), style = MaterialTheme.typography.labelMedium, color = CccColors.Amber)
    }
}

/** The Dashboard's top: its title, the search and the bell with the approvals. (The daemon's state is in the side menu.) */
@Composable
private fun DashboardHeader(state: HomeState, actions: ConversationsActions) {
    HudHeader(stringResource(R.string.dashboard)) {
        FrameIconButton(actions.search) { Icon(Icons.Filled.Search, stringResource(R.string.search), tint = CccColors.Cyan) }
        ApprovalsBell(state, actions)
    }
}

@Composable
private fun QueueHeader(state: HomeState, onBack: () -> Unit) {
    val queue = state.queue.orEmpty()
    HudHeader(
        stringResource(R.string.tasks),
        stringResource(R.string.queue_waiting, queue.size) +
            queue.count { it.task.byRoutine }.takeIf { it > 0 }?.let { " · " + stringResource(R.string.queue_from_routines, it) }.orEmpty(),
        onBack = onBack,
    )
}

@Composable
private fun ConversationList(
    state: HomeState,
    listDetails: Set<String>,
    newVersion: AppRelease? = null,
    actions: ConversationsActions,
    appUpdate: AppUpdateState,
    onCardMenu: (Conversation) -> Unit,
    onGroupMenu: (String) -> Unit,
) {
    val filter = ListFilter.of(state.filterStates, state.filter)
    val shown = (if (state.showArchive) state.archived else state.conversations).filter(filter::matches)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.showArchive) banners(state, newVersion, actions, appUpdate)
        item(key = "filter") { SearchField(state.filter, state.filterStates, actions.filter, actions.filterState) }
        if (!state.loaded) items(3) { ShimmerBlock(height = 84.dp) }
        if (state.loaded && shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(
                        when {
                            filter.active -> R.string.filter_nothing
                            state.showArchive -> R.string.archive_empty
                            else -> R.string.conversations_empty
                        },
                    ),
                    Modifier.fillMaxWidth().padding(32.dp),
                    color = CccColors.Muted,
                )
            }
        }
        var group: String? = null
        shown.forEachIndexed { index, conversation ->
            val heading = conversation.group
            if (heading != null && heading.key != group) {
                item(key = "group:${heading.key}") { GroupHeader(heading, onGroupMenu) }
            }
            group = heading?.key
            item(key = conversation.id) {
                ConversationCard(
                    conversation, listDetails,
                    Modifier.animateItem().staggeredEntry(index),
                    onLongClick = { onCardMenu(conversation) },
                ) { actions.open(conversation.id) }
            }
        }
    }
}

/** What may need you above the lists: who waits, a new ccc, a new app. (The usage limits are in the side menu.) */
private fun LazyListScope.banners(state: HomeState, newVersion: AppRelease?, actions: ConversationsActions, appUpdate: AppUpdateState) {
    state.status?.update?.takeIf { it.available }?.let { update ->
        item(key = "update") { UpdateBanner(update, actions.applyUpdate) }
    }
    newVersion?.let { release ->
        item(key = "app-newer") { NewAppBanner(release, appUpdate, actions.updateApp) }
    }
}

/** The overview: who needs you, the counts, then every conversation with its state. */
@Composable
private fun Dashboard(
    state: HomeState, listDetails: Set<String>, newVersion: AppRelease?, actions: ConversationsActions, appUpdate: AppUpdateState,
    onCardMenu: (Conversation) -> Unit,
) {
    val all = state.conversations
    val blocked = all.count { it.state == ConversationStatus.BLOCKED_ERROR }
    val running = all.count { it.state == ConversationStatus.RUNNING }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        banners(state, newVersion, actions, appUpdate)
        if (state.waiting.isNotEmpty()) {
            item(key = "waiting") { WaitingBanner(state, actions.nextWaiting) }
        }
        item(key = "overview") {
            HudPanel(brackets = false, accent = CccColors.Line, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell(all.size, stringResource(R.string.stat_chats), CccColors.Text)
                    StatCell(running, stringResource(R.string.stat_running), if (running > 0) CccColors.Cyan else CccColors.Faint)
                    StatCell(all.sumOf { it.pending }, stringResource(R.string.stat_queued), CccColors.Text)
                    StatCell(blocked, stringResource(R.string.stat_blocked), if (blocked > 0) CccColors.Red else CccColors.Faint)
                    StatCell(state.waiting.size, stringResource(R.string.stat_waiting), if (state.waiting.isNotEmpty()) CccColors.Amber else CccColors.Faint)
                }
            }
        }
        item(key = "heading") {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.conversations), style = MaterialTheme.typography.titleLarge, color = CccColors.Text)
                CccChip(all.size.toString(), color = CccColors.Dim)
            }
        }
        if (!state.loaded) items(3) { ShimmerBlock(height = 84.dp) }
        if (state.loaded && all.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.conversations_empty), Modifier.fillMaxWidth().padding(32.dp), color = CccColors.Muted)
            }
        }
        itemsIndexed(all, key = { _, c -> "dash:${c.id}" }) { index, conversation ->
            ConversationCard(conversation, listDetails, Modifier.animateItem().staggeredEntry(index), onLongClick = { onCardMenu(conversation) }) { actions.openQueue(conversation.id) }
        }
    }
}

/** 1 234 -> "1.2k", 3 400 000 -> "3.4M". */
internal fun tokenText(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(java.util.Locale.ROOT, tokens / 1_000_000.0)
    tokens >= 1_000 -> "%.1fk".format(java.util.Locale.ROOT, tokens / 1_000.0)
    else -> tokens.toString()
}

@Composable
internal fun DaemonLine(state: HomeState, onRestart: () -> Unit) {
    val (text, color) = when {
        !state.live && state.loaded -> stringResource(R.string.offline) to CccColors.Amber
        state.status?.daemonRunning == true -> stringResource(R.string.daemon_running) to CccColors.Cyan
        state.status != null -> stringResource(R.string.daemon_stopped) to CccColors.Red
        else -> "" to CccColors.Dim
    }
    if (text.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowDot(color, size = 9.dp, pulse = if (color == CccColors.Cyan) Pulse.GLOW else Pulse.NONE)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleSmall.copy(fontFamily = CccFonts.Mono), color = CccColors.Text)
        }
    }
    if (state.status?.restartNeeded == true) RestartLine(onRestart)
}

/** Under the daemon's line, as in the TUI's sidebar: ccc was upgraded while it runs; a tap (after asking) restarts it. */
@Composable
private fun RestartLine(onRestart: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.restart_needed),
        style = MaterialTheme.typography.labelMedium,
        color = CccColors.Waiting,
        modifier = Modifier.clickable { asking = true },
    )
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.restart_title)) },
            text = { Text(stringResource(R.string.restart_confirm_hint)) },
            confirmButton = { TextButton(onClick = { asking = false; onRestart() }) { Text(stringResource(R.string.restart_do)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Who needs you, most urgent first; a tap opens the first (Alt+N in ccc). */
/** A new ccc version on origin/main, with the button that updates it (after asking). */
@Composable
private fun UpdateBanner(update: UpdateInfo, onUpdate: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CccColors.Surface),
        border = BorderStroke(1.dp, CccColors.Waiting),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_available, update.behind), color = CccColors.Waiting,
                    fontWeight = FontWeight.Bold)
                if (update.android) {
                    Text(stringResource(R.string.update_android), style = MaterialTheme.typography.bodySmall, color = CccColors.Title)
                }
            }
            TextButton(onClick = { asking = true }) { Text(stringResource(R.string.update_button)) }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                Column {
                    update.commits.forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    if (update.behind > update.commits.size) Text("…", color = CccColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    val reason = update.reason?.takeIf { !update.canApply }
                    if (reason != null) Text(reason, color = CccColors.Error)
                    else Text(stringResource(R.string.update_confirm_hint), style = MaterialTheme.typography.bodySmall, color = CccColors.Muted)
                }
            },
            confirmButton = {
                TextButton(enabled = update.canApply, onClick = { asking = false; onUpdate() }) {
                    Text(stringResource(R.string.update_do))
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A newer release of the app on GitHub: the button downloads it and hands it to the installer. */
@Composable
private fun NewAppBanner(release: AppRelease, update: AppUpdateState, onUpdate: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CccColors.Surface),
        border = BorderStroke(1.dp, CccColors.Title),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_new_version, release.version), color = CccColors.Title,
                        style = MaterialTheme.typography.bodyMedium)
                    if (update.busy) {
                        Text(stringResource(R.string.app_downloading), style = MaterialTheme.typography.bodySmall, color = CccColors.Muted)
                    }
                }
                TextButton(onClick = onUpdate, enabled = !update.busy) { Text(stringResource(R.string.app_update_button)) }
            }
            if (update.busy) {
                LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = CccColors.Title)
            }
        }
    }
}

@Composable
private fun WaitingBanner(state: HomeState, onNext: () -> Unit) {
    val first = state.waiting.first()
    val color = when (first.kind) {
        "approval" -> CccColors.Cyan
        "error" -> CccColors.Red
        "limit" -> CccColors.Amber
        else -> CccColors.Cyan
    }
    val branch = state.conversations.firstOrNull { it.id == first.conversationId }?.git?.branch
    // The light runs around the frame only while something is there to approve.
    HudPanel(accent = CccColors.Cyan, glow = true, orbit = state.waiting.any { it.kind == "approval" }, onClick = onNext) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.needs_you, state.waiting.size).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CccColors.Cyan,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
                Spacer(Modifier.height(4.dp))
                Text("${first.name} — ${first.label}", style = MaterialTheme.typography.titleMedium, color = if (color == CccColors.Cyan) CccColors.Text else color,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(branch, stringResource(R.string.needs_you_tap)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(54.dp).border(1.dp, CccColors.Cyan, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.open_next), tint = CccColors.Cyan) }
        }
    }
}

/**
 * The search over the list (name and folder; case and diacritics don't matter) with, to its right, the
 * filter by state (ListFilter's markers).
 */
@Composable
private fun SearchField(words: String, states: Set<Char>, onChange: (String) -> Unit, onState: (Char) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = words,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.filter_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (words.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Icons.Filled.Clear, stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                BadgedBox(badge = { if (states.isNotEmpty()) Badge { Text(states.size.toString()) } }) {
                    Icon(FilterListIcon, stringResource(R.string.filter_by_state),
                        tint = if (states.isNotEmpty()) CccColors.Title else LocalContentColor.current)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MARKERS.zip(listOf(R.string.filter_working, R.string.filter_approval, R.string.filter_error, R.string.filter_unseen))
                    .forEach { (marker, label) ->
                        DropdownMenuItem(
                            text = { Text("$marker ${stringResource(label)}") },
                            leadingIcon = { if (marker in states) Icon(Icons.Filled.Check, null, tint = CccColors.Title) },
                            onClick = { onState(marker) },
                        )
                    }
            }
        }
    }
}

private val FilterListIcon: ImageVector = materialIcon("Filled.FilterList") {
    materialPath {
        moveTo(10f, 18f); horizontalLineToRelative(4f); verticalLineToRelative(-2f); horizontalLineToRelative(-4f); close()
        moveTo(3f, 6f); verticalLineToRelative(2f); horizontalLineToRelative(18f); verticalLineTo(6f); horizontalLineTo(3f); close()
        moveTo(6f, 13f); horizontalLineToRelative(12f); verticalLineToRelative(-2f); horizontalLineTo(6f); close()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(group: Group, onMenu: (String) -> Unit) {
    val own = group.key.removePrefix("own:").takeIf { group.key.startsWith("own:") }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .combinedClickable(onClick = { own?.let(onMenu) }, onLongClick = { own?.let(onMenu) }),
    ) {
        Caption(group.title, color = CccColors.Cyan)
        HorizontalDivider(Modifier.padding(top = 6.dp), color = CccColors.Line)
    }
}

/** An own group's heading tapped: rename it (into an existing one: they merge) or delete it. */
@Composable
private fun GroupDialog(name: String, actions: ConversationsActions, onClose: () -> Unit) {
    var newName by rememberSaveable(name) { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.group_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.group_rename)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.group_delete_hint), style = MaterialTheme.typography.bodySmall,
                    color = CccColors.Muted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    actions.renameGroup(name, newName.trim())
                    onClose()
                },
                enabled = newName.isNotBlank() && newName.trim() != name,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                actions.deleteGroup(name)
                onClose()
            }) { Text(stringResource(R.string.group_delete), color = CccColors.Error) }
        },
    )
}

/** Two letters for the avatar: SH for "shop-api", DP for "data pipeline". */
internal fun initials(name: String): String {
    val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val letters = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> "·"
    }
    return letters.uppercase()
}

/**
 * A conversation: a stripe in the colour of its state, an avatar with initials, the name and branch, the state
 * (running pulses, needs approval blinks slowly, an error stands still), then what [details] ask for.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ConversationCard(
    conversation: Conversation, details: Set<String>, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val state = conversation.state
    val color = statusColor(state, conversation.needsApproval)
    val approval = conversation.needsApproval
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (approval) Modifier.glowRect(CccColors.Amber, corner = 8.dp, spread = 8.dp, alpha = 0.3f) else Modifier)
            .clip(shape)
            .background(if (approval) CccColors.Amber.copy(alpha = 0.07f).compositeOver(CccColors.Surface) else CccColors.Surface, shape)
            .border(1.dp, if (approval) CccColors.Amber.copy(alpha = 0.8f) else CccColors.Line, shape)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(CccColors.CyanSoft, RoundedCornerShape(6.dp)).border(1.dp, CccColors.LineBright, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(initials(conversation.name), style = MaterialTheme.typography.titleMedium, color = CccColors.Cyan, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        conversation.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = CccColors.Text,
                        fontWeight = if (conversation.unseen) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    conversation.git?.branch?.let { CccChip(it, color = CccColors.Dim, modifier = Modifier.weight(1f, fill = false)) }
                    if (conversation.unseen) GlowDot(CccColors.Done, size = 7.dp) // finished, a new reply waits: green
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowDot(color, size = 7.dp, pulse = pulseOf(state, approval))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        (if (approval) stringResource(R.string.needs_approval) else statusLabel(state)).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val rest = buildList {
                        if (ListDetail.PENDING in details && conversation.pending > 0) add(stringResource(R.string.pending_count, conversation.pending))
                        if (conversation.unseen) add(stringResource(R.string.new_reply))
                    }
                    if (rest.isNotEmpty()) {
                        Text("  ·  " + rest.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = CccColors.Faint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    conversation.context?.let { context ->
                        Text("  ·  " + stringResource(R.string.context_short, context.text), style = MaterialTheme.typography.labelMedium,
                            color = contextColor(context), maxLines = 1)
                    }
                }
                conversation.goal?.let { goal ->
                    Text(
                        stringResource(R.string.goal_line, goal.icon, goal.round, goal.maxRounds, if (goal.running) goal.phaseLabel else goal.statusLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (goal.status) {
                            "running" -> CccColors.Cyan
                            "passed" -> CccColors.Claude
                            "stopped" -> CccColors.Faint
                            else -> CccColors.Red
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val git = conversation.git?.let { g -> GitState(null, g.added, g.removed, g.ahead, g.behind).text }
                    ?.takeIf { ListDetail.GIT in details && it.isNotEmpty() }
                if (git != null) {
                    Text(git, style = MaterialTheme.typography.labelMedium, color = CccColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (ListDetail.PATH in details) {
                    Text(shortPath(conversation.path), style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                conversation.lastError?.takeIf { state == ConversationStatus.BLOCKED_ERROR }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = CccColors.Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CccColors.Faint)
        }
    }
}

/** What "Update now" ended with — success, or why not — until the user closes it. */
@Composable
fun UpdateOutcomeDialog(outcome: AppViewModel.UpdateOutcome, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(stringResource(when {
                outcome.restart && outcome.ok -> R.string.restart_done_title
                outcome.restart -> R.string.restart_failed_title
                outcome.ok -> R.string.update_done_title
                else -> R.string.update_failed_title
            }))
        },
        text = { Text(outcome.text) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) } },
    )
}

/** The long press on a conversation: archive (or restore) it, or delete it after asking. */
@Composable
private fun CardMenuDialog(conversation: Conversation, actions: ConversationsActions, onClose: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text, conversation.name)) },
            confirmButton = {
                TextButton(onClick = { onClose(); actions.delete(conversation.id) }) {
                    Text(stringResource(R.string.delete), color = CccColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(conversation.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(onClick = { onClose(); actions.archive(conversation.id, !conversation.archived) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(if (conversation.archived) R.string.restore else R.string.archive_it), Modifier.fillMaxWidth())
                }
                TextButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.delete), Modifier.fillMaxWidth(), color = CccColors.Error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
