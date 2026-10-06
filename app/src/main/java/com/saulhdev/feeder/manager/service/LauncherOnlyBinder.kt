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
package com.saulhdev.feeder.manager.service

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

private const val TAG = "LauncherOnlyBinder"

/**
 * Passes calls to the overlay only when the kernel says a launcher made them.
 *
 * [OverlayService] must be exported — a launcher in another process is the
 * whole point — and the check at `onBind` reads the caller's identity out of
 * the bind Uri, because `Binder.getCallingUid()` is meaningless there:
 * `onBind` runs on the main thread, outside any binder transaction, and
 * answers with this process's own uid.
 *
 * But a Uri is written by whoever sends it. That check asks the package
 * manager whether the named package really belongs to the named uid, which
 * proves the pair is genuine and proves nothing about the sender: any app can
 * write Lawnchair's package and Lawnchair's uid into a Uri and be admitted.
 * The comment at that call site describes precisely the attack it does not
 * stop — an app with no permissions binding this, handing over a window token
 * of its own, and having the reader's feed rendered inside it: headlines,
 * sources, and what they have read.
 *
 * One layer down the identity is real. Inside `onTransact` the uid is filled
 * in by the kernel from the sending process and cannot be chosen by the
 * sender, so that is where the question is asked properly. The Uri check stays
 * as a cheap first filter; this is the one that has to hold.
 *
 * Wrapping rather than replacing, because the binder being guarded belongs to
 * the GSA overlay library. Nothing here interprets the protocol — it forwards
 * a transaction or refuses it.
 */
/**
 * The decision, with no Android type anywhere in it.
 *
 * Kept apart from the binder that applies it so it can be tested. A `Binder`
 * subclass cannot even be constructed in a JVM unit test — the framework
 * classes are stubs that throw — and `onTransact` wants a Parcel and a live
 * transaction on top of that. The rule needs none of it, and a security rule
 * that can only be checked by hand on a phone is one that stops being checked.
 */
internal class LauncherGate(private val isLauncher: (Int) -> Boolean) {

    /**
     * The one uid already checked, so the check is not repeated per call.
     *
     * An overlay being scrolled is a steady stream of transactions, and
     * `queryIntentActivities` walks every launcher installed; doing that per
     * frame would make the guard into the performance problem.
     *
     * Volatile because transactions arrive on binder pool threads. A race here
     * costs a duplicate check and nothing else: two threads may both verify
     * the same uid, and both write the same value.
     */
    @Volatile
    private var allowedUid = UNKNOWN_UID

    /**
     * Whether a transaction from this uid may go through.
     *
     * A uid admitted once is admitted again without re-asking; a different one
     * is checked properly however many times the first was accepted, so the
     * cache can never widen the answer.
     */
    fun admits(uid: Int): Boolean {
        if (uid == allowedUid) return true
        if (!isLauncher(uid)) return false
        allowedUid = uid
        return true
    }

    private companion object {
        /** No uid has been admitted yet. Not a uid any process can hold. */
        const val UNKNOWN_UID = -1
    }
}

class LauncherOnlyBinder(
    private val delegate: IBinder,
    context: Context,
    /**
     * Whether a uid belongs to a launcher.
     *
     * Injected so [admits] — which is the security decision, caching and all
     * — can be exercised without a device. `onTransact` needs a Parcel and a
     * real binder transaction; the rule it applies needs neither, and a rule
     * that can only be checked by hand on a phone is a rule that stops being
     * checked.
     */
    private val isLauncher: (Int) -> Boolean =
        { LauncherLink.uidIsALauncher(context, it) },
) : Binder() {

    private val gate = LauncherGate(isLauncher)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // getCallingUid() is filled in by the kernel here, unlike in onBind.
        val uid = getCallingUid()
        if (!gate.admits(uid)) {
            // Refused rather than thrown: a SecurityException would cross back
            // into the caller, and an app probing this should learn nothing
            // from the shape of the failure.
            Log.w(TAG, "Refusing a transaction from uid $uid: not a launcher")
            return false
        }
        return delegate.transact(code, data, reply, flags)
    }

    /**
     * The wrapped binder's descriptor, not this class's.
     *
     * A caller checks the interface it was handed before using it. Answering
     * with our own name would fail that check and the overlay would simply
     * never start — a guard that breaks the feature it protects.
     */
    override fun getInterfaceDescriptor(): String? = delegate.interfaceDescriptor

    override fun pingBinder(): Boolean = delegate.pingBinder()

    override fun isBinderAlive(): Boolean = delegate.isBinderAlive

    private companion object {
        /** No uid has been admitted yet. Not a uid any process can hold. */
        const val UNKNOWN_UID = -1
    }
}
