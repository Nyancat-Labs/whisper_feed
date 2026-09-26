package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pinned in source; the behaviour itself was checked in a Compose test under
 * Robolectric: with touch input, a focus request with no touch is refused, a
 * tap is accepted, and letting go closes the gate again.
 */
class FocusOnTapTest {

    private val gate = File("src/main/java/com/saulhdev/feeder/ui/components/FocusOnTap.kt").readText()

    @Test
    fun `the search field on Data sources takes focus only when tapped`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt").readText()
        assertTrue(page.contains(".focusOnlyWhenTapped()\n                                    .traceFocus(\"sources search\")"))
    }

    @Test
    fun `TalkBack and a hardware keyboard are never shut out`() {
        assertTrue(gate.contains("canFocus = pressed || talkBack || inputMode.inputMode == InputMode.Keyboard"))
        assertTrue(gate.contains("isTouchExplorationEnabled"))
    }

    @Test
    fun `the press is seen before the field asks for focus, and letting go closes the gate`() {
        assertTrue(gate.contains("pass = PointerEventPass.Initial"))
        assertTrue(gate.contains(".onFocusChanged { if (!it.isFocused) pressed = false }"))
    }
}
