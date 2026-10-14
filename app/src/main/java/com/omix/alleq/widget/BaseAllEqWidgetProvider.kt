package com.omix.alleq.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

abstract class BaseAllEqWidgetProvider : AppWidgetProvider() {

    abstract fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        AllEqWidgetManager.updateWidgets(context)
    }
}

class AllEqWidgetProvider2x1 : BaseAllEqWidgetProvider() {
    override fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = AllEqWidgetManager.build2x1Views(context)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class AllEqWidgetProvider2x2 : BaseAllEqWidgetProvider() {
    override fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = AllEqWidgetManager.build2x2Views(context)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class AllEqWidgetProvider4x1 : BaseAllEqWidgetProvider() {
    override fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = AllEqWidgetManager.build4x1Views(context)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
