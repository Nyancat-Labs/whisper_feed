package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every OkHttpClient this app builds refuses private addresses and plaintext.
 *
 * This one reads the source rather than the behaviour, which is unusual and is
 * the point. The guards were applied by hand at each client and three were
 * missed — the weather client, the Google Reader client, and the one in
 * RssLocalSync that fetches every subscribed feed on every scheduled sync,
 * which is the great majority of the fetching this app does. Each of those was
 * written correctly against its own file; what nobody could see from inside any
 * one file was the set.
 *
 * So the invariant is about the set, and the check has to be too: find every
 * client built anywhere in the app and require the guard on each. A unit test
 * of interceptor behaviour would have passed happily the whole time the gap was
 * open, because the interceptors were never the thing that was wrong.
 */
class GuardedClientsTest {

    private val mainSources = File("src/main/java")

    /** What a client may say about the reader's own network. All three count. */
    private val GUARDS = listOf(
        "onlyPublicHttps()",
        "refusingPrivateNetworks()",
        "allowingPrivateNetworks()",
    )

    private fun kotlinFiles(): List<File> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Source with `//` and block comments removed, so prose never matches. */
    private fun withoutComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    /** The chain from `OkHttpClient.Builder()` up to and including `.build()`. */
    private fun builderChains(source: String): List<String> {
        val chains = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = source.indexOf("OkHttpClient.Builder()", from)
            if (start < 0) break
            val end = source.indexOf(".build()", start)
            chains += if (end < 0) source.substring(start) else source.substring(start, end + 8)
            from = start + 1
        }
        return chains
    }

    @Test
    fun `the sources are actually being read`() {
        // A path mistake would make every assertion below vacuous.
        assertTrue("no Kotlin sources found under $mainSources", kotlinFiles().size > 50)
    }

    @Test
    fun `every client built in the app declares what it does about private addresses`() {
        // One of three names, and the third is a no-op that exists to be
        // found: an exemption has to be visible to this test, or it cannot be
        // told apart from the omission the test was written to catch.
        val undeclared = kotlinFiles().flatMap { file ->
            builderChains(withoutComments(file.readText()))
                .filter { chain -> GUARDS.none(chain::contains) }
                .map { file.path }
        }
        assertEquals(
            "these clients declare neither guard nor exemption: $undeclared",
            emptyList<String>(),
            undeclared,
        )
    }

    @Test
    fun `only the sync client is exempt`() {
        // The exemption turns the guard off, so it must not spread quietly.
        // It is justified by provenance and nothing else: this address was
        // typed into a sign-in form by the reader, where every other address
        // the app fetches came out of somebody else's XML.
        val exempt = kotlinFiles()
            .filter { withoutComments(it.readText()).contains("allowingPrivateNetworks()") }
            .map { it.name }
            .sorted()
        assertEquals(listOf("GoogleReaderApi.kt", "SafeAddress.kt"), exempt)
    }

    @Test
    fun `the client that syncs every feed is guarded`() {
        // Named on its own because it is the one that was missed, and because
        // a regression here is the expensive one: it fetches unattended, in
        // the background, for every subscription at once.
        val sync = withoutComments(
            File(mainSources, "com/saulhdev/feeder/manager/sync/RssLocalSync.kt").readText()
        )
        assertTrue(sync.contains("onlyPublicHttps()"))
    }

    @Test
    fun `the sync server client is not quietly upgraded to https`() {
        // The scheme is corrected at sign-in instead, in the field, where the
        // reader can see it happen before their password is sent.
        val greader = withoutComments(
            File(
                mainSources,
                "com/saulhdev/feeder/manager/sync/greader/GoogleReaderApi.kt",
            ).readText()
        )
        assertTrue(
            "the sync client must not take the https upgrade",
            !greader.contains("onlyPublicHttps()"),
        )
    }

    @Test
    fun `no TLS-disabling trust manager survives anywhere`() {
        // There used to be a `trustAllCerts()` extension — an X509TrustManager
        // whose check methods were empty and a hostname verifier that returned
        // true — reachable through a constructor default of `true`. Nothing
        // called it, which is exactly why it went unnoticed for so long.
        //
        // Comments are stripped before the search, because the file that
        // explains the removal names the thing it removed and would otherwise
        // report itself.
        val offenders = kotlinFiles().filter { file ->
            val code = withoutComments(file.readText())
            code.contains("X509TrustManager") ||
                    code.contains("sslSocketFactory(") ||
                    code.contains("hostnameVerifier")
        }
        assertEquals(emptyList<File>(), offenders)
    }
}
