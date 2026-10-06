package com.google.android.libraries.launcherclient

import android.os.Bundle
import android.os.IInterface
import android.os.RemoteException
import android.view.WindowManager

interface ILauncherOverlay : IInterface {
    @Throws(RemoteException::class)
    fun closeOverlay(options: Int)

    @Throws(RemoteException::class)
    fun openOverlay(options: Int)

    fun windowAttached(bundle: Bundle?, callback: ILauncherOverlayCallback?)

    @Throws(RemoteException::class)
    fun windowAttached( attrs: WindowManager.LayoutParams, callbacks: ILauncherOverlayCallback,options: Int)

    @Throws(RemoteException::class)
    fun onScroll(progress: Float)

    @Throws(RemoteException::class)
    fun startScroll()

    @Throws(RemoteException::class)
    fun endScroll()

    @Throws(RemoteException::class)
    fun requestVoiceDetection(start: Boolean)

    @Throws(RemoteException::class)
    fun windowDetached(isChangingConfigurations: Boolean)

    fun onPause()

    fun onResume()
}