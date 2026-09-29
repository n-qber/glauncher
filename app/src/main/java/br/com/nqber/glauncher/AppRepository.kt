package br.com.nqber.glauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AppRepository {

    private const val CACHE_FILE_NAME = "apps_cache.json"

    private var cachedApps: List<AppInfo>? = null
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    fun getCachedApps(): List<AppInfo>? = cachedApps

    @Synchronized
    fun getApps(context: Context, forceReload: Boolean = false): List<AppInfo> {
        if (!forceReload && cachedApps != null) {
            return cachedApps ?: emptyList()
        }

        if (!forceReload) {
            val diskApps = readDiskCache(context)
            if (!diskApps.isNullOrEmpty()) {
                cachedApps = diskApps
                return diskApps
            }
        }

        val freshApps = queryInstalledApps(context)
        cachedApps = freshApps
        writeDiskCache(context, freshApps)
        return freshApps
    }

    fun syncWithSystem(context: Context): List<AppInfo>? {
        val freshApps = queryInstalledApps(context)
        val current = cachedApps
        if (current == null || freshApps != current) {
            cachedApps = freshApps
            writeDiskCache(context, freshApps)
            return freshApps
        }
        return null
    }

    fun getCachedIcon(packageName: String): Drawable? {
        return iconCache[packageName]
    }

    fun getDefaultIcon(context: Context): Drawable {
        return context.packageManager.defaultActivityIcon
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

    fun preloadIcons(context: Context, packageNames: List<String>) {
        for (pkg in packageNames) {
            if (!iconCache.containsKey(pkg)) {
                try {
                    val icon = context.packageManager.getApplicationIcon(pkg)
                    iconCache[pkg] = icon
                } catch (_: Exception) {
                    iconCache[pkg] = context.packageManager.defaultActivityIcon
                }
            }
        }
    }

    @Synchronized
    fun invalidate(context: Context? = null) {
        cachedApps = null
        iconCache.clear()
        if (context != null) {
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    private fun readDiskCache(context: Context): List<AppInfo>? {
        return try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) return null
            val content = file.readText()
            val array = JSONArray(content)
            val list = ArrayList<AppInfo>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val label = obj.getString("l")
                val pkg = obj.getString("p")
                val clean = obj.optString("c", "")
                list.add(
                    if (clean.isNotEmpty()) AppInfo(label, pkg, clean)
                    else AppInfo(label, pkg)
                )
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDiskCache(context: Context, apps: List<AppInfo>) {
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val array = JSONArray()
            for (app in apps) {
                val obj = JSONObject().apply {
                    put("l", app.label)
                    put("p", app.packageName)
                    put("c", app.cleanLabel)
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {
            // Ignore disk cache write failure
        }
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

