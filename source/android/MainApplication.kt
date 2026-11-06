package com.blocker

import android.app.Application
import java.io.File

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val binFile = File(filesDir, "blocklist.bin")
        if (!binFile.exists()) {
            runCatching {
                assets.open("blocklist.bin").use { input ->
                    binFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        if (binFile.exists()) {
            NativeBlockerBridge.load(binFile)
        }
    }
}
