package com.omix.alleq

import android.app.Application
import com.omix.alleq.telemetry.TelemetryManager

class AllEqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TelemetryManager.init(this)
    }
}
