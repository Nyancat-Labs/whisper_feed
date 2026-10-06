package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Opening the overflow menu drops keyboard focus.
 *
 * Compose restores focus to whatever held it when a popup closes. On the
 * sources screen that is the search field, so opening the three-dot menu,
 * choosing something and coming back brought the keyboard up over the result
 * — covering half the screen, with nothing to type into and no reason given.
 *
 * The FocusTrace added for the category editor is what caught it, in a report
 * sent for something else entirely:
 *
 *     19:25:56.236  sources search lost focus
 *     19:25:56.274  sources search gained focus
 *     19:25:56.278  show(ime())
 *
 * Thirty-eight milliseconds apart, with nothing between them that a person
 * did. Reading the screen found no FocusRequester and no requestFocus call,
 * because there are none: nothing asks for this, which is exactly why it
 * would be looked for in the wrong place a second time.
 *
 * Pinned here because the clear is one line with no visible effect on the
 * screen that contains it, in a component shared by every screen that has a
 * menu. A tidy-up removing it would look harmless and break all of them.
 */
class MenuFocusTest {

    private val source = File(
        "src/main/java/com/saulhdev/feeder/ui/components/OverflowMenu.kt"
    ).readText()

    @Test
    fun `the source is actually being read`() {
        assertTrue(source.contains("fun OverflowMenu("))
    }

    @Test
    fun `focus is cleared where the menu is opened`() {
        assertTrue(
            "nothing clears focus, so the keyboard comes back with the menu",
            source.contains("focusManager.clearFocus()"),
        )
        val clear = source.indexOf("focusManager.clearFocus()")
        val open = source.indexOf("showMenu.value = true")
        assertTrue("the menu no longer opens where it did", open > 0)
        assertTrue(
            "focus is cleared after the menu opens, which is too late to stop the restore",
            clear < open,
        )
    }

    @Test
    fun `every three-dot menu clears focus as it opens`() {
        // OverflowMenu covers most screens, but ArticleMenu is hand-rolled —
        // it carries its own explain state — so it does not get the fix for
        // free, and a third menu written the same way would not either. The
        // check is therefore on the pattern rather than on one file.
        val menus = File("src/main/java/com/saulhdev/feeder")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("Phosphor.DotsThreeVertical") }
            .filter { it.readText().contains("IconButton(") }
            .toList()

        assertTrue("no three-dot menus found; the icon was renamed", menus.isNotEmpty())
        menus.forEach { file ->
            val text = file.readText()
            assertTrue(
                "${file.name} opens a menu without dropping focus, so the " +
                        "keyboard comes back when it closes",
                text.contains("focusManager.clearFocus()"),
            )
        }
    }
}
