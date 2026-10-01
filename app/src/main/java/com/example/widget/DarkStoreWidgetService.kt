package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R
import com.example.data.AppDao
import com.example.data.AppEntity
import com.example.data.FirebaseService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Feeds the widget's list with the store's featured apps. */
class DarkStoreWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory = Factory(applicationContext)

    companion object {
        private const val PREFS = "dark_store_widget"
        private const val MAX_ROWS = 8

        /** "Featured apps · updated 14:32" — real values from the last successful load. */
        fun subtitle(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val label = if (prefs.getBoolean("is_featured", true)) "Featured apps" else "Popular apps"
            val at = prefs.getLong("updated_at", 0L)
            return if (at > 0L) "$label · updated ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))}" else label
        }
    }

    private class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
        private var items: List<AppEntity> = emptyList()
        private val icons = HashMap<String, Bitmap?>()

        override fun onCreate() {}

        // Runs on a binder thread — blocking network/disk is allowed here.
        override fun onDataSetChanged() {
            try { loadData() } catch (t: Throwable) { android.util.Log.e("DarkStoreWidget", "load failed", t) }
        }

        private fun loadData() {
            val remote = try {
                runBlocking { withTimeoutOrNull(8_000) { FirebaseService.fetchApps() } }
            } catch (e: Exception) {
                null
            }
            // Fall back to the app's on-disk catalog cache (read-only) when offline.
            val all = if (!remote.isNullOrEmpty()) remote else try {
                AppDao(ctx).getAppsList()
            } catch (e: Exception) {
                emptyList()
            }

            val eligible = all.filter { it.isApproved && !it.isSuspended && !it.isUpcoming }
            val featured = eligible.filter { it.isFeatured }
            val usedFeatured = featured.isNotEmpty()
            items = if (usedFeatured) {
                featured.take(MAX_ROWS)
            } else {
                // Nothing marked featured yet — fall back to popular / newest so the widget isn't blank.
                eligible.sortedWith(compareByDescending<AppEntity> { it.isPopular }.thenByDescending { it.isRecent })
                    .take(MAX_ROWS)
            }

            icons.clear()
            items.forEach { icons[it.id] = loadIcon(it.logo) }

            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("is_featured", usedFeatured)
                .putLong("updated_at", System.currentTimeMillis())
                .apply()
            refreshSubtitle()
        }

        private fun refreshSubtitle() {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, DarkStoreWidget::class.java))
                val partial = RemoteViews(ctx.packageName, R.layout.widget_layout)
                partial.setTextViewText(R.id.widget_subtitle, subtitle(ctx))
                ids.forEach { mgr.partiallyUpdateAppWidget(it, partial) }
            } catch (_: Exception) {}
        }

        private fun loadIcon(url: String): Bitmap? {
            if (url.isBlank()) return null
            return try {
                val request = ImageRequest.Builder(ctx)
                    .data(url)
                    .size(120, 120)
                    .allowHardware(false) // RemoteViews can't carry hardware bitmaps
                    .build()
                val result = runBlocking { Coil.imageLoader(ctx).execute(request) }
                (result as? SuccessResult)?.drawable?.toBitmap(120, 120)?.let { roundCorners(it, 26f) }
            } catch (e: Exception) {
                null
            }
        }

        private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(src, 0f, 0f, paint)
            return out
        }

        private fun installedVersionCode(packageName: String): Int? {
            if (packageName.isBlank()) return null
            return try {
                val info: PackageInfo = ctx.packageManager.getPackageInfo(packageName, 0)
                versionCodeOf(info)
            } catch (e: Exception) {
                null
            }
        }

        @Suppress("DEPRECATION")
        private fun versionCodeOf(info: PackageInfo): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews =
            try { buildRow(position) } catch (t: Throwable) {
                android.util.Log.e("DarkStoreWidget", "row failed", t)
                RemoteViews(ctx.packageName, R.layout.widget_item)
            }

        private fun buildRow(position: Int): RemoteViews {
            val app = items.getOrNull(position)
                ?: return RemoteViews(ctx.packageName, R.layout.widget_item)
            val row = RemoteViews(ctx.packageName, R.layout.widget_item)

            row.setTextViewText(R.id.item_name, app.name)

            val meta = buildList {
                if (app.category.isNotBlank()) add(app.category)
                val r = app.rating.trim().toFloatOrNull()
                if (r != null && r > 0f) add("★ " + String.format(Locale.US, "%.1f", r))
                if (app.size.isNotBlank()) add(app.size)
            }.joinToString(" · ")
            row.setTextViewText(R.id.item_meta, meta.ifBlank { app.developer })

            val icon = icons[app.id]
            if (icon != null) row.setImageViewBitmap(R.id.item_icon, icon)
            else row.setImageViewResource(R.id.item_icon, R.drawable.img_app_icon)

            // Real install state on this device
            val installed = installedVersionCode(app.packageName)
            when {
                installed == null -> {
                    row.setTextViewText(R.id.item_action, if (app.isPremium) "PREMIUM" else "GET")
                    row.setTextColor(R.id.item_action, 0xFF0B0D12.toInt())
                    row.setInt(R.id.item_action, "setBackgroundResource", R.drawable.widget_btn_get)
                }
                installed < app.versionCode -> {
                    row.setTextViewText(R.id.item_action, "UPDATE")
                    row.setTextColor(R.id.item_action, 0xFF0B0D12.toInt())
                    row.setInt(R.id.item_action, "setBackgroundResource", R.drawable.widget_btn_update)
                }
                else -> {
                    row.setTextViewText(R.id.item_action, "OPEN")
                    row.setTextColor(R.id.item_action, 0xFF34D399.toInt())
                    row.setInt(R.id.item_action, "setBackgroundResource", R.drawable.widget_btn_open)
                }
            }

            // Tap → App Details inside Dark Store (merged into the template intent)
            row.setOnClickFillInIntent(
                R.id.widget_item_root,
                Intent().apply {
                    putExtra("open_screen", "app_details")
                    putExtra("app_id", app.id)
                }
            )
            return row
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds(): Boolean = true
        override fun onDestroy() { items = emptyList(); icons.clear() }
    }
}
