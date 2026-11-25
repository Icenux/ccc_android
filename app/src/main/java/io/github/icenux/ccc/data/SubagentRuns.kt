package io.github.icenux.ccc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a conversation's subagents did (GET /conversations/{id}/subagents, ccc #286): a summary and every run, oldest
 * first, with [SubagentRun.parentId] making the tree. Not to be mixed up with [Subagent] (the agent *definitions*).
 */
@Serializable
data class SubagentRuns(val summary: SubagentSummary = SubagentSummary(), val runs: List<SubagentRun> = emptyList())

@Serializable
data class SubagentSummary(
    val total: Int = 0,
    val running: Int = 0,
    val errors: Int = 0,
    @SerialName("span_ms") val spanMs: Long = 0,
    val tokens: Long = 0,
)

/** [status]: "running", "done", "error" or "stopped"; [parentId] "" = started by the conversation itself. */
@Serializable
data class SubagentRun(
    val id: String,
    @SerialName("parent_id") val parentId: String = "",
    val kind: String = "",
    val description: String = "",
    val status: String = "done",
    val started: String? = null,
    val ended: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    val tokens: Long = 0,
    @SerialName("tool_count") val toolCount: Int = 0,
    val model: String = "",
    val depth: Int = 1,
    val background: Boolean = false,
    /** Only in the detail (GET …/subagents/{id}). */
    val prompt: String = "",
    val result: String = "",
    val steps: List<SubagentStep> = emptyList(),
) {
    val running get() = status == "running"
    val failed get() = status == "error" || status == "stopped"
}

/** One tool call of a run; [agentId] set when it started another run (an `Agent` step). [durationMs] null while it runs. */
@Serializable
data class SubagentStep(
    val index: Int = 0,
    val tool: String = "",
    val arg: String = "",
    val started: String? = null,
    @SerialName("offset_ms") val offsetMs: Long = 0,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val error: String = "",
    @SerialName("agent_id") val agentId: String = "",
)

/** Where a run's bar lies on the common timeline: fractions 0..1 of the whole span. */
data class Bar(val from: Float, val to: Float)

object SubagentTimeline {
    /** The runs in the order of the tree: every run is followed by its own runs. Runs with an unknown parent are roots. */
    fun treeOrder(runs: List<SubagentRun>): List<SubagentRun> {
        val known = runs.map { it.id }.toSet()
        val children = runs.groupBy { if (it.parentId in known) it.parentId else "" }
        val out = mutableListOf<SubagentRun>()
        fun walk(parent: String) {
            for (run in children[parent].orEmpty()) { out += run; walk(run.id) }
        }
        walk("")
        return out
    }

    /** The bars of [runs] on one axis from the first start to the last end (a running one reaches the end). */
    fun bars(runs: List<SubagentRun>): Map<String, Bar> {
        val starts = runs.associate { it.id to Elapsed.instant(it.started) }
        val first = starts.values.filterNotNull().minOrNull() ?: return emptyMap()
        val ends = runs.associate { run ->
            run.id to (starts[run.id]?.plusMillis(maxOf(run.durationMs, 1)) ?: first)
        }
        val span = (ends.values.max().toEpochMilli() - first.toEpochMilli()).coerceAtLeast(1).toFloat()
        return runs.mapNotNull { run ->
            val s = starts[run.id] ?: return@mapNotNull null
            val from = (s.toEpochMilli() - first.toEpochMilli()) / span
            val to = (ends.getValue(run.id).toEpochMilli() - first.toEpochMilli()) / span
            run.id to Bar(from.coerceIn(0f, 1f), maxOf(to, from + 0.015f).coerceIn(0f, 1f))
        }.toMap()
    }

    /** 4.9 s, 1 min 05 s, 7 min 03 s — [Elapsed.text] for seconds, one decimal under 10 s. */
    fun duration(ms: Long): String = if (ms in 0..9_999) "%.1f s".format(java.util.Locale.ROOT, ms / 1000.0) else Elapsed.text(ms / 1000)

    /** 950, 1.4k, 186k. */
    fun tokens(count: Long): String = when {
        count >= 10_000 -> "${count / 1000}k"
        count >= 1000 -> "%.1fk".format(java.util.Locale.ROOT, count / 1000.0)
        else -> count.toString()
    }
}
