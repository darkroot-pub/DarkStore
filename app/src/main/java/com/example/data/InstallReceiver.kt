package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class InstallReceiver : BroadcastReceiver() {
    private val TAG = "InstallReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val apkPath = intent.getStringExtra("apk_path")
        if (apkPath.isNullOrBlank()) {
            Log.e(TAG, "InstallReceiver received null or empty APK path.")
            return
        }

        Log.d(TAG, "InstallReceiver triggered for background APK install: $apkPath")
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Log.e(TAG, "InstallReceiver error: APK file does not exist at location $apkPath")
            return
        }

        // BUG FIX: onReceive() runs on the main thread by default for a
        // manifest-registered receiver, but ApkInstaller.installApk() first
        // attempts a silent install via Runtime.exec(...) + process.waitFor(),
        // which BLOCKS the calling thread for as long as the shell command
        // takes (potentially several seconds, tried up to 3 times in
        // sequence). Calling that directly here froze the whole app's main
        // thread — a real ANR risk — every time the "Download Completed"
        // notification was tapped. goAsync() + a background thread keeps the
        // receiver alive long enough to finish without blocking the UI.
        val pendingResult = goAsync()
        Thread {
            try {
                ApkInstaller.installApk(context.applicationContext, apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "InstallReceiver: install failed", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
