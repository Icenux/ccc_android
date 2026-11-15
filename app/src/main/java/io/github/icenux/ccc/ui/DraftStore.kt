package io.github.icenux.ccc.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.properties.ReadWriteProperty

/**
 * Half-written prompts, one per conversation. Held outside the screens, so the text stays when the
 * person switches between the regular view and the terminal view of the same conversation.
 */
class DraftStore {
    private val drafts = mutableStateMapOf<Int, TextFieldValue>()

    operator fun get(id: Int): TextFieldValue = drafts[id] ?: TextFieldValue("")

    operator fun set(id: Int, value: TextFieldValue) {
        if (value.text.isEmpty()) drafts.remove(id) else drafts[id] = value
    }

    /** The draft of one conversation as a delegate: `var draft by drafts.of(id)`. */
    fun of(id: Int): ReadWriteProperty<Any?, TextFieldValue> = object : ReadWriteProperty<Any?, TextFieldValue> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = get(id)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: TextFieldValue) = set(id, value)
    }
}
