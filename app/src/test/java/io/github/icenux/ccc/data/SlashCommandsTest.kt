package io.github.icenux.ccc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    @Test
    fun `the menu opens for a message that is only a slash word`() {
        assertEquals("", slashQuery("/"))
        assertEquals("co", slashQuery("/co"))
        assertNull(slashQuery("/model opus"))
        assertNull(slashQuery("hello /co"))
        assertNull(slashQuery(""))
    }

    @Test
    fun `commands are matched by their start`() {
        assertEquals(setOf("compact", "context", "cost", "config"), SlashCommands.matching("co").map { it.name }.toSet())
        assertTrue(SlashCommands.matching("zzz").isEmpty())
        assertEquals(SlashCommands.ALL, SlashCommands.matching(""))
    }

    @Test
    fun `a command run in Claude Code reads as the command, with its arguments`() {
        val clear = "<command-name>/clear</command-name>\n            <command-message>clear</command-message>\n            <command-args></command-args>"
        assertEquals(CommandLine.Ran("/clear"), commandLine(clear))
        val model = "<command-name>/model</command-name>\n<command-message>model</command-message>\n<command-args>opus</command-args>"
        assertEquals(CommandLine.Ran("/model opus"), commandLine(model))
    }

    @Test
    fun `the output is shown and the caveat or an empty output is not`() {
        assertEquals(CommandLine.Output("Set model to opus"), commandLine("<local-command-stdout>\u001B[1mSet model to opus\u001B[22m</local-command-stdout>"))
        assertEquals(CommandLine.Hidden, commandLine("<local-command-stdout></local-command-stdout>"))
        assertEquals(CommandLine.Hidden, commandLine("<local-command-caveat>Caveat: not a request</local-command-caveat>"))
    }

    @Test
    fun `a normal message is none of these`() {
        assertNull(commandLine("please run /clear for me"))
        assertNull(commandLine("<b>bold</b> text"))
    }
}
