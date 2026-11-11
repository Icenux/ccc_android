package io.github.icenux.ccc.data

/** One choice of a menu Claude Code shows in its terminal: "1. Yes" ([selected]: the ❯ is on it). */
data class PromptOption(val number: Int, val label: String, val selected: Boolean)

/** A question Claude Code asks in its terminal (a permission, a plan, a choice), with the numbered answers. */
data class TerminalPrompt(
    val question: String,
    val options: List<PromptOption>,
    val truncated: Boolean = false,
    /** What the dialog asks about, above the question (the command, the file and its diff, the plan), line breaks kept. */
    val body: String = "",
)

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
    private const val BODY_LINES = 40
    /** A horizontal rule or the top of a box: where Claude Code's dialog starts. */
    private val RULE = Regex("""^[\s─━═╭╮╰╯┄┈╌-]{3,}$""")
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
        var i = first - 1
        while (i >= 0 && lines[i].isBlank()) i--
        val questionLines = buildList {
            while (i >= 0 && lines[i].isNotBlank() && size < QUESTION_LINES && !RULE.matches(lines[i])) add(lines[i--].trim())
        }.asReversed()
        // The body: what the dialog is about, further up — to the dialog's top rule or Claude's own output.
        val bodyLines = ArrayDeque<String>()
        // Only the dialog's own top border (a rule) confirms that the whole body is shown; any other stop may hide a part.
        var border = false
        while (i >= 0 && bodyLines.size < BODY_LINES) {
            val line = lines[i]
            if (RULE.matches(line)) { border = true; break }
            if (line.isBlank() && (bodyLines.isEmpty() || bodyLines.first().isBlank())) { i--; continue }
            bodyLines.addFirst(line.trimEnd())
            i--
        }
        while (bodyLines.isNotEmpty() && bodyLines.first().isBlank()) bodyLines.removeFirst()
        // Anything but the top border: the card says the body may be incomplete, the terminal has all of it.
        cut = bodyLines.isNotEmpty() && !border
        val question = questionLines.joinToString(" ").trim()
        val body = bodyLines.joinToString("\n").trimEnd()
        return TerminalPrompt(question, options, truncated = cut, body = body)
    }
}
