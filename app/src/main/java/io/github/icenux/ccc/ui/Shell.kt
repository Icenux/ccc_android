package io.github.icenux.ccc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.effects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four places of the bottom bar. More opens the side menu. */
enum class Dest { DASHBOARD, CHATS, TASKS, MORE }

/** Which place of the bottom bar a screen belongs to (the lit one). */
fun destOf(screen: Screen, tab: HomeTab): Dest = when (screen) {
    Screen.Conversations -> when (tab) {
        HomeTab.DASHBOARD -> Dest.DASHBOARD
        HomeTab.CONVERSATIONS, HomeTab.ARCHIVE -> Dest.CHATS
        HomeTab.TASKS -> Dest.TASKS
        HomeTab.SESSIONS -> Dest.MORE
    }
    is Screen.Conversation, is Screen.ConversationDetail, Screen.Search -> Dest.CHATS
    is Screen.Tasks -> Dest.TASKS
    else -> Dest.MORE
}

/** Screens that carry the bottom bar: the others (the chat with its message field, sheets, sign-in) use the whole screen. */
fun hasBottomBar(screen: Screen): Boolean = when (screen) {
    Screen.Conversations, is Screen.ConversationDetail, is Screen.Tasks, is Screen.Agents,
    Screen.Routines, Screen.Subagents, Screen.Analytics, Screen.CccSettings, Screen.Approvals -> true
    else -> false
}

/** What the side menu shows besides the list of places. */
class ShellState(
    val screen: Screen,
    val home: HomeState,
    val server: String,
    val liveSessions: Int?,
    val notifications: Boolean,
    val effects: Boolean,
)

class ShellActions(
    val tab: (HomeTab) -> Unit,
    val screen: (Screen) -> Unit,
    val notifications: (Boolean) -> Unit,
    val effects: (Boolean) -> Unit,
)

/**
 * Around every screen: the side menu (with a handle on the left edge to pull it out) and the bottom bar.
 * The screens inside keep their own Scaffold; the bar's room is taken away from their insets here.
 */
@Composable
fun AppShell(state: ShellState, actions: ShellActions, content: @Composable () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val bar = hasBottomBar(state.screen)
    val selected = destOf(state.screen, state.home.tab)
    val close: () -> Unit = { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = state.screen != Screen.Setup,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        drawerContent = {
            SideMenu(state, actions, drawer, onClose = close) { go ->
                close()
                go(actions)
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (bar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                ) { content() }
                if (bar) {
                    CccBottomBar(selected) { dest ->
                        when (dest) {
                            Dest.DASHBOARD -> actions.tab(HomeTab.DASHBOARD)
                            Dest.CHATS -> actions.tab(HomeTab.CONVERSATIONS)
                            Dest.TASKS -> actions.tab(HomeTab.TASKS)
                            Dest.MORE -> scope.launch { drawer.open() }
                        }
                    }
                }
            }
            if (state.screen != Screen.Setup && !hasOwnInput(state.screen)) {
                DrawerHandle(Modifier.align(Alignment.CenterStart).offset(y = (-40).dp)) { scope.launch { drawer.open() } }
            }
        }
    }
}

/** Screens with a message or text field at the bottom: no handle there (it would sit on the text). */
private fun hasOwnInput(screen: Screen): Boolean = screen is Screen.Conversation || screen is Screen.Terminal

// --- bottom bar -------------------------------------------------------------------------------------------------

private class NavItem(val dest: Dest, val icon: ImageVector, val label: Int)

private val NAV = listOf(
    NavItem(Dest.DASHBOARD, HudIcons.Dashboard, R.string.nav_dashboard),
    NavItem(Dest.CHATS, HudIcons.Chats, R.string.nav_chats),
    NavItem(Dest.TASKS, HudIcons.Tasks, R.string.nav_tasks),
    NavItem(Dest.MORE, HudIcons.More, R.string.more),
)

