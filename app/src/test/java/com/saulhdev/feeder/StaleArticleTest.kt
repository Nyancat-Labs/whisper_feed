package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader fetches the article it was asked for, not the one before it.
 *
 * Opening an article showed the previous one's text under the right headline:
 * the bar said Bored Panda, the page underneath was an io9 piece read minutes
 * earlier. It survived closing and reopening, which is what made it look like
 * a rendering fault rather than a fetch.
 *
 * Two sources of truth disagree for a moment after every tap. `articleId` is
 * the navigation argument and changes instantly; the article comes from a
 * database flow and still holds what was open before. The effect that fetches
 * the readable page keyed on the id and the link alone, so it fired on the
 * pair (new id, previous article's link) — which has never been a real
 * article — and wrote the previous page into the new one's file.
 *
 * Permanently, because loadFullText returns early when the file exists. The
 * correct fetch that followed a moment later found the file already there and
 * did nothing.
 *
 * Both halves are pinned. The guard is one condition that looks like a
 * redundant null check, and the early return it protects against is in another
 * file entirely.
 */
class StaleArticleTest {

    private val page = File(
        "src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt"
    ).readText()
    private val viewModel = File(
        "src/main/java/com/saulhdev/feeder/viewmodels/ArticleViewModel.kt"
    ).readText()

    /** The effect that fetches the readable article. */
    private val effect: String by lazy {
        val at = page.indexOf("LaunchedEffect(articleId,")
        assertTrue("the full-text effect is gone", at > 0)
        page.substring(at, page.indexOf("\n    }", at))
    }

    @Test
    fun `the link is only used when it belongs to the article being opened`() {
        assertTrue(
            "the effect no longer checks that the state caught up with the id",
            effect.contains("article.uuid != articleId"),
        )
        val guard = effect.indexOf("article.uuid != articleId")
        val fetch = effect.indexOf("loadFullText(")
        assertTrue("the effect no longer fetches", fetch > 0)
        assertTrue("the fetch happens before the guard", guard < fetch)
        assertTrue(
            "the guard does not stop the fetch",
            effect.substring(guard, fetch).contains("return@LaunchedEffect"),
        )
    }

    @Test
    fun `the effect wakes when the article catches up, not only when the id changes`() {
        // Keyed on the id alone, the effect would never re-run once the state
        // arrived, and the article would show nothing rather than the wrong
        // thing — a different bug with the same cause.
        assertTrue(
            "the effect no longer observes which article the state holds",
            effect.startsWith("LaunchedEffect(articleId, state?.article?.uuid"),
        )
    }

    @Test
    fun `a cached page is still trusted, which is why the guard has to hold`() {
        // This early return is what made the mix-up permanent. It is correct —
        // refetching a stored article on every open would be worse — so the
        // guard above is the only thing standing between a wrong file and a
        // reader that shows it for ever.
        val at = viewModel.indexOf("fun loadFullText(")
        assertTrue("loadFullText is gone", at > 0)
        val body = viewModel.substring(at, viewModel.indexOf("\n    }", at))
        assertTrue(
            "loadFullText no longer short-circuits on an existing file; if that " +
                    "changed deliberately, this test and its comment are stale",
            body.contains("blobFullFile(id, filesDir).isFile"),
        )
    }

    @Test
    fun `pages cached before the fix are thrown away once`() {
        // The fix stops new ones being misfiled. It cannot tell which existing
        // files are already wrong, because a correct page and a misfiled one
        // are the same kind of file.
        val app = File("src/main/java/com/saulhdev/feeder/NeoApp.kt").readText()
        assertTrue(
            "nothing clears the pages cached while the mix-up was live",
            app.contains("purgeMisfiledFullText"),
        )
        val at = app.indexOf("private fun purgeMisfiledFullText")
        val body = app.substring(at, app.indexOf("\n    }", at))
        assertTrue("the purge no longer targets full-text files", body.contains(".full.html.gz"))
        assertTrue(
            "the purge does not record itself, so it runs on every launch",
            body.contains("fullTextPurged.setValue(true)"),
        )
        assertTrue(
            "the purge no longer runs at most once",
            body.contains("if (prefs.fullTextPurged.getValue()) return@launch"),
        )
        assertTrue("the purge is never called", app.contains("        purgeMisfiledFullText()"))
    }
}
