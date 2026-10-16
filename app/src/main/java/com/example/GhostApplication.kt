package com.example

import android.app.Application
import android.util.Log
import com.example.data.remote.TelegramUserClient

class GhostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Log.d("GhostGram", "Application Created")
            // Initialize the singleton TDLib client
            TelegramUserClient.getInstance(this)
        } catch (e: Throwable) {
            Log.e("GhostGram", "Application initialization failed: ${e.message}")
        }
    }
}
