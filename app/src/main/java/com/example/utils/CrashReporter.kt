package com.example.utils

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the stack trace of any uncaught crash to a file, so the NEXT launch can
 * show it (with a Copy button). Lets you report the exact error without adb/logcat.
 * The original handler still runs afterwards, so behaviour is otherwise unchanged.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(app.filesDir, FILE).writeText("Time: $stamp\nThread: ${thread.name}\n\n$sw".take(12000))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? = try {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try { File(context.filesDir, FILE).delete() } catch (_: Throwable) {}
    }
}
