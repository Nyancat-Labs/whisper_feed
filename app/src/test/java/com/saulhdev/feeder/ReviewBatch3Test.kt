package com.saulhdev.feeder

import com.saulhdev.feeder.ui.components.isTowardsTop
import com.saulhdev.feeder.ui.overlay.bylinePlan
import com.saulhdev.feeder.ui.overlay.levelOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The feed, from the 27 October preview review (batch 3). */
class ReviewBatch3Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the byline keeps everything when there is room`() {
        val plan = bylinePlan(available = 300, category = 40, name = 80, age = 60, ageShort = 20)
        assertTrue(plan.showCategory)
        assertFalse(plan.shortAge)
        assertEquals(80, plan.nameRoom)
    }

    @Test
    fun `short of room, the category goes first, then the long age`() {
        // 160 - 40 - 60 = 60: not the whole name, so the category goes.
        val noCategory = bylinePlan(available = 160, category = 40, name = 100, age = 60, ageShort = 20)
        assertFalse(noCategory.showCategory)
        assertFalse(noCategory.shortAge)
        assertEquals(100, noCategory.nameRoom)
        // Still not: the short age, and the whole name.
        val shortAge = bylinePlan(available = 130, category = 40, name = 100, age = 60, ageShort = 20)
        assertFalse(shortAge.showCategory)
        assertTrue(shortAge.shortAge)
        assertEquals(100, shortAge.nameRoom)
    }

    @Test
    fun `a whole name beats the clock time`() {
        // "The Independ… · 12m · 16:14 · 2 min" became "The Independent · 12m".
        val plan = bylinePlan(available = 150, category = 0, name = 100, age = 90, ageShort = 25)
        assertTrue(plan.shortAge)
        assertEquals(100, plan.nameRoom)
    }

    @Test
    fun `with no room for the whole name, the name gets all there is`() {
        val plan = bylinePlan(available = 80, category = 40, name = 100, age = 60, ageShort = 20)
        assertFalse(plan.showCategory)
        assertTrue(plan.shortAge)
        assertEquals(60, plan.nameRoom)
    }

    @Test
    fun `the byline starts at the left edge however wide it is told to be`() {
        // Narrower than its minimum, a layout is centred in it: the byline
        // sat in the middle of a full-width card.
        assertTrue(source("ui/overlay/ArticleCard.kt").contains(".coerceAtLeast(constraints.minWidth)"))
    }

    @Test
    fun `a tile moves up to level the lanes before a lead`() {
        // Lane heights after 0 and 1: 200 and 100; the lead at 2 would leave
        // 100 empty. The 100-high tile at 3 moves up into the short lane.
        val order = levelOrder(
            heights = listOf(200f, 100f, 0f, 100f, 50f),
            lead = listOf(false, false, true, false, false),
            movable = List(5) { true },
            group = List(5) { "day" },
            lanes = 2,
        )
        assertEquals(listOf(0, 1, 3, 2, 4), order)
    }

    @Test
    fun `nothing moves across a day, nor a pinned tile`() {
        val heights = listOf(200f, 100f, 0f, 100f)
        val lead = listOf(false, false, true, false)
        assertEquals(listOf(0, 1, 2, 3), levelOrder(heights, lead, List(4) { true }, listOf("a", "a", "a", "b"), 2))
        assertEquals(listOf(0, 1, 2, 3), levelOrder(heights, lead, listOf(true, true, true, false), List(4) { "a" }, 2))
    }

    @Test
    fun `level lanes are left alone`() {
        val order = levelOrder(listOf(100f, 100f, 0f, 100f), listOf(false, false, true, false), List(4) { true }, List(4) { 1 }, 2)
        assertEquals(listOf(0, 1, 2, 3), order)
    }

    @Test
    fun `the category chip has a tick, not an underline`() {
        val row = source("ui/overlay/CategoryChipRow.kt")
        assertFalse(row.contains("INDICATOR_WIDTH"))
        assertTrue(row.contains("leadingIcon = if (selected)"))
    }

    @Test
    fun `refresh uses Expressive's indicator and back to top is small`() {
        assertTrue(source("ui/components/PullToRefreshLazyColumn.kt").contains("ContainedLoadingIndicator(progress = pulled"))
        assertTrue(source("ui/pages/ArticleListPage.kt").contains("SmallFloatingActionButton("))
        assertTrue(source("ui/overlay/FeedScaffold.kt").contains("SmallFloatingActionButton("))
    }

    @Test
    fun `read today counts this phone's reading only`() {
        val glance = source("manager/glance/GlanceStateHolder.kt")
        assertTrue(glance.contains("articleRepo.readOnPhone("))
        assertFalse(glance.contains("countReadSince"))
        val repo = source("data/repository/ArticleRepository.kt")
        val bulk = repo.substring(repo.indexOf("suspend fun markAllRead("), repo.indexOf("suspend fun unreadCountBefore("))
        assertFalse("a bulk mark is not reading", bulk.contains("tally(seen"))
    }

    @Test
    fun `the card menu is three groups under the source's name`() {
        val menu = source("ui/overlay/ArticleMenu.kt")
        assertTrue(menu.contains("DropdownMenuPopup("))
        assertTrue(menu.contains("DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, groups.size))"))
        assertTrue(menu.contains("text = sourceName,"))
        val share = menu.indexOf("R.string.share,")
        val more = menu.indexOf("R.string.more_like_this,")
        val hide = menu.indexOf("R.string.hide_source,")
        assertTrue(share in 0 until more && more < hide)
    }

    @Test
    fun `a long press previews the article`() {
        val card = source("ui/overlay/ArticleCard.kt")
        assertEquals(4, Regex("""\.cardClick\(openLabel, onClick\)""").findAll(card).count())
        assertTrue(card.contains("onLongClick = longPress,"))
        assertTrue(card.contains("ArticlePreviewDialog("))
    }

    @Test
    fun `the menu's heading sits inside its first group`() {
        val menu = source("ui/overlay/ArticleMenu.kt")
        val group = menu.indexOf("DropdownMenuGroup(shapes")
        val heading = menu.indexOf("text = sourceName,")
        assertTrue("the source's name floated above the menu", group in 0 until heading)
    }

    @Test
    fun `back to top shows on the way up only`() {
        assertTrue(isTowardsTop(5 to 0, 4 to 300))
        assertTrue(isTowardsTop(5 to 200, 5 to 100))
        assertFalse(isTowardsTop(5 to 100, 5 to 200))
        assertFalse(isTowardsTop(4 to 300, 5 to 0))
        assertTrue(source("ui/pages/ArticleListPage.kt").contains("visible = showFAB && scrollingUp &&"))
        assertTrue(source("ui/overlay/FeedScaffold.kt").contains("visible = showScrollToTop && scrollingUp &&"))
    }

    @Test
    fun `a text-only tile saves from its menu`() {
        val card = source("ui/overlay/ArticleCard.kt")
        assertTrue(card.contains("onSave = if (LocalMenuOffersSave.current) onBookmark else null,"))
        assertTrue(card.contains("FeedEmphasis.Small  -> if (hasImage) 2 else 3"))
        assertTrue(source("ui/overlay/ArticleMenu.kt").contains("R.string.action_save, drawable = R.drawable.ic_whisper_save"))
    }

    @Test
    fun `each feed layout says what it looks like`() {
        val prefs = source("data/content/FeedPreferences.kt")
        listOf("cards", "magazine", "list", "list_pictures", "mosaic").forEach {
            assertTrue(it, prefs.contains("R.string.layout_${it}_summary"))
        }
        assertTrue(source("ui/components/dialog/StringSelectionPrefDialogUI.kt").contains("supporting = pref.descriptions?.get(it.first)"))
    }

    @Test
    fun `every Mosaic tile keeps only its menu, with Save inside`() {
        val card = source("ui/overlay/ArticleCard.kt")
        val tile = card.substring(card.indexOf("fun ArticleMosaicTile("), card.indexOf("private val TILE_LINE_HEIGHT"))
        assertFalse("a save button on a tile", tile.contains("SaveButton("))
        assertEquals(2, Regex("""LocalMenuOffersSave provides true""").findAll(tile).count())
    }

    @Test
    fun `one indicator, from the pull to the end of the update`() {
        val line = source("ui/overlay/SyncFreshnessLine.kt")
        assertFalse("no second indicator in the line", line.contains("ContainedLoadingIndicator("))
        assertTrue(line.contains("anchor.centreY ="))
        val pull = source("ui/components/PullToRefreshLazyColumn.kt")
        assertFalse("not hidden when the update starts", pull.contains("if (refreshing) return\n"))
        assertTrue(pull.contains("y.animateTo(target"))
    }

    @Test
    fun `while updating, the indicator rests on the line under the chips`() {
        val r = com.saulhdev.feeder.ui.components.restingTop(
            anchorCentre = 700f, boxTop = 200f, size = 100f, fallback = 40f,
        )
        assertEquals(450f, r, 0f)
        // Line scrolled away, or above the list: near the top.
        assertEquals(40f, com.saulhdev.feeder.ui.components.restingTop(Float.NaN, 200f, 100f, 40f), 0f)
        assertEquals(40f, com.saulhdev.feeder.ui.components.restingTop(150f, 200f, 100f, 40f), 0f)
    }

    @Test
    fun `after a pull the indicator stays until the update ends, without dropping out`() {
        val pull = source("ui/components/PullToRefreshLazyColumn.kt")
        // Held until the update it started reports finished, not for a flat moment.
        assertTrue(pull.contains("snapshotFlow { running }.first { !it }"))
        // Fade, then follow the finger: one effect, so the order cannot flip.
        assertEquals(1, Regex("""snapshotFlow \{ state.distanceFraction \}""").findAll(pull).count())
        assertFalse(pull.contains("if (leaving) return@collect"))
    }
}
