package com.saulhdev.feeder

import com.saulhdev.feeder.manager.service.LauncherGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Only a launcher may drive the overlay.
 *
 * OverlayService is exported, because a launcher in another process is the
 * whole point. The check at `onBind` reads the caller's identity out of the
 * bind Uri — it has to, since `Binder.getCallingUid()` there answers with this
 * process's own uid — and a Uri is written by whoever sends it. That check
 * confirms the named package really belongs to the named uid, which proves the
 * pair is genuine and nothing at all about the sender: any app could write
 * Lawnchair's package and Lawnchair's uid and be let in.
 *
 * What that bought an attacker is written in the comment at that call site: an
 * app holding no permissions binds the service, hands over a window token of
 * its own, and has the reader's feed drawn inside it — headlines, sources, and
 * what they have read.
 *
 * So the real check happens one layer down, in the transaction, where the uid
 * comes from the kernel. `onTransact` needs a Parcel and a live binder; the
 * rule it applies needs neither, and it is the rule that matters.
 */
class LauncherGateTest {

    /** Records what was asked, so caching can be observed rather than assumed. */
    private class Answers(private val launchers: Set<Int>) : (Int) -> Boolean {
        val asked = mutableListOf<Int>()
        override fun invoke(uid: Int): Boolean {
            asked += uid
            return uid in launchers
        }
    }

    private fun gate(answers: Answers) = LauncherGate(answers)

    @Test
    fun `a launcher is admitted`() {
        val answers = Answers(setOf(LAUNCHER))
        assertTrue(gate(answers).admits(LAUNCHER))
    }

    @Test
    fun `anything that is not a launcher is refused`() {
        val answers = Answers(setOf(LAUNCHER))
        assertFalse("an ordinary app reached the overlay", gate(answers).admits(OTHER))
    }

    @Test
    fun `an admitted uid is not re-checked on every call`() {
        // An overlay being scrolled is a stream of transactions, and the check
        // walks every launcher installed. Per frame, the guard becomes the
        // performance problem it was added beside.
        val answers = Answers(setOf(LAUNCHER))
        val subject = gate(answers)
        repeat(50) { assertTrue(subject.admits(LAUNCHER)) }
        assertEquals("the uid was checked more than once", 1, answers.asked.size)
    }

    @Test
    fun `caching one uid never admits another`() {
        // The cache is the part most likely to be got wrong, because getting
        // it wrong looks like working code: the launcher still works, and the
        // hole only opens for whoever transacts second.
        val answers = Answers(setOf(LAUNCHER))
        val subject = gate(answers)
        assertTrue(subject.admits(LAUNCHER))
        assertFalse("a second uid rode in on the first one's check", subject.admits(OTHER))
        assertFalse(subject.admits(OTHER))
        assertEquals(
            "the refused uid stopped being checked",
            listOf(LAUNCHER, OTHER, OTHER),
            answers.asked,
        )
    }

    @Test
    fun `a refusal does not poison a later legitimate caller`() {
        val answers = Answers(setOf(LAUNCHER))
        val subject = gate(answers)
        assertFalse(subject.admits(OTHER))
        assertTrue("the launcher was locked out by an earlier probe", subject.admits(LAUNCHER))
    }

    @Test
    fun `the service hands out the guarded binder, not the raw one`() {
        // The whole guard is one wrapping call. Removed, everything above
        // still passes and the service is wide open again.
        val service = File(
            "src/main/java/com/saulhdev/feeder/manager/service/OverlayService.kt"
        ).readText()
        assertTrue(
            "OverlayService returns the overlay's binder unguarded",
            service.contains("LauncherOnlyBinder("),
        )
        assertFalse(
            "OverlayService still returns onBind's result directly",
            service.contains("return overlaysController.onBind(intent)"),
        )
    }

    @Test
    fun `the uid check does not read the caller's own claim`() {
        // uidIsALauncher must derive everything from the uid it is given. If
        // it ever consults the Intent again, it inherits the weakness the
        // transaction-level check exists to close.
        val link = File(
            "src/main/java/com/saulhdev/feeder/manager/service/LauncherLink.kt"
        ).readText()
        val at = link.indexOf("fun uidIsALauncher(")
        assertTrue("uidIsALauncher is gone", at > 0)
        val body = link.substring(at, link.indexOf("\n    }", at))
        assertFalse(
            "the uid check reads the caller-supplied Intent",
            body.contains("intent") || body.contains("Intent.data") || body.contains("data.host"),
        )
    }

    private companion object {
        const val LAUNCHER = 10123
        const val OTHER = 10456
    }
}
