import os, sys, shutil

p = os.path.expanduser("~/enova-launcher-v1.1/app/src/main/java/com/enova/launcher/MainActivity.kt")
s = open(p, encoding="utf-8").read()

if "private fun catCmd" in s:
    print("already patched")
    sys.exit(0)

def rep(old, new):
    global s
    n = s.count(old)
    if n != 1:
        print("ANCHOR FAIL (found %d): %r" % (n, old[:60]))
        sys.exit(1)
    s = s.replace(old, new)

# 1) new commands in when(cmd)
rep('            "cp" -> cpCmd(args)\n',
    '            "cp" -> cpCmd(args)\n'
    '            "mv" -> mvCmd(args)\n'
    '            "cat" -> catCmd(args)\n'
    '            "touch" -> touchCmd(args)\n'
    '            "rm" -> rmCmd(args)\n')

# 2) y/n confirm hook (for rm -rf)
HOOK = r'''        val act = pending
        if (act != null) {
            pending = null
            if (v.lowercase() == "y" || v.lowercase() == "yes") act() else add("cancelled", dim)
            return
        }
'''
rep('        if (v.isEmpty()) return\n', '        if (v.isEmpty()) return\n' + HOOK)

# 3) help text
rep(r'copy file/folder\nclear',
    r'copy file/folder\nmv <src> <dst>  move or rename\ncat <file>    show file text\ntouch <file>  create empty file\nrm <file>     delete file\nrm -rf <dir>  delete folder\nclear')

# 4) functions
FUNCS = r'''    // ---------- cat / rm / touch / mv ----------

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

'''
rep('    private fun openApp(arg: String) {\n', FUNCS + '    private fun openApp(arg: String) {\n')

shutil.copy(p, p + ".bak")
open(p, "w", encoding="utf-8").write(s)
print("patched OK (backup: MainActivity.kt.bak)")
