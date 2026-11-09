package io.github.icenux.ccc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.github.icenux.ccc.data.Language
import io.github.icenux.ccc.data.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.icenux.ccc.data.Analytics
import io.github.icenux.ccc.data.CccSettings
import io.github.icenux.ccc.data.Choice
import io.github.icenux.ccc.data.AppRelease
import io.github.icenux.ccc.data.Conversation
import io.github.icenux.ccc.data.GitState
import io.github.icenux.ccc.data.ListDetail
import io.github.icenux.ccc.data.ConversationTokens
import io.github.icenux.ccc.data.DayTokens
import io.github.icenux.ccc.data.GoalBrief
import io.github.icenux.ccc.data.Goals
import io.github.icenux.ccc.data.LiveSession
import io.github.icenux.ccc.data.ModelTokens
import io.github.icenux.ccc.data.Review
import io.github.icenux.ccc.data.Reviews
import io.github.icenux.ccc.data.Routine
import io.github.icenux.ccc.data.RoutineKind
import io.github.icenux.ccc.data.Routines
import io.github.icenux.ccc.data.TelegramState
import io.github.icenux.ccc.data.WorkflowEvent
import io.github.icenux.ccc.data.WorkflowRun
import io.github.icenux.ccc.data.WorkflowStep
import io.github.icenux.ccc.data.WorkflowTemplate
import io.github.icenux.ccc.data.Workflows
import io.github.icenux.ccc.data.Task
import io.github.icenux.ccc.data.Tasks
import io.github.icenux.ccc.data.ConversationDetail
import io.github.icenux.ccc.data.Group
import io.github.icenux.ccc.data.LaunchChoice
import io.github.icenux.ccc.data.LaunchPresets
import io.github.icenux.ccc.data.LaunchGroup
import io.github.icenux.ccc.data.LaunchOption
import io.github.icenux.ccc.data.LaunchOptions
import io.github.icenux.ccc.data.Message
import io.github.icenux.ccc.data.Permission
import io.github.icenux.ccc.data.PromptPreset
import io.github.icenux.ccc.data.SearchHit
import io.github.icenux.ccc.data.Queued
import io.github.icenux.ccc.data.Status
import io.github.icenux.ccc.data.UpdateInfo
import io.github.icenux.ccc.data.Usage
import io.github.icenux.ccc.data.UsageWindow
import io.github.icenux.ccc.data.Waiting
import io.github.icenux.ccc.ui.theme.CccTheme
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen with made-up data, rendered for real (Robolectric's native
 * graphics) — that they draw at all, and pictures of them in
 * build/screenshots/<locale>/ for docs and pull requests.
 * Czech: ./gradlew testDebugUnitTest -Pscreenshots.locale=cs is not needed —
 * the cs test class below renders the same screens in Czech.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-xxhdpi")
