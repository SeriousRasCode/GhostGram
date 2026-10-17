package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.remote.TelegramUserClient

class GhostApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

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
