package com.and.craft

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Objects


class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var webViewClient: OfflineEnabledWebViewClient
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val sharedPreferences by lazy { getSharedPreferences("webview_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Objects.requireNonNull(supportActionBar)?.hide()
        setupWebView()
        setupSwipeRefresh()
    }

    private fun setupSwipeRefresh () {
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setOnRefreshListener {
            if (webViewClient.isNetworkAvailable()) {
                webView.clearCache(true)
                webViewClient.setForceReload(true)
                webView.reload()
            }

            swipeRefreshLayout.postDelayed({
                webViewClient.setForceReload(false)
                swipeRefreshLayout.isRefreshing = false
            }, 1000)        }
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webViewClient = OfflineEnabledWebViewClient(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            cacheMode = if (webViewClient.isNetworkAvailable()) {
                WebSettings.LOAD_NO_CACHE
            } else {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
            loadsImagesAutomatically = true
        }

        webView.webViewClient = webViewClient
        webViewClient.setupNetworkCallback(webView)

        val lastVisitedUrl = sharedPreferences.getString("last_visited_url", "https://docs.craft.do/")
        webView.loadUrl(lastVisitedUrl ?: "https://docs.craft.do/")
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewClient.cleanup()
    }
    override fun onBackPressed() {
        if (webView.url == "https://docs.craft.do/recents") {
            super.onBackPressed()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            webView.loadUrl("https://docs.craft.do/recents")
        }
    }


    override fun onPause() {
        super.onPause()
        val currentUrl = webView.url ?: ""
        sharedPreferences.edit().putString("last_visited_url", currentUrl).apply()
    }
}
