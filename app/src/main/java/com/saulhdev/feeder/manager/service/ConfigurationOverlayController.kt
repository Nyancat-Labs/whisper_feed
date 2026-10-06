package com.saulhdev.feeder.manager.service

import android.app.Service
import android.content.res.Configuration
import com.saulhdev.feeder.launcherpanel.OverlayController
import com.saulhdev.feeder.launcherpanel.OverlaysController

class ConfigurationOverlayController(private val service: Service) : OverlaysController(service) {

    override fun createController(
        configuration: Configuration?,
        serverVersion: Int,
        clientVersion: Int
    ): OverlayController {
        val context = if (configuration != null) service.createConfigurationContext(configuration) else service
        return OverlayView(context)
    }
}