open class ScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected open val locale = "en"

    @Test
    fun conversations() = shot("conversations", "shop-api") {
        shell(HomeTab.CONVERSATIONS) {
            ConversationsScreen(home.copy(tab = HomeTab.CONVERSATIONS), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
        }
    }

    @Test
    fun dashboard() = shot("dashboard", "shop-api") {
        shell(HomeTab.DASHBOARD) {
            ConversationsScreen(home.copy(tab = HomeTab.DASHBOARD), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
        }
    }

    @Test
    fun updateBanner() = shot("update", "shop-api") {
        val update = UpdateInfo(
            available = true, behind = 3, remoteHead = "a1b2c3d", android = true, canApply = true,
            commits = listOf("a1b2c3d Add rate limiting to the orders API", "9f8e7d6 Fix the login redirect",
                "5c4b3a2 Dashboard: git state"),
        )
        val status = home.status!!.copy(update = update, androidBuild = 6)
        shell(HomeTab.CONVERSATIONS) {
            ConversationsScreen(home.copy(tab = HomeTab.CONVERSATIONS, status = status, waiting = emptyList(), usage = null), ListDetail.DEFAULT,
                AppRelease("1.2.0", "", ""), SnackbarHostState(), listActions)
        }
    }

    @Test
    fun restartNeeded() = shot("restart_needed", "shop-api") {
        val status = home.status!!.copy(restartNeeded = true)
        shell(HomeTab.DASHBOARD) {
            ConversationsScreen(home.copy(tab = HomeTab.DASHBOARD, status = status), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
        }
    }

    @Test
    fun archive() = shot("archive", "old-prototype") {
        shell(HomeTab.ARCHIVE) {
            ConversationsScreen(home.copy(tab = HomeTab.ARCHIVE, filter = "proto"), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
        }
    }

    @Test
    fun conversation() = shot("conversation", "Rate limiting added") {
        ConversationScreen(conversation, home.promptPresets, home.ownGroups, SnackbarHostState(), conversationActions)
    }

    @Test
    fun mention() = shot("mention", "src/api/limits.py") {
        ConversationScreen(
            conversation.copy(mentions = listOf("src/api/limits.py", "src/api/orders.py", "tests/api/test_limits.py")),
            home.promptPresets, home.ownGroups, SnackbarHostState(), conversationActions,
        )
    }

    @Test
    fun tasks() = shot("tasks", "admin endpoints") {
        TasksScreen(tasksState, "shop-api", SnackbarHostState(), tasksActions)
    }

    @Test
    fun reviews() = shot("reviews", "hits()") {
        AgentsScreen(agentsState, "shop-api", 0, SnackbarHostState(), agentsActions)
    }

    @Test
    fun workflow() = shot("workflow", "Add caching") {
        AgentsScreen(agentsState, "shop-api", 2, SnackbarHostState(), agentsActions)
    }

    @Test
    fun routines() = shot("routines", "Nightly") {
        RoutinesScreen(routinesState, home.conversations, SnackbarHostState(), RoutinesActions({}, {}, {}, { _, _ -> }, {}))
    }

    @Test
    fun subagents() = shot("subagents", "test-runner") {
        SubagentsScreen(subagentsState, listOf("/home/me/shop-api" to "shop-api · /home/me/shop-api"), SnackbarHostState(),
            SubagentsActions({}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _, _ -> }))
    }

    /** A dialog with a text field never gets "idle" in Robolectric (the cursor keeps blinking): the clock is stepped by hand. */
    @Test
    fun subagent_create() {
        compose.setContent {
            CccTheme {
                SubagentsScreen(subagentsState, listOf("/home/me/shop-api" to "shop-api · /home/me/shop-api"), SnackbarHostState(),
                    SubagentsActions({}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _, _ -> }))
            }
        }
        compose.onAllNodesWithText("test-runner", substring = true, useUnmergedTree = true).onFirst().assertExists()
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithContentDescription(compose.activity.getString(io.github.icenux.ccc.R.string.subagent_add)).onFirst().performClick()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        saveWithDialog("subagent_create")
    }

    @Test
    fun analytics() = shot("analytics", "mobile-app") {
        AnalyticsScreen(analyticsState, home.usage, SnackbarHostState(), {}, { _, _ -> }, {})
    }

    @Test
    fun cccSettings() = shot("ccc_settings", "80 %") {
        CccSettingsScreen(settingsState, home.promptPresets, SnackbarHostState(), SettingsActions({}, {}, {}, {}, {}, {}, {}), initialTab = 1)
    }

    /** The phone speaks [locale]; the app is set to the other language — the point of the app's own choice. */
    @Test
    fun cccSettingsGeneral() {
        val appLanguage = if (locale == "en") "cs" else "en"
        Settings(compose.activity).language = appLanguage
        val wrapped = Language.wrap(compose.activity)
        val groups = if (appLanguage == "cs") listOf(Choice("project", "Podle projektu a vlastní skupiny"), Choice("own", "Jen vlastní skupiny"))
        else listOf(Choice("project", "By project, and your own groups"), Choice("own", "Only your own groups"))
        val label = Language.CHOICES.first { it.first == appLanguage }.second
        shot("ccc_settings_general", label) {
            CompositionLocalProvider(LocalContext provides wrapped, LocalConfiguration provides wrapped.resources.configuration) {
                CccSettingsScreen(settingsState.copy(groupsChoices = groups), home.promptPresets, SnackbarHostState(),
                    SettingsActions({}, {}, {}, {}, {}, {}, {}), initialTab = 0, language = appLanguage)
            }
        }
    }

    @Test
    fun liveSessions() = shot("live_sessions", "refactor-auth") {
        shell(HomeTab.SESSIONS) {
            ConversationsScreen(home.copy(tab = HomeTab.SESSIONS), ListDetail.DEFAULT, null, SnackbarHostState(), listActions,
                listOf(LiveSession("a1", "refactor-auth", "/home/you/projects/shop-api", "interactive", "busy"),
                    LiveSession("b2", "nightly-etl", "/home/you/projects/data-pipeline", "background", "idle")))
        }
    }

    @Test
    fun search() = shot("search", "rate limiting") {
        SearchScreen(searchState, SnackbarHostState(), {}, {}, {})
    }

    @Test
    fun approvals() = shot("approvals", "Bash") {
        ApprovalsScreen(listOf(permission), SnackbarHostState(), {}, {}, { _, _ -> })
    }

    @Test
    fun newConversation() = shot("new_conversation", "shop-api") {
        Box {
            ConversationsScreen(home.copy(tab = HomeTab.TASKS), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
            NewConversationSheet(
                NewConversationState(launchOptions, listOf("/home/you/projects/shop-api", "/home/you/projects/docs-site"),
                    LaunchPresets(presets = listOf(LaunchChoice(listOf("perm_plan", "model_opus"), name = "Review"),
                        LaunchChoice(listOf("perm_skip", "model_sonnet"), name = "Quick fix")))),
                SnackbarHostState(), {}, {}, {}, {},
            )
        }
    }

    @Test
    fun taskQueue() = shot("task_queue", "admin endpoints") {
        shell(HomeTab.TASKS) {
            ConversationsScreen(home.copy(tab = HomeTab.TASKS), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
        }
    }

    @Test
    fun conversationDetail() = shot("conversation_detail", "pytest -q tests/api") {
        shell(HomeTab.CONVERSATIONS, Screen.ConversationDetail(1)) {
            ConversationDetailScreen(home.conversations.first(), listOf(permission), SnackbarHostState(),
                DetailActions({}, {}, {}, {}, { _, _ -> }))
        }
    }

    @Test
    fun goalLoop() = shot("goal_loop", "Rate limit per API key") {
        shell(HomeTab.CONVERSATIONS, Screen.Agents(1, 1)) {
            AgentsScreen(agentsState.copy(goals = goalsState), "shop-api", 1, SnackbarHostState(), agentsActions)
        }
    }

    @Test
    fun menu() = shot("menu", "Dashboard", act = {
        val more = compose.activity.getString(io.github.icenux.ccc.R.string.more)
        compose.onAllNodesWithText(more).onFirst().performClick()
    }) {
        shell(HomeTab.DASHBOARD) {
            ConversationsScreen(home.copy(tab = HomeTab.DASHBOARD), ListDetail.DEFAULT, null, SnackbarHostState(), listActions)
        }
    }

    @Test
    fun setup() = shot("setup", "ccc") {
        SetupScreen("", false, false, ListDetail.DEFAULT, { _, _ -> }, SnackbarHostState(), null, { _, _, _ -> }, {}, {})
    }

    @Test
    fun connection() = shot("connection", "computer.tailnet") {
        SetupScreen("computer.tailnet.ts.net", true, true, ListDetail.DEFAULT, { _, _ -> }, SnackbarHostState(), {}, { _, _, _ -> }, {}, {},
            devices = listOf(
                io.github.icenux.ccc.data.Device(1, "Pixel 8", lastSeenAt = "2026-10-30T12:01:00", lastIp = "100.64.0.7", current = true),
                io.github.icenux.ccc.data.Device(2, "Redmi Note 13", lastSeenAt = "2026-10-29T07:54:00", lastIp = "100.64.0.9"),
            ))
    }

    @Test
    fun conversationListDetails() = shot("list_details", "computer.tailnet", scrollTo = io.github.icenux.ccc.R.string.detail_path) {
        SetupScreen("computer.tailnet.ts.net", true, true, setOf(ListDetail.GIT), { _, _ -> },
            SnackbarHostState(), {}, { _, _, _ -> }, {}, {})
    }

    @Test
    fun routine_delete() = shot("routine_delete", "Nightly", dialog = true) {
        RoutinesScreen(routinesState, home.conversations, SnackbarHostState(), RoutinesActions({}, {}, {}, { _, _ -> }, {}))
    }

    @Test
    fun update_failed() = shot("update_failed", "Nothing to update", dialog = true) {
        UpdateOutcomeDialog(AppViewModel.UpdateOutcome(false, "Nothing to update: ccc is already at the latest commit."), {})
    }

    @Test
    fun terminal() = shot("terminal", "Welcome") {
        TerminalScreen(terminalState, "web", SnackbarHostState(), TerminalActions({}, { _, _ -> }, {}, {}))
    }

    private fun shot(name: String, expect: String, scrollTo: Int? = null, dialog: Boolean = false, act: (() -> Unit)? = null, content: @Composable () -> Unit) {
        compose.setContent { CccTheme { content() } }
        compose.onAllNodesWithText(expect, substring = true, useUnmergedTree = true).onFirst().assertExists()
        scrollTo?.let { compose.onAllNodesWithText(compose.activity.getString(it)).onFirst().performScrollTo() }
        act?.invoke()
        compose.waitForIdle()
        if (dialog) {
            if (name == "routine_delete") {
                val delete = compose.activity.getString(io.github.icenux.ccc.R.string.delete)
                compose.onAllNodesWithContentDescription(delete).onFirst().performClick()
                compose.waitForIdle()
            }
            saveWithDialog(name)
        } else save(name)
    }

    /** The screen inside the side menu and bottom bar, as the app shows it. */
    @Composable
    private fun shell(tab: HomeTab, screen: Screen = Screen.Conversations, content: @Composable () -> Unit) {
        AppShell(
            ShellState(screen, home.copy(tab = tab), "computer.tailnet.ts.net", 1, notifications = false, effects = true),
            ShellActions({}, {}, {}, {}),
            content,
        )
    }

    /** The dialog is a window of its own: lay its picture over the dimmed screen below. */
    private fun saveWithDialog(name: String) {
        val screen = compose.activity.window.decorView.drawToBitmap()
        val window = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val box = window.drawToBitmap()
        val out = screen.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.argb(120, 0, 0, 0))
        canvas.drawBitmap(box, (out.width - box.width) / 2f, (out.height - box.height) / 2f, null)
        val folder = File("build/screenshots/$locale").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun save(name: String) {
        val bitmap = compose.activity.window.decorView.drawToBitmap()
        val folder = File("build/screenshots/$locale").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val listActions = ConversationsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
        val conversationActions = ConversationActions(
            {}, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {},
        )
        val terminalState = TerminalState(
            5,
            io.github.icenux.ccc.data.Terminal(
                "\u001b[38;5;214m✻\u001b[0m \u001b[1mWelcome to Claude Code!\u001b[0m\n\n" +
                    "  \u001b[2m/help for help, /status for your current setup\u001b[0m\n\n" +
                    "\u001b[38;5;74m> \u001b[0mAdd caching to GET /products\n\n" +
                    "\u001b[32m●\u001b[0m I'll add a Redis cache with a 60 s TTL.\n\n" +
                    "\u001b[1mDo you want to make this edit to src/api/products.py?\u001b[0m\n" +
                    " \u001b[38;5;74m❯ 1. Yes\u001b[0m\n   2. Yes, and don't ask again this session\n   3. No, and tell Claude what to do differently\n",
                100, 30, 3, 9,
            ),
        )
        val agentsActions = AgentsActions({}, { _, _, _ -> }, {}, {}, { _, _, _ -> }, {}, { _, _, _, _ -> }, { _, _ -> }, {})

        val agentsState = AgentsState(
            1,
            reviews = Reviews(active = false, reviews = listOf(
                Review(4, "done", "done", verdict = "FAIL", round = 1, maxRounds = 3, untilPass = true,
                    scope = "2 runs since the last review (4f2a9c1, 9b1d77a)",
                    report = "1. src/api/limits.py:5 — hits() counts per process, not per API key across workers.\n" +
                        "2. No test for the 429 Retry-After header.\n\nVERDICT: FAIL — the limit isn't shared between workers.",
                    canApply = true, createdAt = "2026-10-21T20:20:00"),
                Review(3, "done", "done", verdict = "PASS", scope = "run 8c0d1e2", report = "VERDICT: PASS — clean rename.",
                    createdAt = "2026-10-21T18:10:00"),
            )),
            goals = Goals(), workflows = Workflows(runs = listOf(
                WorkflowRun(2, "plan-build-review", "Add caching to GET /products", round = 1, maxRounds = 3, status = "waiting",
                    statusLabel = "waits for you", steps = listOf(WorkflowStep("plan", "done"), WorkflowStep("your approval", "waits"),
                        WorkflowStep("build", "to_come"), WorkflowStep("review ×2", "to_come")),
                    events = listOf(WorkflowEvent("plan", "plan", "planner", 1, "done",
                        output = "1. Cache in Redis for 60 s keyed by query\n2. Invalidate on product update\n3. Tests for hit / miss / invalidation")),
                    createdAt = "2026-10-21T20:30:00"),
            ), active = true, waiting = true),
            templates = listOf(WorkflowTemplate("plan-build-review", "Plan → approval → build → review",
                "plan → your approval → build → review ×2  (back to build at most 3×)")),
        )

        val routinesState = Routines(
            kinds = listOf(RoutineKind("daily", "Every day at", "e.g. 18:00"), RoutineKind("cron", "Cron pattern", "e.g. 0 22 * * *")),
            routines = listOf(
                Routine(1, 1, "shop-api", "Nightly dependencies", "Update dependencies, run the tests and open a PR", "daily", "22:00",
                    "every day at 22:00", nextRunAt = "2026-10-22T22:00:00", lastStatus = "queued in its conversation"),
                Routine(2, 3, "data-pipeline", "Weekly report", "Summarise last week's failed jobs", "weekly", "monday 08:00",
                    "Mondays at 08:00", fresh = true, enabled = false),
            ),
        )

        val subagentsState = io.github.icenux.ccc.ui.SubagentsState(
            path = "/home/me/shop-api",
            agents = listOf(
                io.github.icenux.ccc.data.Subagent("test-runner", "Runs the tests and reports only the result. Use after every change.", "project",
                    listOf("Bash", "Read", "Grep"), "haiku", file = "test-runner.md"),
                io.github.icenux.ccc.data.Subagent("code-reviewer", "Reviews code changes for bugs and security problems. Use proactively after code is written.",
                    "project", listOf("Read", "Grep", "Glob", "Bash"), "sonnet", file = "code-reviewer.md"),
                io.github.icenux.ccc.data.Subagent("draft", "", "project", null, "", problem = "no description", file = "draft.md"),
                io.github.icenux.ccc.data.Subagent("code-explorer", "Maps where code is used before a refactor.", "user", listOf("Read", "Grep", "Glob"), "haiku",
                    file = "code-explorer.md"),
            ),
            options = io.github.icenux.ccc.data.SubagentOptions(
                templates = listOf(io.github.icenux.ccc.data.SubagentTemplate("test-runner", "Test runner", "Runs the tests and reports only the result.", listOf("Bash"), "haiku", "You run the tests.")),
                access = listOf(
                    io.github.icenux.ccc.data.SubagentChoice("read", "Only read and search (safe)", listOf("Read", "Grep", "Glob")),
                    io.github.icenux.ccc.data.SubagentChoice("run", "Also run commands (tests, git …)", listOf("Read", "Grep", "Glob", "Bash")),
                ),
                effort = listOf(io.github.icenux.ccc.data.SubagentChoice("balanced", "Balanced (Sonnet)", model = "sonnet")),
                models = listOf("", "sonnet", "opus", "haiku"),
            ),
        )

        val analyticsState = Analytics(
            2026, 10, "October 2026", 3_643_987,
            byDay = (1..21).map { DayTokens("2026-10-%02d".format(it), (it * 37_000L) % 260_000 + 20_000) },
            byModel = listOf(ModelTokens("claude-sonnet-5-5", 3_157_987), ModelTokens("claude-opus-5-5", 486_000)),
            byConversation = listOf(ConversationTokens(4, "mobile-app", tokens = 3_017_988, success = 18),
                ConversationTokens(1, "shop-api", tokens = 486_000, success = 4, rateLimited = 1)),
            blockedMinutes = 12.5, reserve = 80,
        )

        val settingsState = CccSettings(
            language = "auto", languages = listOf(Choice("auto", "Auto (system)"), Choice("en", "English"), Choice("cs", "Čeština")),
            groups = "project", groupsChoices = listOf(Choice("project", "By project, and your own groups"), Choice("own", "Only your own groups")),
            continueText = "Continue where you stopped.", usageReserve = 80, reserveChoices = listOf(50, 80, 90),
            reviewModel = "", reviewModels = listOf(Choice("", "Same as the conversation"), Choice("opus", "Opus")),
            sendChoices = listOf(Choice("", "Only save to the folder")), telegram = TelegramState(true, "123456789", true),
            launchPresets = listOf("Quick fix", "Review"),
        )
        val tasksActions = TasksActions({}, { _, _ -> }, { _, _, _ -> }, {}, { _, _ -> }, {}, {})

        val tasksState = TasksState(1, Tasks(paused = false, autoContinue = true, tasks = listOf(
            Task(9, "Add rate limiting to the admin endpoints too"),
            Task(10, "Run the full test suite and fix anything that fails", notBefore = "2026-10-22T23:30:00"),
            Task(11, "Nightly: update dependencies and open a PR", byRoutine = true),
        )))

        val searchState = SearchState(
            query = "rate limiting",
            hits = listOf(
                SearchHit(1, "shop-api", "2026-10-21T18:13:00+00:00", "assistant_text",
                    "Rate limiting added: • GET /orders and GET /orders/{id} allow 60 requests/min per API key…"),
                SearchHit(1, "shop-api", "2026-10-21T18:02:00+00:00", "human",
                    "Add rate limiting to GET /orders — 60 requests a minute per API key."),
                SearchHit(6, "old-prototype", "2026-09-02T09:40:00+00:00", "assistant_text",
                    "…no rate limiting yet, the prototype trusts every client…"),
            ),
        )

        val permission = Permission(
            id = 1, conversationId = 1, tool = "Bash", conversation = "shop-api",
            input = buildJsonObject { put("command", JsonPrimitive("pytest -q tests/api")) },
        )

        val goalsState = Goals(active = true, goals = listOf(
            io.github.icenux.ccc.data.Goal(
                3, "Rate limit per API key", "pytest -q", round = 2, maxRounds = 5, status = "running", statusLabel = "running",
                phaseLabel = "Claude works on round 2",
                lastReason = "→ pytest -q tests/api\n✓ 8 passed in 2.1s\nreview: reading the diff…\nsrc/api/limits.py +42 -7",
                createdAt = java.time.LocalDateTime.now().minusMinutes(2).toString(),
            ),
        ))

        val home = HomeState(
            queue = listOf(
                QueueEntry(1, "shop-api", Task(11, "Add rate limiting to the admin endpoints too"), 0, false),
                QueueEntry(3, "data-pipeline", Task(12, "Fix the 3 failing pytest tests in the nightly import",
                    notBefore = "2026-10-22T22:00:00", byRoutine = true), 0, false),
                QueueEntry(2, "docs-site", Task(13, "Update the changelog for release 2.1"), 0, false),
                QueueEntry(5, "infra", Task(14, "Check dependency updates and open a pull request", byRoutine = true), 0, false),
            ),
            status = Status("0.1.0", "en", daemonRunning = true, conversations = 5, running = 2, needsApproval = 1),
            usage = Usage(UsageWindow(64.0), UsageWindow(38.0)),
            permissions = listOf(permission),
            waiting = listOf(Waiting(1, "shop-api", "approval", "waits for approval"),
                Waiting(3, "data-pipeline", "error", "error")),
            ownGroups = listOf("Release 2.1"),
            promptPresets = listOf(PromptPreset(1, "Tests", "Run the full test suite", sendNow = true),
                PromptPreset(2, "Commit", "Commit what you have", sendNow = false)),
            archived = listOf(
                Conversation(6, "old-prototype", "/home/you/projects/old-prototype", "idle", "headless", archived = true),
                Conversation(7, "spike-auth", "/home/you/projects/spike-auth", "idle", "interactive", archived = true),
            ),
            loaded = true,
            live = true,
            conversations = listOf(
                Conversation(1, "shop-api", "/home/you/projects/shop-api", "running", "interactive", pending = 2,
                    needsApproval = true, group = Group("own:Release 2.1", "Release 2.1"),
                    git = GitState("feature/rate-limit", 12, 3, 2, 0),
                    goal = GoalBrief(7, "running", "running", "Claude works on round 3", 3, 5)),
                Conversation(2, "docs-site", "/home/you/projects/docs-site", "waiting_retry", "interactive", pending = 1,
                    group = Group("own:Release 2.1", "Release 2.1"),
                    git = GitState("main", 0, 0, 0, 1)),
                Conversation(3, "data-pipeline", "/home/you/projects/data-pipeline", "blocked_error", "headless",
                    lastError = "pytest failed: 3 tests", group = Group("project:/home/you/projects/data-pipeline", "data-pipeline")),
                Conversation(4, "mobile-app", "/home/you/projects/mobile-app", "idle", "interactive", unseen = true,
                    group = Group("project:/home/you/projects/mobile-app", "mobile-app"),
                    git = GitState("fix/login-crash", 4, 1, 1, 0)),
                Conversation(5, "infra", "/home/you/projects/infra", "running", "remote_control",
                    group = Group("project:/home/you/projects/infra", "infra"),
                    goal = GoalBrief(8, "passed", "met", "", 2, 5)),
            ),
        )

        val conversation = ConversationState(
            id = 1,
            total = 5,
            detail = ConversationDetail(
                1, "shop-api", "/home/you/projects/shop-api", "running", "interactive", pending = 1,
                flags = "skip permissions · Opus · effort max",
                queued = listOf(Queued(9, "Add rate limiting to the admin endpoints too")),
                permissions = listOf(permission),
            ),
            messages = listOf(
                Message(0, "human", "Add rate limiting to GET /orders — 60 requests a minute per API key.",
                    "2026-10-21T18:02:00+00:00"),
                Message(1, "tool_call", "Edit: src/api/limits.py", "2026-10-21T18:03:00+00:00"),
                Message(2, "tool_result", "The file src/api/limits.py has been updated.", "2026-10-21T18:03:00+00:00"),
                Message(3, "tool_call", "Bash: pytest -q tests/api/test_orders.py", "2026-10-21T18:04:00+00:00"),
                Message(4, "assistant_text", "Rate limiting added:\n• GET /orders and GET /orders/{id} allow 60 requests/min " +
                    "per API key\n• limits live in src/api/limits.py\n• 3 new tests — all 8 passing", "2026-10-21T18:13:00+00:00"),
            ),
        )

        val launchOptions = LaunchOptions(
            groups = listOf(
                LaunchGroup("session", "Session", exclusive = true, required = true, options = listOf(
                    LaunchOption("session_new", "New session"), LaunchOption("session_continue", "Continue last in directory"),
                    LaunchOption("session_resume", "Resume selected…"), LaunchOption("session_from_pr", "From pull request…"))),
                LaunchGroup("session_extra", "", exclusive = false, required = false,
                    options = listOf(LaunchOption("fork", "As a copy (fork)"))),
                LaunchGroup("permissions", "Permissions", exclusive = true, required = true, options = listOf(
                    LaunchOption("perm_skip", "Skip all permission checks"), LaunchOption("perm_accept_edits", "Auto-accept edits"),
                    LaunchOption("perm_plan", "Plan only"))),
                LaunchGroup("model", "Model", exclusive = true, required = false, options = listOf(
                    LaunchOption("model_opus", "Opus"), LaunchOption("model_sonnet", "Sonnet"),
                    LaunchOption("model_haiku", "Haiku"), LaunchOption("model_fable", "Fable"))),
            ),
            default = listOf("session_new", "perm_skip"),
        )
    }
}

/** The same screens in Czech (ccc's texts in them stay as the data has them). */
@Config(sdk = [35], qualifiers = "cs-w411dp-h891dp-xxhdpi")
class ScreenshotsCzechTest : ScreenshotsTest() {
    override val locale = "cs"
}