@Composable
fun CccBottomBar(selected: Dest, onSelect: (Dest) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(60.dp)
            .glowRect(CccColors.Cyan, corner = 8.dp, spread = 8.dp, alpha = 0.10f)
            .clip(shape)
            .background(CccColors.Surface, shape)
            .border(1.dp, CccColors.Line, shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val cell = maxWidth / NAV.size
            val index = NAV.indexOfFirst { it.dest == selected }.coerceAtLeast(0)
            // The light that slides to the chosen place.
            val x by animateDpAsState(cell * index, tween(260, easing = FastOutSlowInEasing), label = "nav-indicator")
            Box(
                Modifier
                    .offset(x = x)
                    .width(cell)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CccColors.CyanSoft)
                    .drawBehind {
                        drawRect(CccColors.Cyan, size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                    },
            )
            Row(Modifier.fillMaxSize()) {
                NAV.forEach { item ->
                    val on = item.dest == selected
                    val tint by androidx.compose.animation.animateColorAsState(
                        if (on) CccColors.Cyan else CccColors.Faint, tween(200), label = "nav-tint",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(item.dest) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, null, Modifier.size(22.dp), tint = tint)
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(item.label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}

// --- the handle on the left edge --------------------------------------------------------------------------------

/** A small tab on the left edge that opens the side menu; at rest its glow breathes. */
@Composable
private fun DrawerHandle(modifier: Modifier, onClick: () -> Unit) {
    val loops = effects().loops
    val glow = if (loops) {
        rememberInfiniteTransition(label = "handle").animateFloat(
            0.25f, 0.9f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "handle-glow",
        ).value
    } else 0.5f
    val shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
    Box(
        modifier
            .glowRect(CccColors.Cyan, corner = 10.dp, spread = 8.dp, alpha = glow * 0.5f)
            .size(width = 22.dp, height = 92.dp)
            .clip(shape)
            .background(CccColors.Surface, shape)
            .border(1.dp, CccColors.Cyan.copy(alpha = 0.35f + glow * 0.45f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(HudIcons.Handle, stringResource(R.string.menu), Modifier.size(18.dp), tint = CccColors.Cyan)
    }
}

// --- the side menu ----------------------------------------------------------------------------------------------

private class MenuEntry(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val selected: Boolean,
    val badge: String? = null,
    val badgeColor: Color = CccColors.Dim,
    val go: (ShellActions) -> Unit,
)

@Composable
private fun SideMenu(state: ShellState, actions: ShellActions, drawer: DrawerState, onClose: () -> Unit, onGo: (((ShellActions) -> Unit)) -> Unit) {
    val home = state.home
    val onMain = state.screen == Screen.Conversations
    val screen = state.screen
    val purple = Color(0xFFA78BFA)
    val pink = Color(0xFFFF6B9A)
    val places = listOf(
        MenuEntry(HudIcons.Dashboard, stringResource(R.string.dashboard), CccColors.Cyan, onMain && home.tab == HomeTab.DASHBOARD) { it.tab(HomeTab.DASHBOARD) },
        MenuEntry(HudIcons.Shield, stringResource(R.string.approvals), CccColors.Red, screen == Screen.Approvals,
            home.permissions.size.takeIf { it > 0 }?.toString(), CccColors.Red) { it.screen(Screen.Approvals) },
        MenuEntry(HudIcons.Tasks, stringResource(R.string.tasks), CccColors.Amber, onMain && home.tab == HomeTab.TASKS,
            home.conversations.sumOf { it.pending }.takeIf { it > 0 }?.toString()) { it.tab(HomeTab.TASKS) },
        MenuEntry(HudIcons.Chats, stringResource(R.string.conversations), CccColors.Blue, onMain && home.tab == HomeTab.CONVERSATIONS) { it.tab(HomeTab.CONVERSATIONS) },
        MenuEntry(HudIcons.Archive, stringResource(R.string.archive), CccColors.Dim, onMain && home.tab == HomeTab.ARCHIVE,
            home.archived.size.takeIf { it > 0 }?.toString()) { it.tab(HomeTab.ARCHIVE) },
        MenuEntry(HudIcons.Routines, stringResource(R.string.routines), purple, screen == Screen.Routines) { it.screen(Screen.Routines) },
        MenuEntry(HudIcons.Helper, stringResource(R.string.subagents), CccColors.Claude, screen == Screen.Subagents) { it.screen(Screen.Subagents) },
        MenuEntry(HudIcons.Live, stringResource(R.string.live_sessions), pink, onMain && home.tab == HomeTab.SESSIONS,
            state.liveSessions?.takeIf { it > 0 }?.toString()) { it.tab(HomeTab.SESSIONS) },
    )
    val system = listOf(
        MenuEntry(HudIcons.Analytics, stringResource(R.string.analytics), CccColors.Faint, screen == Screen.Analytics) { it.screen(Screen.Analytics) },
        MenuEntry(HudIcons.Sliders, stringResource(R.string.ccc_settings), CccColors.Faint, screen == Screen.CccSettings) { it.screen(Screen.CccSettings) },
    )
    // The entries jump in one after another (30 ms apart) each time the menu opens.
    val opening = drawer.targetValue == DrawerValue.Open
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 330.dp),
        drawerContainerColor = CccColors.Background,
        drawerShape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
        windowInsets = WindowInsets(0),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                MenuHeader(state, onClose)
                var index = 0
                Caption(stringResource(R.string.menu_section_ccc), Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp))
                places.forEach { entry -> MenuRow(entry, index++, opening, big = true) { onGo(entry.go) } }
                Caption(stringResource(R.string.menu_section_system), Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp))
                system.forEach { entry -> MenuRow(entry, index++, opening, big = false) { onGo(entry.go) } }
                Spacer(Modifier.height(12.dp))
            }
            MenuFooter(state, actions)
        }
    }
}

