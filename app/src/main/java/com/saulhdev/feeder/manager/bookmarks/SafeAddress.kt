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
package com.saulhdev.feeder.manager.bookmarks

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/**
 * Whether an address is one this app should go and fetch.
 *
 * The usual reason to block private ranges is a server being tricked into
 * reaching its own network. Here it is the other way round and worse: a phone
 * would be reaching *the reader's own* network. A bookmark pointing at
 * `192.168.1.1` — or a redirect that lands there — would have Whisper probing
 * the household router, the printer and the NAS for `/feed.xml`, from inside
 * the firewall, using the reader's own device to do it.
 *
 * Checked after every redirect rather than only at the start, because a
 * public host that redirects to a private one is the whole trick.
 */
object SafeAddress {

    /** Scheme check only, for an address that has not been resolved yet. */
    fun schemeAllowed(url: String): Boolean {
        val scheme = runCatching { URL(url).protocol?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    /**
     * Whether a resolved address is outside the reader's own network.
     *
     * Loopback, link-local, site-local and the multicast and wildcard ranges
     * are all refused. `isSiteLocalAddress` covers 10/8, 172.16/12 and
     * 192.168/16; the rest are named because it does not.
     */
    fun isPublic(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        if (address.isLinkLocalAddress) return false
        if (address.isSiteLocalAddress) return false
        if (address.isAnyLocalAddress) return false
        if (address.isMulticastAddress) return false

        return when (address) {
            // 100.64/10, carrier-grade NAT — not the reader's LAN, but not
            // somewhere to be probing either.
            is Inet4Address -> {
                val b = address.address
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                !(first == 100 && second in 64..127) && first != 0
            }
            // fc00::/7, unique local addresses: IPv6's private range, which
            // isSiteLocalAddress does not report.
            is Inet6Address -> (address.address[0].toInt() and 0xFE) != 0xFC
            else -> true
        }
    }

    /** The same question for a hostname, resolving it first. */
    fun isPublicHost(host: String): Boolean =
        runCatching { InetAddress.getAllByName(host).all(::isPublic) }.getOrDefault(false)
}

/**
 * Refuses any hop that lands on the reader's own network.
 *
 * A **network** interceptor rather than an application one, and the difference
 * is the whole point: OkHttp follows redirects itself, and an application
 * interceptor runs once for the call while a network interceptor runs for
 * every hop. Checking only the address that was typed would miss a public host
 * that redirects to `192.168.1.1`, which is exactly the trick worth stopping —
 * a bookmark cannot be trusted to stay where it points.
 */
/**
 * Asks for https, when a subscription still says http.
 *
 * Plaintext is refused outright since the September audit, and that is right —
 * article HTML is rendered in a WebView, and anyone on the network can rewrite
 * a page in transit. What it missed is that a great many perfectly healthy
 * feeds are *stored* with an http address, from before their site moved, and
 * their server has answered a redirect to https ever since. Nobody noticed
 * because the redirect did the work.
 *
 * Refusing the connection means that redirect never arrives, so those feeds
 * stopped fetching and appeared as broken — not because the publisher had
 * done anything, but because of the scheme written down years ago. Three of
 * them were visible in a single screen of one test device's source list.
 *
 * So the scheme is rewritten before the connection is attempted: an
 * application interceptor, not a network one, because by the time a network
 * interceptor runs the socket it would have to prevent is already being
 * opened.
 *
 * Nothing is weakened. No plaintext request is made either way; the only
 * change is that a host willing to serve https gets asked. A host that truly
 * only speaks http still fails, and should — that is the decision the audit
 * made, and this does not reverse it.
 *
 * The stored address is left alone. It is what the reader typed or imported,
 * the fetch works regardless, and rewriting somebody's subscription list as a
 * side effect of a network policy is not this interceptor's business.
 */
class UpgradeToHttps : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val upgraded = upgradedUrl(request.url)
        if (upgraded == request.url) return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(upgraded).build())
    }
}

/**
 * The rewrite itself, kept apart from the interceptor so it can be tested.
 *
 * Faking an OkHttp `Chain` means implementing a dozen members that have
 * nothing to do with what is being checked; the question worth asking is only
 * ever "what address comes out", and everything else about the request has to
 * survive — path, query, port. A lost query string turns a working fetch into
 * a 404, which would be worse than the problem this repairs.
 */
internal fun upgradedUrl(url: okhttp3.HttpUrl): okhttp3.HttpUrl =
    if (url.isHttps) url else url.newBuilder().scheme("https").build()

