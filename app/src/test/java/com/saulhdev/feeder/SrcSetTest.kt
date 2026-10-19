package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageCandidates
import com.saulhdev.feeder.utils.srcSetCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class SrcSetTest {

    private val sora = "https://soranews24.com/uploads/5_fc9942.jpg 1200w, " +
        "https://soranews24.com/uploads/5_fc9942.jpg?resize=150,113 150w, " +
        "https://soranews24.com/uploads/5_fc9942.jpg?resize=640,480 640w, " +
        "https://soranews24.com/uploads/5_fc9942.jpg?resize=1024,768 1024w"

    @Test
    fun `commas inside an address do not split it`() {
        assertEquals(
            listOf(
                "https://soranews24.com/uploads/5_fc9942.jpg" to "1200w",
                "https://soranews24.com/uploads/5_fc9942.jpg?resize=150,113" to "150w",
                "https://soranews24.com/uploads/5_fc9942.jpg?resize=640,480" to "640w",
                "https://soranews24.com/uploads/5_fc9942.jpg?resize=1024,768" to "1024w",
            ),
            srcSetCandidates(sora),
        )
    }

    @Test
    fun `the chosen size is a whole address`() {
        val chosen = ImageCandidates("https://soranews24.com/story/", sora, "").getBestImageForMaxSize(640, 1f)
        assertEquals("https://soranews24.com/uploads/5_fc9942.jpg?resize=640,480", chosen)
    }

    @Test
    fun `plain lists, densities and missing descriptors still read`() {
        assertEquals(
            listOf("a.jpg" to "1x", "b.jpg" to "2x"),
            srcSetCandidates("a.jpg 1x, b.jpg 2x"),
        )
        assertEquals(listOf("a.jpg" to null, "b.jpg" to "640w"), srcSetCandidates("a.jpg, b.jpg 640w"))
        assertEquals(listOf("a.jpg" to null), srcSetCandidates(" a.jpg "))
        assertEquals(emptyList<Pair<String, String?>>(), srcSetCandidates(""))
    }
}
