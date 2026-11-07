package com.enova.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    data class App(val name: String, val pkg: String)

    private val fg = Color.parseColor("#B9F5C8")
    private val green = Color.parseColor("#00FF41")
    private val amber = Color.parseColor("#FFB454")
    private val red = Color.parseColor("#FF6B6B")
    private val dim = Color.parseColor("#4F7A5B")

    private lateinit var robot: RobotView
    private lateinit var say: TextView
    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private val handler = Handler(Looper.getMainLooper())
    private var typer: Runnable? = null
    private var apps = listOf<App>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        robot = findViewById(R.id.robot)
        say = findViewById(R.id.say)
        out = findViewById(R.id.out)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        add("Enova Launcher v1.1 ready. 'ls' likho, phir 'open WhatsApp'.", dim)
        speak("Namaste! Main Enova hoon.")
        val tap = View.OnClickListener { focusInput() }
        scroll.setOnClickListener(tap)
        out.setOnClickListener(tap)
        findViewById<View>(R.id.content).setOnClickListener(tap)
        input.setOnFocusChangeListener { _, f ->
            if (f) scroll.postDelayed({ scroll.fullScroll(ScrollView.FOCUS_DOWN) }, 300)
        }
        input.setOnEditorActionListener { _, id, ev ->
            val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (id == EditorInfo.IME_ACTION_SEND || enter) {
                run(input.text.toString())
                input.text.clear()
                true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    // Home dabane par launcher hi rahe, back se band na ho
    @Deprecated("Launcher ignores back")
    override fun onBackPressed() {}

    // Terminal par kahin bhi tap karo to keyboard khul jaye
    private fun focusInput() {
        input.requestFocus()
        getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(i, 0)
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }

    private fun add(t: String, color: Int = fg) {
        val s = SpannableString(t + "\n")
        s.setSpan(ForegroundColorSpan(color), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.append(s)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun speak(t: String) {
        typer?.let { handler.removeCallbacks(it) }
        say.text = ""
        robot.talking = true
        var n = 0
        val r = object : Runnable {
            override fun run() {
                if (n < t.length) {
                    n++
                    say.text = t.substring(0, n)
                    handler.postDelayed(this, 28)
                } else robot.talking = false
            }
        }
        typer = r
        handler.post(r)
    }

    private fun run(raw: String) {
        val v = raw.trim()
        add("enova@home:~$ $v", green)
        if (v.isEmpty()) return
        val parts = v.split(Regex("\\s+"), 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim()?.lowercase() ?: ""
        when (cmd) {
            "ls", "apps" -> {
                if (apps.isEmpty()) loadApps()
                apps.forEach { add("✅ " + it.name) }
                speak("${apps.size} apps mile.")
            }
            "open", "launch" -> openApp(arg)
            "exit", "home" -> exitToOtherLauncher()
            "switch" -> openSettings(Settings.ACTION_HOME_SETTINGS, "Default home app")
            "settings" -> openSettings(Settings.ACTION_SETTINGS, "Settings")
            "clear" -> out.text = ""
            "date" -> add(DateFormat.getDateTimeInstance().format(Date()))
            "help" -> add("ls            apps ki list\nopen <app>    app kholo\nexit          dusre launcher par jao\nswitch        default launcher badlo\nsettings      phone settings\nclear         screen saaf\ndate          samay", dim)
            else -> add("$cmd: command not found", red)
        }
    }

    private fun openApp(arg: String) {
        if (arg.isEmpty()) { add("open: app ka naam likho, e.g. open WhatsApp", red); return }
        if (apps.isEmpty()) loadApps()
        val a = apps.firstOrNull { it.name.lowercase() == arg }
            ?: apps.firstOrNull { it.name.lowercase().startsWith(arg) }
            ?: apps.firstOrNull { it.name.lowercase().contains(arg) }
        if (a == null) { add("open: '$arg' naam ka app nahi mila. ls chalao.", red); speak("Ye app nahi mila."); return }
        val li = packageManager.getLaunchIntentForPackage(a.pkg)
        if (li == null) { add("open: ${a.name} khul nahi sakta.", red); return }
        add("Opening ${a.name}...", amber)
        speak("${a.name} khol raha hoon.")
        handler.postDelayed({ startActivity(li) }, 700)
    }

    private fun exitToOtherLauncher() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val other = packageManager.queryIntentActivities(i, 0)
            .firstOrNull { it.activityInfo.packageName != packageName }
        if (other == null) { add("exit: dusra launcher nahi mila. 'switch' try karo.", red); return }
        val label = other.loadLabel(packageManager).toString()
        add("Exiting to $label...", amber)
        speak("Bye bhai!")
        val go = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClassName(other.activityInfo.packageName, other.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        handler.postDelayed({ startActivity(go) }, 700)
    }

    private fun openSettings(action: String, name: String) {
        add("Opening $name...", amber)
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (e2: Exception) { add("settings khul nahi payi.", red) }
        }
    }
}
