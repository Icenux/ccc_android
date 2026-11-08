package io.github.icenux.ccc.data

/** One choice of a menu Claude Code shows in its terminal: "1. Yes" ([selected]: the ❯ is on it). */
data class PromptOption(val number: Int, val label: String, val selected: Boolean)

/** A question Claude Code asks in its terminal (a permission, a plan, a choice), with the numbered answers. */
data class TerminalPrompt(val question: String, val options: List<PromptOption>, val truncated: Boolean = false)

/**
 * Finds such a question on a terminal screen. Claude Code's interactive prompts don't go through ccc's
 * approvals, so the app reads them from the screen: a numbered menu with a ❯ on one of the choices, at the
 * bottom, and Claude Code's own dialog around it — a frame (│) or the "Esc to cancel" footer. Numbered lists
 * in Claude's answers have no ❯ and no dialog, and text that merely looks like a menu is not taken.
 */
object TerminalPrompts {
    private val OPTION = Regex("""^\s*(❯)?\s*(\d{1,2})[.)]\s+(\S.*)$""")
    private val FOOTER = Regex("""(?i)esc to (cancel|exit|go back)|enter to (select|confirm)""")
    private const val QUESTION_LINES = 8
    private const val BOX = "│┃║"

    /** A screen line without the frame Claude Code draws around a dialog. */
    private fun clean(line: String): String = line.trim().trim { it in BOX }.trimEnd()

    fun detect(screen: String): TerminalPrompt? {
        val plain = Ansi.parse(screen).joinToString("") { it.text }
        val raw = plain.lines().dropLastWhile { it.isBlank() }
        val lines = raw.map(::clean)
        val last = lines.indexOfLast { OPTION.matches(it) }
        if (last < 0 || lines.size - 1 - last > 6) return null
        // Only inside Claude Code's own dialog: a frame around the menu, or its footer hint under it.
        val framed = raw[last].trim().firstOrNull()?.let { it in BOX } == true
        val footer = lines.drop(last + 1).any { FOOTER.containsMatchIn(it) }
        if (!framed && !footer) return null
        // The block of options upwards from the last one; a long label may continue on indented lines.
        var first = last
        while (first > 0) {
            val above = lines[first - 1]
            if (OPTION.matches(above) || (above.isNotBlank() && above.startsWith("    ") && first - 1 > 0)) first-- else break
        }
        while (first < last && !OPTION.matches(lines[first])) first++
        val options = mutableListOf<PromptOption>()
        for (i in first..last) {
            val match = OPTION.matchEntire(lines[i])
            if (match != null) {
                options += PromptOption(match.groupValues[2].toInt(), match.groupValues[3].trim(), match.groupValues[1].isNotEmpty())
            } else if (options.isNotEmpty()) {
                val previous = options.removeAt(options.lastIndex)
                options += previous.copy(label = previous.label + " " + lines[i].trim())
            }
        }
        if (options.size < 2 || options.first().number != 1 || options.none { it.selected }) return null
        if (options.map { it.number } != (1..options.size).toList()) return null
        // The question: the lines right above the menu, up to the blank line.
        var cut = false
        val question = buildList {
            var i = first - 1
            while (i >= 0 && lines[i].isBlank()) i--
            while (i >= 0 && lines[i].isNotBlank() && size < QUESTION_LINES) add(lines[i--].trim())
            // More of the dialog above than fits: the card says so, the terminal has all of it.
            cut = i >= 0 && lines[i].isNotBlank() && size >= QUESTION_LINES
        }.asReversed().joinToString(" ").trim()
        return TerminalPrompt(question, options, truncated = cut)
    }
}
