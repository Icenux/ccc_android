package io.github.icenux.ccc.data

/** What to tell the phone about: an approval to answer, or a conversation that needs you. */
sealed interface Alert {
    /** Stable across checks: the same alert is shown once. */
    val key: String

    /** Which of the user's choices (AlertKind) covers it. */
    val kind: String

    data class Approval(val permission: Permission) : Alert {
        override val key get() = "p:${permission.id}"
        override val kind get() = AlertKind.APPROVAL
    }

    data class NeedsYou(val waiting: Waiting) : Alert {
        override val key get() = "w:${waiting.conversationId}:${waiting.kind}"
        override val kind get() = waiting.kind
    }

    /** A new ccc version: once per version (its head). */
    data class Update(val behind: Int, val head: String) : Alert {
        override val key get() = "u:$head"
        override val kind get() = AlertKind.UPDATE
    }
}

/** The kinds of notification the user can switch on and off in the settings. */
object AlertKind {
    const val APPROVAL = "approval"
    const val DONE = "done"
    const val ERROR = "error"
    const val LIMIT = "limit"
    const val UPDATE = "update"

    val ALL = listOf(APPROVAL, DONE, ERROR, LIMIT, UPDATE)
    val DEFAULT: Set<String> = ALL.toSet()
}

/** The outcome of one check: alerts to show, and keys of shown ones that are over (to take away). */
data class AlertChanges(val show: List<Alert>, val gone: Set<String>)

/**
 * Which alerts are new since the last check. An approval is an alert of
 * its own; a conversation needing you for something else (an error, a
 * limit, a finished reply) is one per conversation and kind — shown again
 * only after it stopped needing you.
 */
object Alerts {
    fun changes(
        permissions: List<Permission>,
        waiting: List<Waiting>,
        shown: Set<String>,
        update: UpdateInfo? = null,
        kinds: Set<String> = AlertKind.DEFAULT,
    ): AlertChanges {
        val news = update?.takeIf { it.available && !it.remoteHead.isNullOrEmpty() }?.let { listOf(Alert.Update(it.behind, it.remoteHead!!)) }
            .orEmpty()
        val now = (permissions.map(Alert::Approval) + waiting.filter { it.kind != "approval" }.map(Alert::NeedsYou) + news)
            // A kind the app doesn't know is never switched off.
            .filter { it.kind in kinds || it.kind !in AlertKind.ALL }
        val keys = now.map { it.key }.toSet()
        return AlertChanges(show = now.filter { it.key !in shown }, gone = shown - keys)
    }
}
