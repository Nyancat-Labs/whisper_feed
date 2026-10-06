package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.problemFrom
import com.saulhdev.feeder.manager.sync.greader.problemFromStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Turning a failed sign-in into something a reader can act on.
 *
 * The account screen used to print the exception's own message. "Trust anchor
 * for certification path not found" is true, and it is a sentence about a Java
 * class hierarchy handed to somebody who wanted their feeds — it says neither
 * what is wrong nor what to do, and it looks exactly as baffling as a typo in
 * the hostname does.
 */
class AccountProblemTest {

    @Test
    fun `an untrusted certificate is recognised through its wrappers`() {
        // The shape Android actually throws for a self-signed certificate,
        // and the reason this walks the chain at all: only the outermost
        // exception is visible without it, and on its own it is an
        // SSLHandshakeException, which could be half a dozen things.
        val thrown = SSLHandshakeException("Handshake failed").initCause(
            CertificateException(
                "Trust anchor for certification path not found",
                CertPathValidatorException("Trust anchor for certification path not found"),
            )
        )
        assertEquals(AccountProblem.CERTIFICATE, problemFrom(thrown))
    }

    @Test
    fun `a certificate for the wrong name is a certificate problem`() {
        val thrown = SSLPeerUnverifiedException("Hostname nas.local not verified")
        assertEquals(AccountProblem.CERTIFICATE, problemFrom(thrown))
    }

    @Test
    fun `a name DNS does not know is told apart from a server not answering`() {
        // These two are the pair most worth separating. One means check the
        // spelling; the other means check the server is running. The old
        // screen showed raw text for both and they read the same.
        assertEquals(
            AccountProblem.HOST_NOT_FOUND,
            problemFrom(UnknownHostException("Unable to resolve host \"frshrss.example.com\"")),
        )
        assertEquals(
            AccountProblem.REFUSED,
            problemFrom(ConnectException("Failed to connect to /192.168.1.50:8443")),
        )
    }

    @Test
    fun `a timeout is not mistaken for a refusal`() {
        // SocketTimeoutException is an InterruptedIOException is an
        // IOException, and ConnectException is a SocketException is an
        // IOException. Check them in the wrong order and all of this collapses
        // into one unhelpful answer.
        assertEquals(AccountProblem.TIMEOUT, problemFrom(SocketTimeoutException("timeout")))
    }

    @Test
    fun `the platform refusing cleartext says so`() {
        val thrown = UnknownServiceException(
            "CLEARTEXT communication to example.com not permitted by network security policy"
        )
        assertEquals(AccountProblem.CLEARTEXT, problemFrom(thrown))
    }

    @Test
    fun `a handshake that failed for a non-certificate reason is not blamed on one`() {
        // A connection reset partway through a handshake arrives as an
        // SSLHandshakeException with nothing about certificates in it.
        // Treating the whole SSLException family as a certificate problem
        // would send the reader off to inspect a certificate that was fine.
        val thrown = SSLHandshakeException("Connection closed by peer")
        assertEquals(AccountProblem.UNKNOWN, problemFrom(thrown))
    }

    @Test
    fun `an ordinary IO failure falls through rather than being guessed at`() {
        assertEquals(AccountProblem.UNKNOWN, problemFrom(IOException("unexpected end of stream")))
        assertEquals(AccountProblem.UNKNOWN, problemFrom(null))
    }

    @Test
    fun `a cause chain that loops does not hang`() {
        // Bounded because a chain can be made to point at itself, and this
        // runs on a failed sign-in with nobody watching it.
        val a = IOException("a")
        val b = IOException("b", a)
        a.initCause(b)
        assertEquals(AccountProblem.UNKNOWN, problemFrom(a))
    }

    @Test
    fun `a wrong password is not reported as a server fault`() {
        assertEquals(AccountProblem.CREDENTIALS, problemFromStatus(401))
        assertEquals(AccountProblem.CREDENTIALS, problemFromStatus(403))
    }

    @Test
    fun `a missing API path is told apart from everything else`() {
        // The likeliest real mistake: people paste the address of the FreshRSS
        // web interface rather than /api/greader.php. Server up, certificate
        // fine, password probably right, and "The server said 404" gave them
        // nothing to work with.
        assertEquals(AccountProblem.NOT_FOUND, problemFromStatus(404))
        assertEquals(AccountProblem.NOT_FOUND, problemFromStatus(405))
    }

    @Test
    fun `a server-side fault is named as theirs rather than the reader's`() {
        assertEquals(AccountProblem.SERVER_ERROR, problemFromStatus(500))
        assertEquals(AccountProblem.SERVER_ERROR, problemFromStatus(502))
        assertEquals(AccountProblem.SERVER_ERROR, problemFromStatus(503))
    }

    @Test
    fun `an unexpected status keeps its number rather than being invented`() {
        assertEquals(AccountProblem.UNKNOWN, problemFromStatus(418))
    }
}
