package com.omix.alleq

import android.app.Application
import com.omix.alleq.telemetry.TelemetryManager
import com.omix.alleq.ui.haptics.AppHaptics

class AllEqApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TelemetryManager.init(this)
        AppHaptics.init(this)
    }
}
