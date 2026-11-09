package com.enova.launcher

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import java.io.File

// Local preview/app: serves ~ (app's private home) at https://localhost/
// No Enova bridge, no file:// access. app=false -> preview bar + console, app=true -> full screen
class PreviewActivity : AppCompatActivity() {
    private val green = Color.parseColor("#00FF41")
    private val dim = Color.parseColor("#4F7A5B")
    private val red = Color.parseColor("#FF6B6B")
    private val bg = Color.parseColor("#020A04")
    private val key = Color.parseColor("#0A2314")

    private lateinit var web: WebView
    private var urlView: TextView? = null
    private var consoleView: TextView? = null
    private val logs = mutableListOf<String>()

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = File(filesDir, "home").canonicalFile
        val f = intent.getStringExtra("path")?.let { File(it).canonicalFile }
        if (f == null || !f.path.startsWith(home.path + File.separator) || !f.isFile) {
            finish(); return
        }
        val appMode = intent.getBooleanExtra("app", false)

        val ub = Uri.Builder().scheme("https").authority("localhost")
        f.relativeTo(home).path.split(File.separatorChar).forEach { ub.appendPath(it) }
        val url = ub.build().toString()

        val loader = WebViewAssetLoader.Builder()
            .setDomain("localhost")
            .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(this, home))
            .build()

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }

            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                logs.clear()
                consoleView?.visibility = View.GONE
                urlView?.text = u
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val where = m.sourceId().orEmpty().substringAfterLast('/')
                log("${m.messageLevel().name.lowercase()}: ${m.message()}  ($where:${m.lineNumber()})")
                return true
            }
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)
        root.fitsSystemWindows = true

        if (!appMode) {
            val top = LinearLayout(this)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            top.setPadding(dp(10), dp(4), dp(6), dp(4))
            val uv = TextView(this)
            uv.setTextColor(green)
            uv.typeface = Typeface.MONOSPACE
            uv.textSize = 12f
            uv.maxLines = 1
            uv.ellipsize = TextUtils.TruncateAt.START
            uv.text = url
            urlView = uv
            top.addView(uv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(btn("Reload") { web.reload() })
            top.addView(btn("Close") { finish() })
            root.addView(top)
        }

        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (!appMode) {
            val cv = TextView(this)
            cv.setTextColor(red)
            cv.typeface = Typeface.MONOSPACE
            cv.textSize = 11f
            cv.setBackgroundColor(Color.parseColor("#04120A"))
            cv.setPadding(dp(10), dp(4), dp(10), dp(4))
            cv.visibility = View.GONE
            consoleView = cv
            root.addView(cv)
        }

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        web.loadUrl(url)
    }

    private fun log(s: String) {
        val c = consoleView ?: return
        logs.add(s)
        if (logs.size > 6) logs.removeAt(0)
        c.text = logs.joinToString("\n")
        c.visibility = View.VISIBLE
    }

    private fun btn(label: String, on: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(green)
            setBackgroundColor(key)
            typeface = Typeface.MONOSPACE
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { on() }
        }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }
}
