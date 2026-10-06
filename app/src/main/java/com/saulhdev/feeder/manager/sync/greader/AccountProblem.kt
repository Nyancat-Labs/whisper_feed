/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.manager.sync.greader

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Why connecting to a sync server did not work, as something to say about it.
 *
 * The account screen used to show whatever the exception said. That is true
 * and useless: "Trust anchor for certification path not found" is a sentence
 * about a Java class hierarchy, offered to somebody who wanted their feeds. It
 * named neither what was wrong nor what to do, and the four most likely causes
 * — a typo, a wrong path, a wrong password, a certificate the phone will not
 * accept — were indistinguishable from one another and from a server being
 * down.
 *
 * An enum rather than a string, and it lives here rather than in the UI,
 * because [GoogleReaderApi] does the protocol and nothing else — including no
 * deciding how to word things. The wording, and its translations, belong to
 * the screen.
 */
enum class AccountProblem {
    /** Not a web address at all. */
    BAD_ADDRESS,

    /** The certificate is self-signed, expired, or for a different name. */
    CERTIFICATE,

    /** DNS knows nothing about that name. */
    HOST_NOT_FOUND,

    /** The address resolves, but nothing is listening. */
    REFUSED,

    /** It answered too slowly, or not at all. */
    TIMEOUT,

    /** An `http://` address, which the platform refuses outright. */
    CLEARTEXT,

    /** The server understood and said no: wrong username or password. */
    CREDENTIALS,

    /** The server is there but the path is not the API. */
    NOT_FOUND,

    /** The server answered with something in the 5xx range. */
    SERVER_ERROR,

    /** A reply that was not a refusal and did not contain a token either. */
    NO_TOKEN,

    /** A credential that used to work has stopped being accepted. */
    SIGNED_OUT,

    /**
     * The sign-in was sent on to a different server. Not followed: a 307 or
     * 308 re-sends the whole request, the password with it, to wherever the
     * first server points.
     */
    MOVED,

    /** Everything else, which keeps the original text rather than losing it. */
    UNKNOWN,
}

/**
 * How deep to follow a `cause` chain.
 *
 * OkHttp and the TLS stack both wrap: the exception that reaches the caller
 * for an untrusted certificate is an SSLHandshakeException around a
 * CertificateException around a CertPathValidatorException, and only the
 * outermost is visible without walking. Bounded rather than unbounded because
 * a cause chain can be made to loop, and this runs on a failed sign-in where
 * nobody is watching it.
 */
private const val MAX_CAUSE_DEPTH = 8

/** The whole chain, outermost first, so a nested cause can be recognised. */
private fun Throwable.chain(): List<Throwable> {
    val out = mutableListOf<Throwable>()
    var t: Throwable? = this
    while (t != null && out.size < MAX_CAUSE_DEPTH && out.none { it === t }) {
        out += t
        t = t.cause
    }
    return out
}

/** Which problem a thrown exception describes. */
fun problemFrom(cause: Throwable?): AccountProblem {
    val chain = cause?.chain() ?: return AccountProblem.UNKNOWN

    // Order is by specificity, not by likelihood. ConnectException and
    // UnknownHostException are both IOExceptions, SocketTimeoutException is an
    // InterruptedIOException, and a check in the wrong order would answer
    // "unknown network error" for all three.
    return when {
        chain.any { it is UnknownServiceException } ||
                chain.any { it.message?.contains("CLEARTEXT", ignoreCase = true) == true } ->
            AccountProblem.CLEARTEXT

        // Only when something in the chain is genuinely about a certificate.
        // A bare SSLException is not: a connection reset partway through a
        // handshake arrives as one, and answering "could not verify that
        // server's certificate" would send the reader to inspect a
        // certificate that was never the problem. Android puts a
        // CertificateException in the chain for an untrusted or expired one,
        // and SSLPeerUnverifiedException is the name mismatch, so the real
        // cases are still caught by name rather than by family.
        chain.any { it is CertificateException } ||
                chain.any { it is SSLPeerUnverifiedException } ||
                chain.any { it.javaClass.name.startsWith("java.security.cert.") } ->
            AccountProblem.CERTIFICATE

        chain.any { it is UnknownHostException } -> AccountProblem.HOST_NOT_FOUND
        chain.any { it is SocketTimeoutException } -> AccountProblem.TIMEOUT
        chain.any { it is ConnectException } -> AccountProblem.REFUSED
        chain.any { it is IOException } -> AccountProblem.UNKNOWN
        else -> AccountProblem.UNKNOWN
    }
}

/**
 * Which problem an HTTP status describes.
 *
 * 404 earns its own case because it is the likeliest real mistake and the one
 * the old message hid completely. A FreshRSS address ends in
 * `/api/greader.php`, people paste the address of the web interface, and "The
 * server said 404" gave them nothing to work with — the server is up, the
 * certificate is fine, the password may well be right, and the only thing
 * wrong is a missing path segment.
 */
fun problemFromStatus(status: Int): AccountProblem = when (status) {
    401, 403 -> AccountProblem.CREDENTIALS
    404, 405 -> AccountProblem.NOT_FOUND
    in 500..599 -> AccountProblem.SERVER_ERROR
    else -> AccountProblem.UNKNOWN
}
