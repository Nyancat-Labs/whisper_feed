package com.saulhdev.feeder.utils.extensions

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import com.google.android.material.color.DynamicColors
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.utils.isViewable
import com.saulhdev.feeder.utils.schemeOf
import com.saulhdev.feeder.utils.LEGACY_THEME_BLACK
import com.saulhdev.feeder.utils.LEGACY_THEME_SYSTEM_BLACK
import com.saulhdev.feeder.utils.THEME_DARK
import com.saulhdev.feeder.utils.THEME_FOLLOW_SYSTEM
import com.saulhdev.feeder.utils.THEME_LIGHT
import org.koin.java.KoinJavaComponent.get
import kotlin.system.exitProcess

interface ToastMaker {
    suspend fun makeToast(text: String)
    suspend fun makeToast(@StringRes resId: Int)
}

fun Context.makeToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

private fun Context.restartFeed() {
    val pm: PackageManager = packageManager
    var intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val componentName: ComponentName = intent.resolveActivity(pm)
    if (this.packageName != componentName.packageName) {
        intent = pm.getLaunchIntentForPackage(this.packageName)!!
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    startActivity(intent)
    Log.d(this.javaClass.name, "restartFeed: $intent")

    // Create a pending intent so the application is restarted after System.exit(0) was called.
    // We use an AlarmManager to call this intent in 100ms
    val mPendingIntent: PendingIntent = PendingIntent.getActivity(
        this,
        0,
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val mgr: AlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent

    // Kill the application
    exitProcess(0)
}

/**
 * Opens an address in whichever app handles it, if it is one we will open.
 *
 * The scheme allowlist is [isViewable]; see it for why this is not simply
 * ACTION_VIEW on whatever a feed put in an href. Silent when it refuses — a
 * link that quietly does nothing beats a dialog explaining a URI scheme — and
 * returns whether it went, for callers with a fallback.
 */
fun Context.launchView(url: String): Boolean {
    if (!isViewable(url)) {
        Log.w("Context", "Refusing to open: ${schemeOf(url)}")
        return false
    }
    val uri = runCatching { url.toUri() }.getOrNull() ?: return false
    safeStartActivity(Intent(Intent.ACTION_VIEW, uri))
    return true
}

/**
 * Safely start an activity from overlay context.
 * Uses PendingIntent BAL opt-in for non-Activity contexts (overlay/service).
 */
fun Context.safeStartActivity(intent: Intent) {
    try {
        val canStartDirectly = this is Activity

        if (canStartDirectly) {
            startActivity(intent)
        } else {
            launchWithBalPendingIntent(intent)
        }
    } catch (e: Exception) {
        Log.e("Context", "Failed to start activity: ${e.message}")
    }
}

/**
 * Safely start a share intent from overlay context.
 */
fun Context.safeShareIntent(url: String, title: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Where to Send?")

        val canStartDirectly = this is Activity

        if (canStartDirectly) {
            startActivity(chooserIntent)
        } else {
            launchWithBalPendingIntent(chooserIntent)
        }
    } catch (e: Exception) {
        Log.e("Context", "Failed to start share: ${e.message}")
    }
}

private fun Context.launchWithBalPendingIntent(intent: Intent) {
    val targetIntent = Intent(intent).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val creatorOptions = buildBalCreatorOptions()
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val pendingIntent = PendingIntent.getActivity(
        this,
        System.currentTimeMillis().toInt(),
        targetIntent,
        flags,
        creatorOptions
    )

    val senderOptions = buildBalSenderOptions()
    pendingIntent.send(
        this,
        0,
        null,
        null,
        null,
        null,
        senderOptions
    )
}

private fun buildBalCreatorOptions(): Bundle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return runCatching {
        val options = ActivityOptions.makeBasic()
        val clazz = ActivityOptions::class.java
        val method = clazz.getMethod(
            "setPendingIntentCreatorBackgroundActivityStartMode",
            Int::class.javaPrimitiveType
        )
        val modeField = clazz.getField("MODE_BACKGROUND_ACTIVITY_START_ALLOWED")
        method.invoke(options, modeField.getInt(null))
        options.toBundle()
    }.getOrNull()
}

private fun buildBalSenderOptions(): Bundle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return runCatching {
        val options = ActivityOptions.makeBasic()
        val clazz = ActivityOptions::class.java
        val method = clazz.getMethod(
            "setPendingIntentBackgroundActivityStartMode",
            Int::class.javaPrimitiveType
        )
        val modeField = clazz.getField("MODE_BACKGROUND_ACTIVITY_START_ALLOWED")
        method.invoke(options, modeField.getInt(null))
        options.toBundle()
    }.getOrNull()
}

fun Context.shareIntent(url: String, title: String) {
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TITLE, title)
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
    shareIntent.putExtra(Intent.EXTRA_TEXT, url)

    startActivity(Intent.createChooser(shareIntent, "Where to Send?"))
}


fun Context.setCustomTheme() {
    AppCompatDelegate.setDefaultNightMode(nightMode)
    if (!(isDynamicTheme && DynamicColors.isDynamicColorAvailable())) {
        setTheme(R.style.AppTheme)
    }
}

// These read the same preference the Compose theme does. They still accept the
// two retired values, because a stored preference can outlive one launch: the
// migration runs asynchronously, so the first read after upgrading may still
// see the old string.

private val Context.themePref
    get() = get<FeedPreferences>(FeedPreferences::class.java).overlayTheme.getValue()

val Context.isDynamicTheme
    get() = themePref in listOf(THEME_FOLLOW_SYSTEM, LEGACY_THEME_SYSTEM_BLACK)

val Context.nightMode
    get() = when (themePref) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK, LEGACY_THEME_BLACK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

val Context.isDarkTheme: Boolean
    get() = when (themePref) {
        THEME_DARK, LEGACY_THEME_BLACK -> true
        THEME_LIGHT -> false
        else -> resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

/**
 * True black, which is now the switch rather than a theme entry — so this had
 * to stop testing the mode or it would have reported false for everyone.
 */
val Context.isBlackTheme: Boolean
    get() {
        val prefs = get<FeedPreferences>(FeedPreferences::class.java)
        val legacy = prefs.overlayTheme.getValue() in
                listOf(LEGACY_THEME_BLACK, LEGACY_THEME_SYSTEM_BLACK)
        return isDarkTheme && (prefs.pureBlack.getValue() || legacy)
    }