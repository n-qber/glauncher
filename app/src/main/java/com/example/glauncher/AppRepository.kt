package com.example.glauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object AppRepository {

    private var cachedApps: List<AppInfo>? = null

    @Synchronized
    fun getApps(context: Context, forceReload: Boolean = false): List<AppInfo> {
        if (cachedApps == null || forceReload) {
            cachedApps = queryInstalledApps(context)
        }
        return cachedApps ?: emptyList()
    }

    @Synchronized
    fun invalidate() {
        cachedApps = null
    }

    private fun queryInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveInfos
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString().trim(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
