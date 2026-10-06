package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi.AuthResult
import com.saulhdev.feeder.manager.sync.greader.serverCandidates
import com.saulhdev.feeder.manager.sync.greader.signInAtAny
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Signing in with the address people remember: the web one.
 *
 * FreshRSS answers at `/api/greader.php`, which nobody remembers and which a
 * password manager does not keep. The first sign-in on a real server went in
 * as `https://rss.…` with no path.
 */
class ServerPathTest {

    @Test
    fun `a bare address is tried as given, then with the FreshRSS path`() {
        assertEquals(
            listOf("https://rss.example.com", "https://rss.example.com/api/greader.php"),
            serverCandidates("https://rss.example.com/"),
        )
    }

    @Test
    fun `the address of FreshRSS's own pages becomes its API`() {
        assertEquals(
            "https://rss.example.com/api/greader.php",
            serverCandidates("https://rss.example.com/i/").last(),
        )
        assertEquals(
            "https://rss.example.com/api/greader.php",
            serverCandidates("https://rss.example.com/api").last(),
        )
    }

    @Test
    fun `an address that already names the API is tried alone`() {
        assertEquals(
            listOf("https://rss.example.com/api/greader.php"),
            serverCandidates("https://rss.example.com/api/greader.php"),
        )
    }

    @Test
    fun `a FreshRSS in a folder keeps its folder`() {
        assertEquals(
            "https://example.com/freshrss/api/greader.php",
            serverCandidates("https://example.com/freshrss").last(),
        )
    }

    @Test
    fun `the second address is used when the first has nothing there`() = runBlocking {
        val tried = mutableListOf<String>()
        val (address, result) = signInAtAny(serverCandidates("https://rss.example.com")) {
            tried += it
            if (it.endsWith("greader.php")) AuthResult.Success("t") else AuthResult.Failed(AccountProblem.NOT_FOUND)
        }
        assertEquals("https://rss.example.com/api/greader.php", address)
        assertEquals(AuthResult.Success("t"), result)
        assertEquals(2, tried.size)
    }

    @Test
    fun `the first address is kept when it accepts`() = runBlocking {
        val tried = mutableListOf<String>()
        val (address, _) = signInAtAny(serverCandidates("https://miniflux.example.com")) {
            tried += it
            AuthResult.Success("t")
        }
        assertEquals("https://miniflux.example.com", address)
        assertEquals(1, tried.size)
    }

    @Test
    fun `an unreachable host is not tried twice`() = runBlocking {
        val tried = mutableListOf<String>()
        val (_, result) = signInAtAny(serverCandidates("https://nowhere.example.com")) {
            tried += it
            AuthResult.Failed(AccountProblem.HOST_NOT_FOUND)
        }
        assertEquals(1, tried.size)
        assertEquals(AccountProblem.HOST_NOT_FOUND, (result as AuthResult.Failed).problem)
    }

    @Test
    fun `a wrong password beats nothing there`() = runBlocking {
        // The root has no API, the path does and refuses the password: the
        // reader needs to hear about the password, not the path.
        val (_, result) = signInAtAny(serverCandidates("https://rss.example.com")) {
            if (it.endsWith("greader.php")) AuthResult.Failed(AccountProblem.CREDENTIALS)
            else AuthResult.Failed(AccountProblem.NOT_FOUND)
        }
        assertEquals(AccountProblem.CREDENTIALS, (result as AuthResult.Failed).problem)
    }

    @Test
    fun `nothing there anywhere reports the address as given`() = runBlocking {
        val (address, result) = signInAtAny(serverCandidates("https://rss.example.com")) {
            AuthResult.Failed(AccountProblem.NOT_FOUND, detail = it)
        }
        assertEquals("https://rss.example.com", address)
        assertEquals(AccountProblem.NOT_FOUND, (result as AuthResult.Failed).problem)
    }

    @Test
    fun `the sign-in fields are named for autofill`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt").readText()
        assertTrue(page.contains("contentType = ContentType.Username"))
        assertTrue(page.contains("contentType = ContentType.Password"))
        assertTrue(page.contains("R.string.account_server_hint"))
        val model = File("src/main/java/com/saulhdev/feeder/viewmodels/AccountViewModel.kt").readText()
        assertTrue(model.contains("account.signIn(address,"))
    }
}
