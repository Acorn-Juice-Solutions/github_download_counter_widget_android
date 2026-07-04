package com.example.downloadwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class RefreshWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RefreshWorker"

        fun enqueueRefresh(context: Context, providerClass: Class<*>) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf("provider_class" to providerClass.name))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "refresh_widget_${providerClass.simpleName}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val providerClassName = inputData.getString("provider_class") ?: return Result.failure()
        val providerClass = try {
            Class.forName(providerClassName)
        } catch (e: Exception) {
            return Result.failure()
        }
        
        Log.d(TAG, "Starting background refresh for $providerClassName")
        
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, providerClass)
        val widgetIds = manager.getAppWidgetIds(component)

        if (widgetIds.isEmpty()) return Result.success()

        val provider = try {
            providerClass.getDeclaredConstructor().newInstance() as? BaseDownloadWidgetProvider
        } catch (e: Exception) {
            null
        } ?: return Result.failure()

        for (widgetId in widgetIds) {
            try {
                provider.updateWidgetSyncInternal(context, manager, widgetId)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $widgetId", e)
                if (e.message?.contains("403") == true) {
                    return Result.failure()
                }
                return Result.retry()
            }
        }

        return Result.success()
    }
}
