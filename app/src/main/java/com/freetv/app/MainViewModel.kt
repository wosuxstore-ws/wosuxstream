package com.freetv.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val channels = MutableLiveData<List<Channel>>(emptyList())
    val loading = MutableLiveData(false)
    val message = MutableLiveData<String?>()
    val source = MutableLiveData(Prefs.source)

    var query: String = ""
    var favoritesOnly: Boolean = false

    private var job: Job? = null

    init {
        load(force = false)
    }

    fun setSource(s: Source) {
        Prefs.source = s
        source.value = s
        channels.value = emptyList()
        favoritesOnly = false
        load(force = false)
    }

    /**
     * Shows the saved copy instantly (works offline), then refreshes from the internet
     * when forced or when the saved copy is older than a few hours.
     */
    fun load(force: Boolean) {
        job?.cancel()
        val s = source.value ?: Sources.ALL
        job = viewModelScope.launch {
            loading.value = true
            try {
                val cache = cacheFile(s)
                var shown = false

                if (cache.exists()) {
                    val cached = withContext(Dispatchers.IO) { parseFile(cache) }
                    if (cached.isNotEmpty()) {
                        channels.value = cached
                        shown = true
                    }
                }

                val stale = !cache.exists() || System.currentTimeMillis() - cache.lastModified() > STALE_MS
                if (force || stale) {
                    try {
                        val fresh = withContext(Dispatchers.IO) {
                            download(s.url, cache)
                            parseFile(cache)
                        }
                        channels.value = fresh
                        if (fresh.isEmpty()) {
                            message.value = getApplication<Application>().getString(R.string.err_empty)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val app = getApplication<Application>()
                        message.value = if (shown) {
                            app.getString(R.string.err_refresh)
                        } else {
                            app.getString(R.string.err_load, e.message ?: e.javaClass.simpleName)
                        }
                    }
                }
            } finally {
                if (isActive) loading.value = false
            }
        }
    }

    private fun cacheFile(s: Source): File =
        File(getApplication<Application>().filesDir, "pl_" + Integer.toHexString(s.url.hashCode()) + ".m3u")

    private fun parseFile(f: File): List<Channel> =
        try {
            f.bufferedReader().use { M3uParser.parse(it) }
        } catch (e: Exception) {
            emptyList()
        }

    private fun download(urlStr: String, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        var current = urlStr
        var redirects = 0
        while (true) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "FreeTV/1.0 (Android)")
            try {
                val code = conn.responseCode
                if (code in 300..399 && redirects < 5) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Bad redirect")
                    current = URL(URL(current), loc).toString()
                    redirects++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
                conn.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            } finally {
                conn.disconnect()
            }
            break
        }
        if (tmp.length() == 0L) {
            tmp.delete()
            throw IOException("Empty playlist")
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private companion object {
        const val STALE_MS = 6L * 60 * 60 * 1000
    }
}
