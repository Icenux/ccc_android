package io.github.icenux.ccc.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStateTest {
    @Test
    fun `the app opens on the Dashboard`() {
        assertEquals(HomeTab.DASHBOARD, HomeState().tab)
    }
}
