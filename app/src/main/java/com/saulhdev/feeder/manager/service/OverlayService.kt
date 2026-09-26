package com.saulhdev.feeder.manager.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.libraries.gsa.d.a.OverlaysController

class OverlayService(): Service() {
    private lateinit var overlaysController: OverlaysController

    override fun onCreate() {
        super.onCreate()
        overlaysController = ConfigurationOverlayController(this)
    }
    override fun onDestroy() {
        overlaysController.onDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        // This service has to be exported — a launcher in another process is
        // the entire point — but exported meant any installed app could bind
        // it, hand over a window token of its own, and have Whisper render the
        // reader's feed inside it. Titles, sources, what has been read: an app
        // holding no permissions at all could have collected the lot.
        //
        // A launcher is a thing that answers CATEGORY_HOME. That is the whole
        // set of callers with a legitimate reason to be here, it needs no list
        // of package names to keep up to date, and a launcher nobody has heard
        // of qualifies on the same terms as Lawnchair.
        //
        // The caller is identified from the bind Uri rather than from
        // Binder.getCallingUid(): onBind is dispatched on the main thread,
        // outside the binder transaction, where getCallingUid answers with
        // *this* process's uid — so a check written that way would have
        // admitted everybody. See LauncherLink.callerIsALauncher.
        if (!LauncherLink.callerIsALauncher(this, intent)) {
            // The Uri's host here is the caller's package name, not an address.
            val caller = intent.data?.host
            Log.w(TAG, "Refusing a bind from $caller: not a launcher")
            return null
        }
        // The one moment the app can know a launcher is actually using it.
        LauncherLink.onBound()

        // Wrapped, because the check above cannot be trusted on its own: it
        // reads the caller's identity from the bind Uri, which the caller
        // writes. LauncherOnlyBinder asks again inside the transaction, where
        // the uid comes from the kernel rather than from the sender.
        val binder = overlaysController.onBind(intent) ?: return null
        return LauncherOnlyBinder(binder, applicationContext)
    }
    override fun onUnbind(intent: Intent): Boolean {
        LauncherLink.onUnbound()
        this.overlaysController.onUnbind(intent)
        return false
    }
}

private const val TAG = "OverlayService"
