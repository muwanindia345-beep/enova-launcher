import os, sys, re, shutil

base = os.path.expanduser("~/enova-launcher-v1.1")
mp = base + "/app/src/main/java/com/enova/launcher/MainActivity.kt"
xp = base + "/app/src/main/AndroidManifest.xml"
gp = base + "/app/build.gradle.kts"

m = open(mp, encoding="utf-8").read()
x = open(xp, encoding="utf-8").read()
g = open(gp, encoding="utf-8").read()

if "private fun nanoCmd" in m:
    print("already patched")
    sys.exit(0)

def rep(text, old, new, label):
    n = text.count(old)
    if n != 1:
        print("ANCHOR FAIL [%s] (found %d): %r" % (label, n, old[:60]))
        sys.exit(1)
    return text.replace(old, new)

# MainActivity: command
m = rep(m, '            "rm" -> rmCmd(args)\n',
        '            "rm" -> rmCmd(args)\n            "nano" -> nanoCmd(args)\n', "when")
# help
m = rep(m, r'delete folder\nclear', r'delete folder\nnano <file>   edit file\nclear', "help")
# function
FUNC = r'''    private fun nanoCmd(a: List<String>) {
        if (a.isEmpty()) { add("nano: usage: nano <file>", red); return }
        val f = resolve(a[0])
        when {
            f == null -> add("nano: '${a[0]}': outside ~ not allowed", red)
            f.isDirectory -> add("nano: '${a[0]}': is a folder", red)
            f.parentFile?.isDirectory != true -> add("nano: '${a[0]}': folder does not exist", red)
            else -> startActivity(Intent(this, NanoActivity::class.java).putExtra("path", f.path))
        }
    }

'''
m = rep(m, '    private fun openApp(arg: String) {\n', FUNC + '    private fun openApp(arg: String) {\n', "func")

# Manifest
ACT = '''        <activity
            android:name=".NanoActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize|uiMode"
            android:windowSoftInputMode="adjustResize" />
'''
x = rep(x, '    </application>\n', ACT + '    </application>\n', "manifest")

# version bump
mc = re.search(r'versionCode = (\d+)', g)
mn = re.search(r'versionName = "([\d.]+)"', g)
if mc and mn:
    nc = int(mc.group(1)) + 1
    parts = mn.group(1).split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    nv = ".".join(parts)
    g = g.replace(mc.group(0), "versionCode = %d" % nc).replace(mn.group(0), 'versionName = "%s"' % nv)
    print("version -> %s (code %d)" % (nv, nc))

bk = os.path.expanduser("~/enova-backup-nano")
os.makedirs(bk, exist_ok=True)
for pth in (mp, xp, gp):
    shutil.copy(pth, bk + "/" + os.path.basename(pth) + ".bak")
open(mp, "w", encoding="utf-8").write(m)
open(xp, "w", encoding="utf-8").write(x)
open(gp, "w", encoding="utf-8").write(g)
print("patched OK (backups in ~/enova-backup-nano)")
