package com.example.audiobook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AudiobookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tom_rouwe.pdfbox.Config.allowAssetAccess = true // Optional, for assets
        com.tom_rouwe.pdfbox.util.PDFBoxResourceLoader.init(applicationContext)
    }
}

