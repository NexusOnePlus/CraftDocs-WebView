package com.and.craft

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OfflineEnabledWebViewClient(private val context: Context) : WebViewClientCompat() {
    private var forceReload = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var webView: WebView? = null

    fun setForceReload(force: Boolean){
        forceReload = force
    }

    fun setupNetworkCallback(webView: WebView) {
        this.webView = webView
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                webView.post {
                    webView.clearCache(true)
                    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    webView.reload()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                webView.post {
                    webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }
    fun cleanup() {
        networkCallback?.let {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
        webView = null
    }
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        return try {
            if (isNetworkAvailable()) {
                return loadFromNetwork(request)
            }

           return loadFromCache(request.url.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            loadFromCache(request.url.toString())
        }
    }

    private fun loadFromNetwork(request: WebResourceRequest): WebResourceResponse? {
        val response = makeHttpRequest(request)

        response?.let {
            if (it.statusCode == 200) {
                val responseData = it.data.readBytes()
                saveToCache(request.url.toString(), responseData, it.mimeType)

                return WebResourceResponse(
                    it.mimeType,
                    it.encoding,
                    ByteArrayInputStream(responseData)
                ).apply {
                    setStatusCodeAndReasonPhrase(it.statusCode, it.reasonPhrase)
                    it.responseHeaders?.let { setResponseHeaders(it) }
                }
            }
        }
        return null
    }

    private fun loadFromCache(url: String): WebResourceResponse? {
        return try {
            val cacheDir = context.cacheDir
            val urlHash = url.hashCode().toString()
            val cacheFile = File(cacheDir, urlHash)
            val mimeTypeFile = File(cacheDir, "${urlHash}_mime")

            if (cacheFile.exists() && mimeTypeFile.exists()) {
                val mimeType = mimeTypeFile.readText()
                val data = cacheFile.readBytes()
                WebResourceResponse(mimeType, "UTF-8", ByteArrayInputStream(data))
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveToCache(url: String, data: ByteArray, mimeType: String) {
        try {
            val cacheDir = context.cacheDir
            val urlHash = url.hashCode().toString()
            val cacheFile = File(cacheDir, urlHash)
            val mimeTypeFile = File(cacheDir, "${urlHash}_mime")

            FileOutputStream(cacheFile).use { it.write(data) }
            mimeTypeFile.writeText(mimeType)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeHttpRequest(request: WebResourceRequest): WebResourceResponse? {
        return try {
            val connection = URL(request.url.toString()).openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")

            request.requestHeaders.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            connection.connect()

            val responseData = connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
            }

            val mimeType = connection.contentType ?: getMimeType(request.url.toString())

            WebResourceResponse(
                mimeType,
                connection.contentEncoding ?: "UTF-8",
                ByteArrayInputStream(responseData)
            ).apply {
                setStatusCodeAndReasonPhrase(connection.responseCode, connection.responseMessage ?: "")
                connection.headerFields.mapValues { it.value.joinToString(",") }.let { setResponseHeaders(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMimeType(url: String): String {
        return when {
            url.endsWith(".html", true) -> "text/html"
            url.endsWith(".js", true) -> "application/javascript"
            url.endsWith(".css", true) -> "text/css"
            url.endsWith(".jpg", true) -> "image/jpeg"
            url.endsWith(".jpeg", true) -> "image/jpeg"
            url.endsWith(".png", true) -> "image/png"
            url.endsWith(".gif", true) -> "image/gif"
            url.endsWith(".svg", true) -> "image/svg+xml"
            url.endsWith(".heic", true) -> "image/heic"
            url.endsWith(".webp", true) -> "image/webp"
            url.endsWith(".json", true) -> "application/json"
            url.endsWith(".xml", true) -> "application/xml"
            url.endsWith(".pdf", true) -> "application/pdf"
            url.endsWith(".woff", true) -> "font/woff"
            url.endsWith(".woff2", true) -> "font/woff2"
            url.endsWith(".ttf", true) -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
