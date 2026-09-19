package com.leo.eats

import android.app.Application
import com.leo.eats.di.AppContainer

class EatsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
