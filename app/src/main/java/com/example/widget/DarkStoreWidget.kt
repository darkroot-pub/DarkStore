package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/**
 * Home-screen widget: a Play-Store-style list of the store's FEATURED apps
 * (icon, name, category · rating · size, and a real GET / UPDATE / OPEN state
 * based on what is installed on this device). Data comes from the live catalog
 * (see [DarkStoreWidgetService]); nothing here is hard-coded status text.
 */
class DarkStoreWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            notifyDataChanged(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"

        /** Re-binds every widget and tells its list to reload (called from the app after a catalog sync). */
        fun updateAllWidgets(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, DarkStoreWidget::class.java))
                for (id in ids) updateAppWidget(context, mgr, id)
                if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /** Cheap variant: just reload the list contents (no re-bind). */
        fun notifyDataChanged(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, DarkStoreWidget::class.java))
                if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Collection adapter (unique data URI per widget id so each gets its own factory)
            val serviceIntent = Intent(context, DarkStoreWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Last real sync info (written by the list factory after each load)
            views.setTextViewText(R.id.widget_subtitle, DarkStoreWidgetService.subtitle(context))

            // Row taps: the factory fills in open_screen/app_id for each row
            val templateIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val templatePending = PendingIntent.getActivity(
                context, 200 + appWidgetId, templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            views.setPendingIntentTemplate(R.id.widget_list, templatePending)

            // Header opens the store
            val launchPending = PendingIntent.getActivity(
                context, 111,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_logo, launchPending)
            views.setOnClickPendingIntent(R.id.widget_title, launchPending)
            views.setOnClickPendingIntent(R.id.widget_subtitle, launchPending)

            // Refresh button
            val refreshPending = PendingIntent.getBroadcast(
                context, 112,
                Intent(context, DarkStoreWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
