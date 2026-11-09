package io.github.icenux.ccc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/** A conversation at a glance (GET /conversations/{id}/overview, src/ccc/overview.py): now, changes, usage, errors. */
@Serializable
data class Overview(
    val now: OverviewNow = OverviewNow(),
    /** null where ccc may not run git (outside git, or not started with "Skip all permission checks"). */
    val changes: OverviewChanges? = null,
    val usage: OverviewUsage = OverviewUsage(),
    val errors: OverviewErrors = OverviewErrors(),
)

/** What the last transcript line was: [kind] is "human", "assistant_text", "tool_call" or "tool_result". */
@Serializable
data class OverviewEvent(val kind: String = "", val text: String = "", val time: String? = null)

/**
 * [state]: "working", "waiting_approval", "waiting_limit", "paused_limit", "blocked", "paused" or "idle";
 * [stateLabel] is the server's text for it in the language of the app.
 */
@Serializable
data class OverviewNow(
    val state: String = "idle",
    @SerialName("state_label") val stateLabel: String = "",
    val pending: Int = 0,
    @SerialName("next_retry_at") val nextRetryAt: String? = null,
    @SerialName("last_event") val lastEvent: OverviewEvent? = null,
    val context: ContextFill? = null,
)

/** [status]: "modified" or "new"; the lines are null for a binary or a new file. */
@Serializable
data class OverviewFile(
    val path: String,
    val status: String = "modified",
    val added: Int? = null,
    val removed: Int? = null,
)

@Serializable
data class OverviewChanges(
    val branch: String? = null,
    val added: Int = 0,
    val removed: Int = 0,
    val ahead: Int? = null,
    val behind: Int? = null,
    val files: List<OverviewFile> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class OverviewDay(val day: String, val tokens: Long = 0)

@Serializable
data class OverviewModel(val model: String, val tokens: Long = 0)

@Serializable
data class OverviewUsage(
    val total: Long = 0,
    val today: Long = 0,
    val days: List<OverviewDay> = emptyList(),
    val models: List<OverviewModel> = emptyList(),
)

@Serializable
data class OverviewFailure(val id: Int = 0, val time: String? = null, val status: String = "", val text: String = "")

@Serializable
data class OverviewErrors(
    /** What blocks the conversation now. */
    val current: String? = null,
    /** How many runs failed in the last [lastDays] days. */
    val count: Int = 0,
    @SerialName("last_days") val lastDays: Int = 7,
    val recent: List<OverviewFailure> = emptyList(),
)

/** "10:30" for a time today, else "09.11. 10:30"; the server sends local time without a zone. */
fun overviewTime(iso: String?, today: LocalDate = LocalDate.now()): String {
    val moment = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return "–"
    val clock = "%02d:%02d".format(moment.hour, moment.minute)
    return if (moment.toLocalDate() == today) clock else "%02d.%02d. %s".format(moment.dayOfMonth, moment.monthValue, clock)
}
