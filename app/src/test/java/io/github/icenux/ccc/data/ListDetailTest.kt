package io.github.icenux.ccc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListDetailTest {
    @Test
    fun `the folder keeps its last two folders`() {
        assertEquals("…/projects/shop-api", shortPath("/home/you/projects/shop-api"))
        assertEquals("/home/shop-api", shortPath("/home/shop-api"))
        assertEquals("…/work/shop-api", shortPath("/home/you/projects/work/shop-api/"))
    }

    @Test
    fun `git shows the branch and only what is there`() {
        assertEquals("main", GitState("main").text)
        assertEquals("main ↓1", GitState("main", 0, 0, 0, 1).text)
        assertEquals("fix +4−1 ↑1", GitState("fix", 4, 1, 1, 0).text)
        assertEquals("", GitState(null).text)
    }
}
