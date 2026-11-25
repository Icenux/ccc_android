package io.github.icenux.ccc.data

import kotlinx.serialization.Serializable

/**
 * One of ccc's own notes (ccc #223): a Markdown file in ccc's folder on the computer. The list has no [content]; one
 * note read whole has it, with the [version] a change has to send back (ccc answers 409 when the note changed since).
 */
@Serializable
data class Note(
    val path: String,
    val title: String = "",
    val size: Int = 0,
    val modified: String = "",
    val version: String = "",
    val content: String = "",
) {
    /** A note typed in the app and not saved yet has no version. */
    val isNew: Boolean get() = version.isEmpty()

    companion object {
        /** What the user typed as a name, with .md when it has none; blank stays blank. */
        fun pathOf(typed: String): String {
            val text = typed.trim().trim('/')
            return if (text.isEmpty() || text.endsWith(".md", ignoreCase = true)) text else "$text.md"
        }

        /** The path of the copy that keeps an edit made over a note that changed meanwhile: "a (conflict).md", "a (conflict 2).md". */
        fun conflictPath(path: String, number: Int): String {
            val stem = path.removeSuffix(".md")
            return if (number <= 1) "$stem (conflict).md" else "$stem (conflict $number).md"
        }
    }
}
