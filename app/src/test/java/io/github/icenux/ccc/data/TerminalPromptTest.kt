package io.github.icenux.ccc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalPromptTest {
    @Test
    fun permissionDialog() {
        val screen = "\u001b[1mDo you want to make this edit to src/api/products.py?\u001b[0m\n" +
            " \u001b[38;5;74m❯ 1. Yes\u001b[0m\n   2. Yes, and don't ask again this session\n   3. No, and tell Claude what to do differently\n\n" +
            "  Esc to cancel\n"
        val prompt = TerminalPrompts.detect(screen)!!
        assertEquals("Do you want to make this edit to src/api/products.py?", prompt.question)
        assertEquals(listOf(1, 2, 3), prompt.options.map { it.number })
        assertEquals(listOf(true, false, false), prompt.options.map { it.selected })
        assertEquals("Yes", prompt.options[0].label)
    }

    @Test
    fun dialogInABox() {
        val screen = "╭──────────────╮\n│ Bash command │\n│ pytest -q    │\n│ Proceed?     │\n│ ❯ 1. Yes     │\n│   2. No      │\n╰──────────────╯\n"
        val prompt = TerminalPrompts.detect(screen)!!
        assertEquals(2, prompt.options.size)
        assertNotNull(prompt.question)
    }

    @Test
    fun numberedListInAnAnswerIsNotAPrompt() {
        assertNull(TerminalPrompts.detect("Here is the plan:\n1. Add a cache\n2. Add tests\n3. Commit\n\n> "))
    }

    @Test
    fun menuAboveTheBottomIsNotCurrent() {
        val old = "Proceed?\n❯ 1. Yes\n  2. No\n" + "\nline\n".repeat(10)
        assertNull(TerminalPrompts.detect(old))
    }

    @Test
    fun emptyScreen() {
        assertNull(TerminalPrompts.detect(""))
    }

    @Test
    fun otherMarkersAreNotTheDialog() {
        assertNull(TerminalPrompts.detect("Proceed?\n> 1. Yes\n  2. No\n\n  Esc to cancel\n"))
        assertNull(TerminalPrompts.detect("Proceed?\n› 1. Yes\n  2. No\n\n  Esc to cancel\n"))
    }

    @Test
    fun withoutFrameOrFooterItIsNotTheDialog() {
        assertNull(TerminalPrompts.detect("Proceed?\n❯ 1. Yes\n  2. No\n"))
    }

    @Test
    fun longBodyIsMarkedAsCut() {
        val body = (1..60).joinToString("\n") { "line $it of the command" }
        val prompt = TerminalPrompts.detect("$body\nProceed?\n❯ 1. Yes\n  2. No\n  Esc to cancel\n")!!
        assertEquals(true, prompt.truncated)
    }

    @Test
    fun bodyOfTheDialogIsKept() {
        val screen = "● Running the tests\n\n──────────────\n Bash command\n\n   pytest -q tests/\n   Run the unit tests\n\n Do you want to proceed?\n ❯ 1. Yes\n   2. No\n\n Esc to cancel\n"
        val prompt = TerminalPrompts.detect(screen)!!
        assertEquals("Do you want to proceed?", prompt.question)
        assertEquals("Bash command\n\npytest -q tests/\nRun the unit tests", prompt.body)
        assertEquals(false, prompt.truncated)
    }
}
