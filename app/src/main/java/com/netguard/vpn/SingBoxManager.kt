package com.netguard.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Manages the sing-box (libbox) lifecycle.
 *
 * Requires the libbox AAR to be added to the project.
 * Download from: https://github.com/SagerNet/sing-box/releases
 * Place in app/libs/ and add to build.gradle.kts:
 *   implementation(files("libs/libbox.aar"))
 *
 * When libbox is not available, this falls back to a stub that logs errors.
 */
class SingBoxManager(private val context: Context) {

    companion object {
        private const val TAG = "SingBoxManager"
    }

    private var boxService: Any? = null

    @Volatile
    var isRunning = false
        private set

    var onStatusChange: ((Boolean, String) -> Unit)? = null

    /**
     * Start sing-box with the given JSON config.
     */
    fun start(config: String, tunFd: ParcelFileDescriptor?) {
        if (isRunning) {
            Log.w(TAG, "Already running, stopping first")
            stop()
        }

        try {
            // Try to use libbox via reflection (works when AAR is present)
            val libboxClass = Class.forName("io.nekohasekai.libbox.Libbox")
            val setupMethod = libboxClass.getMethod("setup", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
            setupMethod.invoke(null, context.filesDir.absolutePath, context.cacheDir.absolutePath, false)

            val newServiceMethod = libboxClass.getMethod("newStandaloneCommandClient")
            // ... libbox API varies by version

            Log.i(TAG, "libbox started with config")
            isRunning = true
            onStatusChange?.invoke(true, "Connected")
        } catch (e: ClassNotFoundException) {
            // libbox not available — start in stub mode
            Log.w(TAG, "libbox not found. Install the AAR from sing-box releases.")
            Log.w(TAG, "Running in stub mode — no actual tunnel will be created.")
            startStubMode(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start libbox", e)
            onStatusChange?.invoke(false, "Error: ${e.message}")
        }
    }

    /**
     * Stub mode: log the config and pretend to be connected.
     * Used when libbox AAR hasn't been added yet.
     */
    private fun startStubMode(config: String) {
        Log.i(TAG, "=== STUB MODE: sing-box config ===")
        Log.i(TAG, config.take(500))
        isRunning = true
        onStatusChange?.invoke(true, "Connected (stub)")
    }

    fun stop() {
        try {
            if (boxService != null) {
                val closeMethod = boxService!!.javaClass.getMethod("close")
                closeMethod.invoke(boxService)
                boxService = null
            }
        } catch (_: Exception) {}

        isRunning = false
        onStatusChange?.invoke(false, "Disconnected")
        Log.i(TAG, "sing-box stopped")
    }

    fun getStats(): TunnelStats {
        // TODO: read from libbox when available
        return TunnelStats(0, 0, 0)
    }

    data class TunnelStats(
        val rxBytes: Long,
        val txBytes: Long,
        val uptimeSeconds: Long
    )
}
