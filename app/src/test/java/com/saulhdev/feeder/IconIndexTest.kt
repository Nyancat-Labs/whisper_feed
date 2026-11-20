package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * docs/ICONS.md says where every icon and image is used (owner, 20 November:
 * "if we change the pin icon, we know all its locations"). This fails when
 * the code and the index part ways: an icon added or removed, or a file that
 * starts or stops using one. The fix is to re-run scripts/icon-index.py.
 */
class IconIndexTest {

    private val main = File("src/main")
    private val java = File(main, "java")
    private val icons = File(java, "com/saulhdev/feeder/ui/icons/phosphor")
    private val index = File("../docs/ICONS.md").readText()
    private val hint = " (re-run: python3 -I scripts/icon-index.py <phosphor packages>)"

    /** Heading to the files listed under it (paths without line numbers). */
    private fun indexed(prefix: String): Map<String, Set<String>> =
        index.split("\n### ").drop(1).filter { it.startsWith(prefix) }.associate { block ->
            val name = block.lineSequence().first().removePrefix(prefix).trim()
            val files = Regex("""^\s+- `([^`:]+):\d+`""", RegexOption.MULTILINE)
                .findAll(block).map { it.groupValues[1].removePrefix("app/") }.toSet()
            name to files
        }

    private fun codeFiles() = java.walkTopDown()
        .filter { (it.extension == "kt" || it.extension == "java") && !it.path.startsWith(icons.path) }
        .toList()

    private fun users(pattern: Regex, files: List<File>): Set<String> =
        files.filter { pattern.containsMatchIn(it.readText()) }.map { it.path }.toSet()

    @Test
    fun `every icon is in the index, with every file that uses it`() {
        val names = icons.listFiles()!!.filter { it.extension == "kt" }.mapNotNull {
            Regex("""val Phosphor\.(\w+)""").find(it.readText())?.groupValues?.get(1)
        }.toSet()
        val listed = indexed("Phosphor.")
        assertEquals("icons in the folder and in the index$hint", names, listed.keys)
        val code = codeFiles()
        for (name in names) {
            assertEquals(
                "files using Phosphor.$name$hint",
                users(Regex("""Phosphor\.$name\b"""), code),
                listed.getValue(name),
            )
        }
    }

    @Test
    fun `every image is in the index, with every file that uses it`() {
        val res = File(main, "res")
        val images = res.listFiles()!!.filter { it.name.startsWith("drawable") || it.name.startsWith("mipmap") }
            .flatMap { folder ->
                val kind = if (folder.name.startsWith("mipmap")) "mipmap" else "drawable"
                folder.listFiles()!!.map { "$kind/${it.nameWithoutExtension}" }
            }.toSet()
        val drawables = indexed("drawable/").mapKeys { "drawable/${it.key}" }
        val mipmaps = indexed("mipmap/").mapKeys { "mipmap/${it.key}" }
        val listed = drawables + mipmaps
        assertEquals("images in res and in the index$hint", images, listed.keys)
        val code = codeFiles()
        val xml = res.walkTopDown().filter { it.extension == "xml" }.toList() + File(main, "AndroidManifest.xml")
        for (image in images) {
            val (kind, stem) = image.split("/")
            val used = users(Regex("""R\.$kind\.$stem\b"""), code) + users(Regex("""@$kind/$stem\b"""), xml)
            assertEquals("files using $image$hint", used, listed.getValue(image))
        }
    }
}
