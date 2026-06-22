package com.escosis.shizubridge

import android.app.Application
import com.escosis.shizubridge.utils.ShizukuManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init()
    }
}