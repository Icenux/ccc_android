package io.github.icenux.ccc.data

/** One choice of a menu Claude Code shows in its terminal: "1. Yes" ([selected]: the ❯ is on it). */
data class PromptOption(val number: Int, val label: String, val selected: Boolean)

/** A question Claude Code asks in its terminal (a permission, a plan, a choice), with the numbered answers. */
data class TerminalPrompt(val question: String, val options: List<PromptOption>)

/**
 * Finds such a question on a terminal screen. Claude Code's interactive prompts don't go through ccc's
 * approvals, so the app reads them from the screen: a numbered menu with a ❯ on one of the choices, at the
 * bottom (only its footer hint below it). Numbered lists in Claude's answers have no ❯ and are not taken.
 */
object TerminalPrompts {
    private val OPTION = Regex("""^\s*([❯›>])?\s*(\d{1,2})[.)]\s+(\S.*)$""")
    private const val BOX = "│┃║"

    /** A screen line without the frame Claude Code draws around a dialog. */
    private fun clean(line: String): String = line.trim().trim { it in BOX }.trimEnd()

    fun detect(screen: String): TerminalPrompt? {
        val plain = Ansi.parse(screen).joinToString("") { it.text }
        val lines = plain.lines().map(::clean).dropLastWhile { it.isBlank() }
        val last = lines.indexOfLast { OPTION.matches(it) }
        if (last < 0 || lines.size - 1 - last > 6) return null
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
        val question = buildList {
            var i = first - 1
            while (i >= 0 && lines[i].isBlank()) i--
            while (i >= 0 && lines[i].isNotBlank() && size < 4) add(lines[i--].trim())
        }.asReversed().joinToString(" ").trim()
        return TerminalPrompt(question, options)
    }
}
