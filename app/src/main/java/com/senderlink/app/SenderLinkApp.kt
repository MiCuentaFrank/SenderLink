package com.senderlink.app

import android.app.Application
import android.content.Context

class SenderLinkApp : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