@Composable
private fun MenuHeader(state: ShellState, onClose: () -> Unit) {
    val host = remember(state.server) {
        runCatching { java.net.URI(state.server.trim().let { if ("://" in it) it else "https://$it" }).host }.getOrNull() ?: state.server
    }
    val shape = RoundedCornerShape(6.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(52.dp).clip(shape).background(CccColors.CyanSoft, shape).border(1.dp, CccColors.LineBright, shape),
            contentAlignment = Alignment.Center,
        ) { Text("PH", style = MaterialTheme.typography.titleLarge, color = CccColors.Cyan, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.menu_this_phone), style = MaterialTheme.typography.titleMedium, color = CccColors.Text, maxLines = 1)
            Text(host.orEmpty(), style = MaterialTheme.typography.labelMedium, color = CccColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FrameIconButton(onClose) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.close), tint = CccColors.Dim) }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CccChip(stringResource(R.string.menu_paired).uppercase(), color = CccColors.Cyan, filled = true)
        CccChip(stringResource(R.string.menu_conversations, state.home.conversations.size), color = CccColors.Dim)
        val live = state.home.live
        CccChip(
            stringResource(if (live) R.string.menu_connected else R.string.menu_offline),
            color = if (live) CccColors.Cyan else CccColors.Amber,
            dot = true,
        )
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, index: Int, opening: Boolean, big: Boolean, onClick: () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(opening) {
        if (opening) {
            delay(index * 30L)
            shown.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        } else shown.snapTo(0f)
    }
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = shown.value
                translationX = (1f - shown.value) * -28.dp.toPx()
            }
            .padding(vertical = if (big) 2.dp else 0.dp)
            .clip(shape)
            .then(if (entry.selected) Modifier.background(CccColors.CyanSoft, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (big) 10.dp else 8.dp, vertical = if (big) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (big) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (entry.selected) Color.Transparent else entry.tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(entry.icon, null, Modifier.size(22.dp), tint = if (entry.selected) CccColors.Cyan else entry.tint) }
        } else {
            Icon(entry.icon, null, Modifier.size(22.dp), tint = entry.tint)
        }
        Spacer(Modifier.width(if (big) 14.dp else 16.dp))
        Text(
            entry.label,
            Modifier.weight(1f),
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (entry.selected) CccColors.Cyan else if (big) CccColors.Text else CccColors.Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.badge != null) {
            CccChip(entry.badge, color = entry.badgeColor, filled = entry.badgeColor == CccColors.Red)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = CccColors.Faint)
        }
    }
}

@Composable
private fun MenuFooter(state: ShellState, actions: ShellActions) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.home.usage?.let { usage ->
            HudPanel(brackets = false, glow = true, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption(stringResource(R.string.usage_limits), Modifier.weight(1f))
                    Text(
                        stringResource(if (state.home.live) R.string.menu_live else R.string.menu_stale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.home.live) CccColors.Cyan else CccColors.Faint,
                    )
                }
                Spacer(Modifier.height(8.dp))
                UsageCard(usage)
            }
        }
        SwitchRow(stringResource(R.string.notifications_on_short), state.notifications, actions.notifications)
        SwitchRow(stringResource(R.string.menu_effects), state.effects, actions.effects)
        Text(
            stringResource(R.string.menu_swipe_close),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = CccColors.Faint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = CccColors.Dim)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CccColors.Background,
                checkedTrackColor = CccColors.Cyan,
                uncheckedThumbColor = CccColors.Dim,
                uncheckedTrackColor = CccColors.SurfaceHigh,
                uncheckedBorderColor = CccColors.Line,
            ),
        )
    }
}
