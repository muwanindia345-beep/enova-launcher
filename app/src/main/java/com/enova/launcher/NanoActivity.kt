package com.enova.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import java.io.File

class NanoActivity : AppCompatActivity() {
    private val green = Color.parseColor("#00FF41")
    private val fg = Color.parseColor("#B9F5C8")
    private val dim = Color.parseColor("#4F7A5B")
    private val bg = Color.parseColor("#020A04")
    private val surf = Color.parseColor("#04120A")
    private val key = Color.parseColor("#0A2314")

    private lateinit var home: File
    private lateinit var file: File
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var ed: Code
    private var dirty = false
    private var isNew = false

    private inner class Code(c: Context) : AppCompatEditText(c) {
        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            if (::status.isInitialized) showPos(selStart)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = File(filesDir, "home").canonicalFile
        val p = intent.getStringExtra("path")
        val f = p?.let { File(it).canonicalFile }
        if (f == null || !f.path.startsWith(home.path + File.separator) || f.isDirectory) {
            finish(); return
        }
        file = f

        var text = ""
        isNew = !file.exists()
        if (!isNew) {
            try {
                if (file.length() > 262144) { toast("File too large (max 256 KB)"); finish(); return }
                val b = file.readBytes()
                if (b.take(512).any { it.toInt() == 0 }) { toast("Binary file"); finish(); return }
                text = String(b, Charsets.UTF_8)
            } catch (e: Exception) {
                toast("Cannot read: ${e.message}"); finish(); return
            }
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)
        root.fitsSystemWindows = true

        // top bar
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(10), dp(4), dp(6), dp(4))
        title = TextView(this)
        title.setTextColor(green)
        title.typeface = Typeface.MONOSPACE
        title.textSize = 13f
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.START
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(btn("Save", true) { save() })
        top.addView(btn("Exit", true) { tryExit() })
        root.addView(top)

        // editor
        ed = Code(this)
        ed.typeface = Typeface.MONOSPACE
        ed.textSize = 13f
        ed.setTextColor(fg)
        ed.setBackgroundColor(surf)
        ed.gravity = Gravity.TOP or Gravity.START
        ed.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        ed.setHorizontallyScrolling(true)
        ed.setPadding(dp(10), dp(8), dp(10), dp(8))
        ed.isVerticalScrollBarEnabled = true
        root.addView(ed, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // status
        status = TextView(this)
        status.setTextColor(dim)
        status.typeface = Typeface.MONOSPACE
        status.textSize = 11f
        status.setPadding(dp(10), dp(3), dp(10), dp(3))
        status.text = "Ln 1, Col 1"
        root.addView(status)

        // quick keys
        val keys = listOf(
            Triple("Tab", "  ", 0), Triple("<", "<", 0), Triple(">", ">", 0), Triple("/", "/", 0),
            Triple("{}", "{}", 1), Triple("()", "()", 1), Triple("\"\"", "\"\"", 1),
            Triple("{", "{", 0), Triple("}", "}", 0), Triple("(", "(", 0), Triple(")", ")", 0),
            Triple("[", "[", 0), Triple("]", "]", 0), Triple(";", ";", 0), Triple(":", ":", 0),
            Triple("'", "'", 0), Triple("=", "=", 0), Triple("#", "#", 0), Triple(".", ".", 0),
            Triple(",", ",", 0), Triple("_", "_", 0), Triple("!", "!", 0), Triple("&", "&", 0),
            Triple("|", "|", 0)
        )
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        keys.forEach { k -> row.addView(btn(k.first, false) { insert(k.second, k.third) }) }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.setBackgroundColor(bg)
        hs.addView(row)
        root.addView(hs)

        setContentView(root)

        ed.setText(text)
        ed.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!dirty) { dirty = true; refreshTitle() }
            }
        })
        refreshTitle()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { tryExit() }
        })
    }

    private fun btn(label: String, focusable: Boolean, on: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(green)
            setBackgroundColor(key)
            typeface = Typeface.MONOSPACE
            textSize = 13f
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isFocusable = focusable
            isFocusableInTouchMode = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { on() }
        }

    private fun refreshTitle() {
        val name = file.relativeTo(home).path
        title.text = (if (dirty) "* " else "") + name + (if (isNew) " [new]" else "")
    }

    private fun showPos(pos: Int) {
        val l = ed.layout ?: return
        val p = pos.coerceIn(0, ed.length())
        val line = l.getLineForOffset(p)
        status.text = "Ln ${line + 1}, Col ${p - l.getLineStart(line) + 1}"
    }

    private fun insert(s: String, back: Int) {
        val t = ed.text ?: return
        val a = minOf(ed.selectionStart, ed.selectionEnd).coerceAtLeast(0)
        val b = maxOf(ed.selectionStart, ed.selectionEnd).coerceAtLeast(0)
        t.replace(a, b, s)
        ed.setSelection(a + s.length - back)
    }

    private fun save(): Boolean {
        return try {
            file.writeText(ed.text.toString(), Charsets.UTF_8)
            dirty = false
            isNew = false
            refreshTitle()
            toast("Saved")
            true
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
            false
        }
    }

    private fun tryExit() {
        if (!dirty) { finish(); return }
        AlertDialog.Builder(this)
            .setMessage("Save changes to ${file.name}?")
            .setPositiveButton("Save") { _, _ -> if (save()) finish() }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }
}
