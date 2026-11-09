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
import java.io.File
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

    // Real folders/files live here: app's private storage (no permission needed)
    private val root: File by lazy { File(filesDir, "home").also { it.mkdirs() }.canonicalFile }
    private var cwd: File? = null            // null = home (~)
    private fun here(): File = cwd ?: root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        robot = findViewById(R.id.robot)
        say = findViewById(R.id.say)
        out = findViewById(R.id.out)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        add("Enova Launcher ready. Type 'ls', then 'open WhatsApp'.", dim)
        speak("Hello! I am Enova.")
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

    // Tap anywhere on the terminal to open the keyboard
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
        add("enova@home:${pathLabel()}$ $v", green)
        if (v.isEmpty()) return
        val act = pending
        if (act != null) {
            pending = null
            if (v.lowercase() == "y" || v.lowercase() == "yes") act() else add("cancelled", dim)
            return
        }
        val parts = v.split(Regex("\\s+"), 2)
        val cmd = parts[0].lowercase()
        val rest = parts.getOrNull(1)?.trim() ?: ""         // original case (for file names)
        val arg = rest.lowercase()
        val args = if (rest.isEmpty()) emptyList() else rest.split(Regex("\\s+"))
        when (cmd) {
            "ls", "apps" -> {
                if (apps.isEmpty()) loadApps()
                apps.forEach { add("✅ " + it.name) }
                speak("${apps.size} apps found.")
            }
            "open", "launch" -> openApp(arg)
            "exit", "home" -> exitToOtherLauncher()
            "switch" -> openSettings(Settings.ACTION_HOME_SETTINGS, "Default home app")
            "settings" -> openSettings(Settings.ACTION_SETTINGS, "Settings")
            "os" -> openOs(arg)
            "mkdir" -> mkdirCmd(args)
            "cd" -> cdCmd(args.firstOrNull() ?: "~")
            "cwd", "pwd" -> add(pathLabel())
            "dir", "ll" -> dirCmd()
            "cp" -> cpCmd(args)
            "mv" -> mvCmd(args)
            "cat" -> catCmd(args)
            "touch" -> touchCmd(args)
            "rm" -> rmCmd(args)
            "clear" -> out.text = ""
            "date" -> add(DateFormat.getDateTimeInstance().format(Date()))
            "help" -> add("ls            list apps\nopen <app>    open an app\nos            open EnovaOS\nexit          go to another launcher\nswitch        change default launcher\nsettings      phone settings\nmkdir <name>  create folder\ncd <folder>   enter folder (cd ~ = home)\ncwd           show current folder\ndir           list files in folder\ncp <src> <dst> copy file/folder\nmv <src> <dst>  move or rename\ncat <file>    show file text\ntouch <file>  create empty file\nrm <file>     delete file\nrm -rf <dir>  delete folder\nclear         clear screen\ndate          show date and time", dim)
            else -> add("$cmd: command not found", red)
        }
    }

    // ---------- file commands ----------

    private fun pathLabel(): String {
        val rel = here().relativeTo(root).path
        return if (rel.isEmpty()) "~" else "~/$rel"
    }

    // ~, ~/x, /x, x, .. -> real File, but never outside root
    private fun resolve(p: String): File? {
        val base = when {
            p == "~" -> root
            p.startsWith("~/") -> File(root, p.substring(2))
            p.startsWith("/") -> File(root, p.substring(1))
            else -> File(here(), p)
        }
        val c = base.canonicalFile
        return if (c == root || c.path.startsWith(root.path + File.separator)) c else null
    }

    private fun mkdirCmd(names: List<String>) {
        if (names.isEmpty()) { add("mkdir: folder name missing, e.g. mkdir notes", red); return }
        for (n in names) {
            val f = resolve(n)
            when {
                f == null -> add("mkdir: '$n': outside ~ not allowed", red)
                f.exists() -> add("mkdir: '$n': already exists", red)
                f.mkdirs() -> add("created $n", dim)
                else -> add("mkdir: '$n': failed", red)
            }
        }
    }

    private fun cdCmd(arg: String) {
        if (arg == "~") { cwd = null; return }
        val f = resolve(arg)
        when {
            f == null -> add("cd: '$arg': outside ~ not allowed", red)
            !f.exists() -> add("cd: '$arg': no such folder", red)
            !f.isDirectory -> add("cd: '$arg': not a folder", red)
            else -> cwd = if (f == root) null else f
        }
    }

    private fun dirCmd() {
        val list = here().listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
        if (list.isEmpty()) { add("(empty)", dim); return }
        list.forEach { add(if (it.isDirectory) "📁 ${it.name}" else "📄 ${it.name}") }
    }

    private fun cpCmd(a: List<String>) {
        if (a.size != 2) { add("cp: usage: cp <source> <dest>", red); return }
        val src = resolve(a[0])
        var dst = resolve(a[1])
        if (src == null || dst == null) { add("cp: outside ~ not allowed", red); return }
        if (!src.exists()) { add("cp: '${a[0]}': no such file or folder", red); return }
        if (dst.isDirectory) dst = File(dst, src.name)
        if (src.isDirectory && (dst == src || dst.path.startsWith(src.path + File.separator))) {
            add("cp: cannot copy a folder into itself", red); return
        }
        if (dst.exists()) { add("cp: '${dst.name}' already exists", red); return }
        try {
            val ok = if (src.isDirectory) src.copyRecursively(dst) else { src.copyTo(dst); true }
            if (ok) add("copied ${src.name} -> ${dst.name}", dim) else add("cp: failed", red)
        } catch (e: Exception) {
            add("cp: failed (${e.message})", red)
        }
    }

    // ---------- cat / rm / touch / mv ----------

    private var pending: (() -> Unit)? = null

    private fun catCmd(names: List<String>) {
        if (names.isEmpty()) { add("cat: usage: cat <file>", red); return }
        for (n in names) {
            val f = resolve(n)
            when {
                f == null -> add("cat: '$n': outside ~ not allowed", red)
                !f.exists() -> add("cat: '$n': no such file", red)
                f.isDirectory -> add("cat: '$n': is a folder", red)
                f.length() > 65536 -> add("cat: '$n': file too large (max 64 KB)", red)
                else -> try {
                    val b = f.readBytes()
                    if (b.take(512).any { it.toInt() == 0 }) add("cat: '$n': binary file", red)
                    else add(String(b, Charsets.UTF_8).trimEnd('\n'))
                } catch (e: Exception) {
                    add("cat: '$n': cannot read (${e.message})", red)
                }
            }
        }
    }

    private fun touchCmd(names: List<String>) {
        if (names.isEmpty()) { add("touch: usage: touch <file>", red); return }
        for (n in names) {
            val f = resolve(n)
            when {
                f == null -> add("touch: '$n': outside ~ not allowed", red)
                f.exists() -> f.setLastModified(System.currentTimeMillis())
                f.parentFile?.isDirectory != true -> add("touch: '$n': folder does not exist", red)
                else -> try {
                    f.createNewFile()
                    add("created $n", dim)
                } catch (e: Exception) {
                    add("touch: '$n': failed (${e.message})", red)
                }
            }
        }
    }

    private fun rmCmd(a: List<String>) {
        val rec = a.any { it.startsWith("-") && (it.contains('r') || it.contains('R')) }
        val names = a.filter { !it.startsWith("-") }
        if (names.isEmpty()) { add("rm: usage: rm <file> | rm -rf <folder>", red); return }
        val targets = mutableListOf<File>()
        for (n in names) {
            val f = resolve(n)
            when {
                f == null -> add("rm: '$n': outside ~ not allowed", red)
                f == root -> add("rm: cannot delete home (~)", red)
                !f.exists() -> add("rm: '$n': no such file or folder", red)
                f.isDirectory && !rec -> add("rm: '$n': is a folder (use rm -rf $n)", red)
                else -> targets.add(f)
            }
        }
        if (targets.isEmpty()) return
        if (targets.any { it.isDirectory }) {
            add("Delete ${targets.joinToString(", ") { it.name }} and everything inside? (y/n)", amber)
            pending = { targets.forEach { rmDo(it) } }
        } else {
            targets.forEach { rmDo(it) }
        }
    }

    private fun rmDo(f: File) {
        val c = cwd
        val inside = c != null && (c == f || c.path.startsWith(f.path + File.separator))
        val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
        if (ok) {
            if (inside) cwd = null
            add("deleted ${f.name}", dim)
        } else add("rm: '${f.name}': failed", red)
    }

    private fun mvCmd(a: List<String>) {
        if (a.size != 2) { add("mv: usage: mv <source> <dest>", red); return }
        val src = resolve(a[0])
        var dst = resolve(a[1])
        if (src == null || dst == null) { add("mv: outside ~ not allowed", red); return }
        if (src == root) { add("mv: cannot move home (~)", red); return }
        if (!src.exists()) { add("mv: '${a[0]}': no such file or folder", red); return }
        if (dst.isDirectory) dst = File(dst, src.name)
        if (src.isDirectory && (dst == src || dst.path.startsWith(src.path + File.separator))) {
            add("mv: cannot move a folder into itself", red); return
        }
        if (dst.exists()) { add("mv: '${dst.name}' already exists", red); return }
        if (src.renameTo(dst)) {
            val c = cwd
            if (c != null && (c == src || c.path.startsWith(src.path + File.separator))) cwd = null
            add("moved ${src.name} -> ${dst.name}", dim)
        } else add("mv: failed", red)
    }

    private fun openApp(arg: String) {
        if (arg.isEmpty()) { add("open: enter an app name, e.g. open WhatsApp", red); return }
        if (apps.isEmpty()) loadApps()
        val a = apps.firstOrNull { it.name.lowercase() == arg }
            ?: apps.firstOrNull { it.name.lowercase().startsWith(arg) }
            ?: apps.firstOrNull { it.name.lowercase().contains(arg) }
        if (a == null) { add("open: no app named '$arg'. Run ls to see apps.", red); speak("App not found."); return }
        val li = packageManager.getLaunchIntentForPackage(a.pkg)
        if (li == null) { add("open: cannot open ${a.name}.", red); return }
        add("Opening ${a.name}...", amber)
        speak("Opening ${a.name}.")
        handler.postDelayed({ startActivity(li) }, 700)
    }

    private fun exitToOtherLauncher() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val other = packageManager.queryIntentActivities(i, 0)
            .firstOrNull { it.activityInfo.packageName != packageName }
        if (other == null) { add("exit: no other launcher found. Try 'switch'.", red); return }
        val label = other.loadLabel(packageManager).toString()
        add("Exiting to $label...", amber)
        speak("Bye!")
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
            catch (e2: Exception) { add("could not open settings.", red) }
        }
    }

    private fun openOs(arg: String) {
        val name = arg.removePrefix("run").trim()
        val safe = if (Regex("[a-z0-9_-]{1,32}").matches(name)) name else null
        add("Launching EnovaOS...", amber)
        speak("Opening EnovaOS.")
        val i = Intent(this, OsActivity::class.java)
        if (safe != null) i.putExtra("run", safe)
        handler.postDelayed({ startActivity(i) }, 600)
    }
}
