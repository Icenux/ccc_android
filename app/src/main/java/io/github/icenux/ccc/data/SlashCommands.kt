package io.github.icenux.ccc.data

/** A Claude Code command typed with a leading slash; [name] has no slash. */
data class SlashCommand(val name: String)

/** The commands offered when the message starts with "/" (Claude Code's built-in ones). */
object SlashCommands {
    val ALL = listOf(
        "clear", "compact", "context", "cost", "usage", "model", "resume", "review", "memory", "init",
        "help", "config", "mcp", "agents", "permissions", "status", "doctor", "rewind", "export", "todos",
    ).map(::SlashCommand)

    /** Commands starting with [query] (what follows the slash), in the list's order. */
    fun matching(query: String): List<SlashCommand> = ALL.filter { it.name.startsWith(query.lowercase()) }
}

/** The command being typed: the text after the leading slash when the whole message is still one word, else null. */
fun slashQuery(text: String): String? {
    if (!text.startsWith("/")) return null
    val query = text.substring(1)
    return query.takeUnless { q -> q.any { it.isWhitespace() } }
}

/** How Claude Code's own bookkeeping lines in a transcript are shown in the chat. */
sealed interface CommandLine {
    /** A command you ran: "/clear", "/model opus". */
    data class Ran(val text: String) : CommandLine

    /** What a command printed. */
    data class Output(val text: String) : CommandLine

    /** Only a note for Claude (the "run directly in Claude Code" caveat) or an empty output: nothing to show. */
    data object Hidden : CommandLine
}

private val NAME = Regex("<command-name>\\s*(.*?)\\s*</command-name>", RegexOption.DOT_MATCHES_ALL)
private val ARGS = Regex("<command-args>\\s*(.*?)\\s*</command-args>", RegexOption.DOT_MATCHES_ALL)
private val STDOUT = Regex("<local-command-stdout>(.*?)</local-command-stdout>", RegexOption.DOT_MATCHES_ALL)
private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")

/** What a user line is when it is a slash command or its output (as Claude Code writes them); null for a normal message. */
fun commandLine(text: String): CommandLine? {
    val trimmed = text.trimStart()
    NAME.find(trimmed)?.takeIf { trimmed.startsWith("<command-") }?.let { name ->
        val args = ARGS.find(trimmed)?.groupValues?.get(1).orEmpty()
        return CommandLine.Ran(listOf(name.groupValues[1], args).filter { it.isNotEmpty() }.joinToString(" "))
    }
    if (trimmed.startsWith("<local-command-caveat>")) return CommandLine.Hidden
    if (trimmed.startsWith("<local-command-stdout>") || trimmed.startsWith("<local-command-stderr>")) {
        val out = STDOUT.find(trimmed)?.groupValues?.get(1)
            ?: trimmed.substringAfter('>').substringBeforeLast("</local-command-", trimmed.substringAfter('>'))
        val clean = out.replace(ANSI, "").trim()
        return if (clean.isEmpty()) CommandLine.Hidden else CommandLine.Output(clean)
    }
    return null
}
