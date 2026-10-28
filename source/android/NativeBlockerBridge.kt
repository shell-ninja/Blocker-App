package com.blocker

import java.io.File

object NativeBlockerBridge {

    init {
        try {
            System.loadLibrary("blocker_engine")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @Volatile
    private var isLoaded: Boolean = false

    fun load(binFile: File): Boolean {
        if (!binFile.exists()) return false
        isLoaded = try {
            nativeInit(binFile.absolutePath)
        } catch (e: Throwable) {
            false
        }
        return isLoaded
    }

    fun isEngineReady(): Boolean = isLoaded && try { nativeIsLoaded() } catch (_: Throwable) { false }

    fun unload() {
        try {
            nativeClose()
        } catch (_: Throwable) {}
        isLoaded = false
    }

    // Zero-allocation text normalization and checking
    fun matchUrlFast(charArray: CharArray, length: Int): Boolean {
        if (!isLoaded || length <= 0) return false
        return try {
            nativeMatchUrl(charArray, length) > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun matchTextFast(charArray: CharArray, length: Int): Boolean {
        if (!isLoaded || length <= 0) return false
        return try {
            nativeMatchText(charArray, length) > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun computeFnv1a(charArray: CharArray, length: Int): Long {
        if (length <= 0) return 0L
        return try {
            nativeHashChars(charArray, length)
        } catch (_: Throwable) {
            0L
        }
    }

    // Native JNI bindings
    @JvmStatic
    private external fun nativeInit(filePath: String): Boolean

    @JvmStatic
    private external fun nativeClose()

    @JvmStatic
    private external fun nativeIsLoaded(): Boolean

    @JvmStatic
    private external fun nativeMatchUrl(chars: CharArray, len: Int): Int

    @JvmStatic
    private external fun nativeMatchText(chars: CharArray, len: Int): Int

    @JvmStatic
    private external fun nativeHashChars(chars: CharArray, len: Int): Long

    @JvmStatic
    external fun compileBinary(
        domains: Array<String>,
        keywords: Array<String>,
        outPath: String
    ): Boolean
}
