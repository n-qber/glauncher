package com.example.glauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val GITHUB_REPO = "n-qber/glauncher"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    fun checkForUpdates(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            try {
                val releaseInfo = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return@launch
                val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                val latestVersion = releaseInfo.tagName.removePrefix("v").trim()

                if (isNewer(latestVersion, currentVersion)) {
                    promptUpdate(activity, releaseInfo)
                }
            } catch (_: Exception) {
                // Ignore network errors or if rate-limited; do not interrupt the launcher
            }
        }
    }

    private fun promptUpdate(activity: AppCompatActivity, release: ReleaseInfo) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage("Version ${release.tagName} is available. Would you like to update?")
            .setPositiveButton("Update") { _, _ ->
                startDownload(activity, release.apkUrl, release.tagName)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startDownload(activity: AppCompatActivity, apkUrl: String, version: String) {
        val progressDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading update...")
            .setMessage("Please wait while version $version is downloading.")
            .setCancelable(false)
            .create()

        progressDialog.show()

        activity.lifecycleScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadFile(activity, apkUrl)
                }
                progressDialog.dismiss()
                installApk(activity, apkFile)
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(activity, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFile(context: Context, downloadUrl: String): File {
        var currentUrl = downloadUrl
        var connection: HttpURLConnection
        var redirect = true

        // Follow HTTP redirects (GitHub redirects to AWS S3 / CDN)
        while (true) {
            connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
            }
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308
            ) {
                currentUrl = connection.getHeaderField("Location")
                connection.disconnect()
            } else {
                break
            }
        }

        val destinationFile = File(context.cacheDir, "update.apk")
        connection.inputStream.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
        return destinationFile
    }

    private fun installApk(activity: AppCompatActivity, apkFile: File) {
        // Request install permission on Android 8.0+ if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "Please allow installing updates for GLauncher", Toast.LENGTH_LONG).show()
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(permissionIntent)
            return
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(installIntent)
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            connectTimeout = 10000
            readTimeout = 10000
        }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            return null
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val json = JSONObject(response)
        val tagName = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: return null

        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }

        return if (tagName.isNotEmpty() && apkUrl != null) {
            ReleaseInfo(tagName, apkUrl)
        } else {
            null
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val lParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val cParts = current.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private data class ReleaseInfo(val tagName: String, val apkUrl: String)
}
