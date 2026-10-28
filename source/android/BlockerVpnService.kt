package com.blocker

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class BlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var stopPipeReadFd: FileDescriptor? = null
    private var stopPipeWriteFd: FileDescriptor? = null
    private var workerThread: Thread? = null

    companion object {
        const val VIRTUAL_DNS_IP = "10.255.255.1"

        init {
            try {
                System.loadLibrary("blocker_engine")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        private external fun nativeStartDnsSinkhole(tunFd: Int, upstreamDns: String, stopPipeFd: Int)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnInterface != null) return START_STICKY

        try {
            val builder = Builder()
                .setSession("BlockerDnsSinkhole")
                .addAddress(VIRTUAL_DNS_IP, 32)
                .addRoute(VIRTUAL_DNS_IP, 32) // Route ONLY DNS virtual IP, zero general traffic bottleneck!
                .addDnsServer(VIRTUAL_DNS_IP)
                .setBlocking(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            val tunPfd = vpnInterface ?: return START_STICKY

            // Ensure native engine is loaded
            val binFile = File(filesDir, "blocklist.bin")
            if (!binFile.exists()) {
                try {
                    assets.open("blocklist.bin").use { input ->
                        binFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Throwable) {}
            }
            if (binFile.exists() && !NativeBlockerBridge.isEngineReady()) {
                NativeBlockerBridge.load(binFile)
            }

            // Create pipe for clean poll wake-up
            val pipe = Os.pipe()
            stopPipeReadFd = pipe[0]
            stopPipeWriteFd = pipe[1]

            val tunFd = tunPfd.fd
            val stopFd = getFd(stopPipeReadFd!!)

            workerThread = thread(name = "BlockerDnsSinkhole", isDaemon = true) {
                try {
                    nativeStartDnsSinkhole(tunFd, "1.1.1.1", stopFd)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    private fun getFd(fdObj: FileDescriptor): Int {
        return try {
            val field = FileDescriptor::class.java.getDeclaredField("descriptor")
            field.isAccessible = true
            field.getInt(fdObj)
        } catch (_: Throwable) {
            -1
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopPipeWriteFd?.let {
                val buf = ByteBuffer.allocate(1)
                buf.put(1)
                buf.flip()
                Os.write(it, buf)
            }
            workerThread?.join(300)
            vpnInterface?.close()
            stopPipeReadFd?.let { Os.close(it) }
            stopPipeWriteFd?.let { Os.close(it) }
        } catch (_: Throwable) {}
        vpnInterface = null
        stopPipeReadFd = null
        stopPipeWriteFd = null
    }
}
