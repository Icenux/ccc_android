package io.github.icenux.ccc.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** How long Claude worked: from a human message to the last event before the next one. */
object Elapsed {
    fun instant(iso: String?): Instant? {
        iso ?: return null
        return runCatching { OffsetDateTime.parse(iso).toInstant() }
            .recoverCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }
            .getOrNull()
    }

    /**
     * For every finished turn the seconds it took, keyed by the index of the turn's last assistant text (where the
     * answer ends). The newest turn counts only when [running] is false — while it runs, the clock ticks instead.
     */
    fun turns(messages: List<Message>, running: Boolean): Map<Int, Long> {
        val result = mutableMapOf<Int, Long>()
        var start: Instant? = null
        var end: Instant? = null
        var answer: Int? = null
        fun close() {
            val a = answer
            val s = start
            val e = end
            if (a != null && s != null && e != null && e > s) result[a] = e.epochSecond - s.epochSecond
        }
        for (message in messages) {
            if (message.kind == "human") {
                close()
                start = instant(message.time)
                end = null
                answer = null
            } else {
                instant(message.time)?.let { end = it }
                if (message.kind == "assistant_text") answer = message.index
            }
        }
        if (!running) close()
        return result
    }

    /** When the running turn began: the newest human message. */
    fun runningSince(messages: List<Message>): Instant? =
        messages.lastOrNull { it.kind == "human" }?.let { instant(it.time) }

    /** 12 s, 2 min 14 s, 1 h 05 min. */
    fun text(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min ${s % 60} s"
            else -> "${s / 3600} h ${"%02d".format(s % 3600 / 60)} min"
        }
    }
}
