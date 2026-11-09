package com.enova.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

class OsActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val host = "appassets.androidplatform.net"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .setDomain(host)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#020A04"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host != host) {
                    launchWeb(request.url.toString())
                    return true
                }
                return false
            }
        }
        web.addJavascriptInterface(Bridge(), "Enova")

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#020A04"))
        root.fitsSystemWindows = true
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.eosBack && window.eosBack()") { r ->
                    if (r != "true") finish()
                }
            }
        })

        val run = intent.getStringExtra("run")
        web.loadUrl("https://$host/assets/os/index.html" + if (run != null) "#run-$run" else "")
    }

    // Online apps open in a separate WebView and do not get the bridge
    private fun launchWeb(u: String) {
        if (u.startsWith("https://")) {
            startActivity(Intent(this, WebAppActivity::class.java).putExtra("url", u))
        }
    }

    inner class Bridge {
        @Suppress("DEPRECATION")
        @JavascriptInterface
        fun vibrate(ms: Int) {
            val v = getSystemService(Vibrator::class.java) ?: return
            val t = ms.toLong().coerceIn(1L, 2000L)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(t, VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(t)
        }

        @JavascriptInterface
        fun battery(): Int {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            return if (l < 0 || s <= 0) -1 else l * 100 / s
        }

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@OsActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun openApp(pkg: String) {
            runOnUiThread {
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
        }

        @JavascriptInterface
        fun openUrl(u: String) {
            runOnUiThread { launchWeb(u) }
        }

        @JavascriptInterface
        fun torch(on: Boolean) {
            try {
                val cm = getSystemService(CameraManager::class.java)
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (id != null) cm.setTorchMode(id, on)
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun exit() {
            runOnUiThread { finish() }
        }
    }
}
