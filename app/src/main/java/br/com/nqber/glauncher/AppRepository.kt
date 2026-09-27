package br.com.nqber.glauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

object AppRepository {

    private var cachedApps: List<AppInfo>? = null
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    @Synchronized
    fun getApps(context: Context, forceReload: Boolean = false): List<AppInfo> {
        if (cachedApps == null || forceReload) {
            cachedApps = queryInstalledApps(context)
        }
        return cachedApps ?: emptyList()
    }

    fun getIcon(context: Context, packageName: String): Drawable {
        return iconCache.getOrPut(packageName) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                context.packageManager.defaultActivityIcon
            }
        }
    }

    @Synchronized
    fun invalidate() {
        cachedApps = null
        iconCache.clear()
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
                    packageName = it.activityInfo.packageName
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
