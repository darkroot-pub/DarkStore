package com.example.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEMPORARY crash logger — install early in MainActivity.onCreate.
 * Writes uncaught exceptions to filesDir/crash_logs/ so they can be
 * copied from Settings → Crash logs.
 *
 * REMOVE LATER: delete this file, the install() call, and the Settings UI block.
 */
object CrashLogCatcher {

    private const val TAG = "CrashLogCatcher"
    private const val DIR_NAME = "crash_logs"
    private const val MAX_FILES = 20

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            // Always pass through so the system still shows the usual crash dialog
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    // No previous handler — kill process after logging
                    try {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(10)
                    } catch (_: Exception) { }
                }
        }
        Log.i(TAG, "Crash log catcher installed")
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$stamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString()

        val body = buildString {
            appendLine("=== Dark Store crash ===")
            appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())}")
            appendLine("thread=${thread.name} id=${thread.id}")
            appendLine("sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("package=${context.packageName}")
            try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                @Suppress("DEPRECATION")
                appendLine("versionName=${pi.versionName} versionCode=${pi.versionCode}")
            } catch (_: Exception) { }
            appendLine()
            appendLine(stack)
        }

        file.writeText(body)
        Log.e(TAG, "Crash written to ${file.absolutePath}\n$body")

        // Keep only the newest MAX_FILES
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun listCrashes(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun readLatest(context: Context): String? =
        listCrashes(context).firstOrNull()?.readText()

    fun readAll(context: Context): String {
        val files = listCrashes(context)
        if (files.isEmpty()) return "No crash logs yet."
        return files.joinToString("\n\n────────────\n\n") { f ->
            "FILE: ${f.name}\n" + f.readText()
        }
    }

    fun clear(context: Context) {
        listCrashes(context).forEach { it.delete() }
    }

    fun count(context: Context): Int = listCrashes(context).size
}