class BlockPrivateNetworks : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val route = chain.connection()?.route()
        if (reachesPrivateNetwork(
                proxy = route?.proxy?.type(),
                connected = route?.socketAddress?.address,
                host = chain.request().url.host,
                isPublicHost = SafeAddress::isPublicHost,
            )
        ) {
            // No address in the message: it travels into the log with the
            // exception, and the log into a shared report.
            throw java.io.IOException("Refusing to fetch a private address")
        }
        return chain.proceed(chain.request())
    }
}

/**
 * Whether a connection reaches the reader's own network.
 *
 * The address judged is the one the socket is connected to. It used to be the
 * host looked up a second time, and a name can answer two lookups differently:
 * public to one, the reader's router to the other. That is DNS rebinding, and
 * in practice certificates already stop it, since everything here is https and
 * a router cannot show a valid certificate for somebody else's domain. This
 * makes the guard right on its own account, and costs a lookup less.
 *
 * Through a proxy the socket is the proxy's, and the target is the proxy's to
 * find, so the name is all there is to judge; which is what happens then.
 */
internal fun reachesPrivateNetwork(
    proxy: java.net.Proxy.Type?,
    connected: java.net.InetAddress?,
    host: String,
    isPublicHost: (String) -> Boolean,
): Boolean =
    if (proxy == java.net.Proxy.Type.DIRECT && connected != null) !SafeAddress.isPublic(connected)
    else !isPublicHost(host)

/**
 * Refuses the reader's own network. The guard every client needs.
 *
 * Applied by hand at each client, three were missed — the weather client, the
 * Google Reader client, and the one in
 * [com.saulhdev.feeder.manager.sync.RssLocalSync] that fetches every
 * subscribed feed on every scheduled sync, which is most of the fetching this
 * app does. A guard that has to be remembered separately at each call site is
 * one that will be forgotten at the next, so there is now one name to apply
 * and a test that fails the build when a client is built without it.
 */
fun okhttp3.OkHttpClient.Builder.refusingPrivateNetworks(): okhttp3.OkHttpClient.Builder =
    addNetworkInterceptor(BlockPrivateNetworks())

/**
 * That, plus asking https of an address still written down as http.
 *
 * The two are separate because they do not belong together everywhere.
 * Refusing a private address is right for every client without exception.
 * Quietly upgrading the scheme is right only where the thing being fetched is
 * public — a feed, an article page, an image — and is deliberately *not*
 * applied to anything carrying a credential, which is refused outright
 * instead. Guessing at https is reasonable for a public article and not
 * reasonable for somebody's password: a sync server written down as `http://`
 * should fail loudly and be corrected, not be silently reached by a route its
 * owner never confirmed.
 *
 * The order is not interchangeable. [UpgradeToHttps] is an application
 * interceptor so the scheme is rewritten before a socket is opened;
 * [BlockPrivateNetworks] is a network interceptor so it runs on every redirect
 * hop rather than only on the address that was typed.
 */
fun okhttp3.OkHttpClient.Builder.onlyPublicHttps(): okhttp3.OkHttpClient.Builder =
    addInterceptor(UpgradeToHttps()).refusingPrivateNetworks()

/**
 * Declares that a client is allowed to reach the reader's own network.
 *
 * A no-op that exists to be read, and to be found. Every other client refuses
 * private addresses, and the test that enforces that finds clients by looking
 * at the source — so an exemption has to be something it can see, or it is
 * indistinguishable from the omission it was written to catch.
 *
 * There is exactly one, and the difference is provenance rather than trust.
 * [BlockPrivateNetworks] exists because a feed address, an article link or a
 * redirect is a string a publisher put in a file, and a hostile one could have
 * the phone reach into its reader's network from inside the firewall. A sync
 * server's address is not that: the reader typed it into a sign-in form and
 * then typed their own password underneath it.
 *
 * Refusing it breaks the thing the feature is for. Self-hosted FreshRSS and
 * Miniflux are the whole use case — the network security config says so in as
 * many words — and they live at `192.168.1.50`, at `nas.local`, and behind
 * Tailscale on `100.64/10`, all of which are refused. Guarding this client
 * protects nobody from anything and unsupports self-hosting, which is a poor
 * trade for a reader who went to the trouble of running their own server.
 */
fun okhttp3.OkHttpClient.Builder.allowingPrivateNetworks(): okhttp3.OkHttpClient.Builder = this
