package com.leo.wardrobe

import android.app.Application
import com.leo.wardrobe.di.AppContainer

class WardrobeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
