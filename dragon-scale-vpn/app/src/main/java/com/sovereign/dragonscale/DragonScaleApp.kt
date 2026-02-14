package com.sovereign.dragonscale

import android.app.Application
import com.wireguard.android.backend.GoBackend

class DragonScaleApp : Application() {

    lateinit var backend: GoBackend
        private set

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
    }

    companion object {
        fun get(context: android.content.Context): DragonScaleApp {
            return context.applicationContext as DragonScaleApp
        }
    }
}
