package com.freetv.app

import android.app.Application

class FreeTVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Prefs.init(this)
    }
}
