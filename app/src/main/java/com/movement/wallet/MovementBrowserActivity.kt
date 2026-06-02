package com.movement.wallet

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class MovementBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var walletEngine: WalletEngine
    private var injectScript: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        walletEngine = WalletEngine(this)

        injectScript = assets.open("movement-wallet-inject.js")
            .bufferedReader()
            .use { it.readText() }

        setupWebView()
        webView.loadUrl("https://app.mosaic.ag")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString.replace("; wv", "") +
                    " MovementWallet/1.0"
        }

        WebView.setWebContentsDebuggingEnabled(true)

        webView.addJavascriptInterface(
            MovementJsBridge(walletEngine, webView, this),
            "MovementBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                view.evaluateJavascript(injectScript, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(injectScript, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                android.util.Log.d(
                    "DAppConsole",
                    "${consoleMessage.message()}"
                )
                return true
            }
        }
    }

    fun sendCallbackToJs(id: Int, result: org.json.JSONObject) {
        val payload = org.json.JSONObject().apply {
            put("id", id)
            put("result", result)
        }
        val js = "window.movementCallback(${
            org.json.JSONObject.quote(payload.toString())
        });"
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    fun sendErrorToJs(id: Int, error: String) {
        val payload = org.json.JSONObject().apply {
            put("id", id)
            put("error", error)
        }
        val js = "window.movementCallback(${
            org.json.JSONObject.quote(payload.toString())
        });"
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}

class MovementJsBridge(
    private val walletEngine: WalletEngine,
    private val webView: WebView,
    private val activity: MovementBrowserActivity
) {
    @JavascriptInterface
    fun postMessage(payloadJson: String) {
        try {
            val payload = org.json.JSONObject(payloadJson)
            val id = payload.getInt("id")
            val method = payload.getString("method")
            val params = payload.optJSONObject("params")
                ?: org.json.JSONObject()

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                walletEngine.handle(id, method, params) { result, error ->
                    if (error != null) {
                        activity.sendErrorToJs(id, error)
                    } else {
                        activity.sendCallbackToJs(id, result
                            ?: org.json.JSONObject())
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MovementBridge", "error", e)
        }
    }
}
