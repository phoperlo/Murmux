package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object MirrorFinder {
    private const val TAG = "MirrorFinder"

    // List of repository mirrors for Ubuntu 20.04 Rootfs and standard fallbacks
    val UBUNTU_MIRRORS = listOf(
        "https://old-releases.ubuntu.com/releases/20.04.4/ubuntu-20.04-live-server-arm64.iso",
        "https://cdimage.ubuntu.com/ubuntu-base/releases/20.04/release/ubuntu-base-20.04.5-base-arm64.tar.gz",
        "https://raw.githubusercontent.com/murmux-project/rootfs/main/ubuntu-20.04-arm64.tar.gz",
        "https://github.com/murmux-project/rootfs/releases/download/v1.0/ubuntu-20.04-rootfs-arm64.tar.gz",
        "https://partner-images.canonical.com/core/focal/current/ubuntu-focal-core-cloudimg-arm64-root.tar.gz",
        "https://cdimage.ubuntu.com/ubuntu-base/releases/20.04.5/release/ubuntu-base-20.04.5-base-arm64.tar.gz"
    )

    /**
     * Poll mirror URLs using HTTP HEAD to find a working mirror and its content size.
     * Returns a Pair of (Working URL, Content-Length) or null if none accessible.
     */
    suspend fun findWorkingMirror(
        mirrors: List<String> = UBUNTU_MIRRORS,
        onProgressLog: (String) -> Unit = {}
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        for (mirror in mirrors) {
            try {
                onProgressLog("Опрос зеркала: $mirror ...")
                Log.d(TAG, "Checking mirror: $mirror")
                val url = URL(mirror)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code for $mirror: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = connection.contentLengthLong
                    Log.i(TAG, "Success! Found mirror: $mirror with size $contentLength bytes")
                    connection.disconnect()
                    onProgressLog("Успешно: Найдено активное зеркало. Размер файла: ${contentLength} б.")
                    return@withContext Pair(mirror, contentLength)
                } else {
                    onProgressLog("Недоступно (HTTP $responseCode) для $mirror")
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking mirror $mirror: ${e.message}")
                onProgressLog("Сбой подключения к $mirror: ${e.localizedMessage}")
            }
        }
        
        onProgressLog("Предупреждение: Все онлайн-зеркала недоступны.")
        return@withContext null
    }
}
