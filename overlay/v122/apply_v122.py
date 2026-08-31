from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
TEXT = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Header fix from the physical-device screenshot: much smaller time,
# real breathing room, and numeric day/year while keeping Doran vectors.
s = UI.read_text(encoding='utf-8')
old = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 17.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(7.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 13.dp,\n        centered = true,\n        words = true\n    )'''
new = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 12.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(12.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 10.dp,\n        centered = true,\n        words = false\n    )'''
if s.count(old) != 1:
    raise SystemExit(f'Expected v1.2.1 clock/date block once; found {s.count(old)}')
s = s.replace(old, new, 1)
UI.write_text(s, encoding='utf-8')

# 2) Add generic but safe orthographic cleanup. It only joins already-separated
# Persian morphology; it does not invent words or paraphrase the transcript.
s = TEXT.read_text(encoding='utf-8')
call = '        text = normalizeCommonOrthography(text)\n'
replacement = '        text = normalizeCommonOrthography(text)\n        text = normalizeV122Orthography(text)\n'
if 'text = normalizeV122Orthography(text)' not in s:
    if s.count(call) != 1:
        raise SystemExit(f'Expected normalizeCommonOrthography call once; found {s.count(call)}')
    s = s.replace(call, replacement, 1)

anchor = '    private fun restoreFinalPunctuation(input: String): String {'
func = r'''    private fun normalizeV122Orthography(input: String): String {
        var text = input
        // Generic separated verbal prefix: «می روم» -> «می‌روم», «نمی دونم» -> «نمی‌دونم».
        text = text.replace(
            Regex("(^|\\s)(ن?می)\\s+([\\u0600-\\u06FF]+)"),
            "$1$2‌$3"
        )
        // Comparative suffixes: «بهتر ترین» -> «بهتر‌ترین».
        text = text.replace(
            Regex("(?<=[\\u0600-\\u06FF])\\s+(تر|ترین)(?=\\s|$|[،,.!؟؛:])"),
            "‌$1"
        )
        // Colloquial/standard ezafe after heh: «خونه ی» -> «خونه‌ی».
        text = text.replace(
            Regex("ه\\s+ی(?=\\s|$|[،,.!؟؛:])"),
            "ه‌ی"
        )
        return text
    }

'''
if 'private fun normalizeV122Orthography' not in s:
    if anchor not in s:
        raise SystemExit('restoreFinalPunctuation anchor missing')
    s = s.replace(anchor, func + anchor, 1)
TEXT.write_text(s, encoding='utf-8')

# 3) Side-by-side test package so the user's previous journal install is untouched.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v121fast"', 'applicationId = "com.nameemrooz.journal.v122accuracy"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*24\b', 'versionCode = 25', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.2\.1"', 'versionName = "1.2.2"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'Version patch failed: {(a, b, c)}')

# 4) Replace the legacy single-model Gradle downloader. Without this, preBuild
# silently re-downloads the old 125 MB streaming Koochik model and bloats the APK.
model_block_pattern = re.compile(
    r'val modelDir = layout\.projectDirectory\.dir\("src/main/assets/models/shenava_v10_ctc"\).*?'
    r'tasks\.matching \{ it\.name == "preBuild" \}\.configureEach \{ dependsOn\(fetchPersianModel\) \}',
    re.S,
)
model_block = r'''val liveModelDir = layout.projectDirectory.dir("src/main/assets/models/shenava_rizeh_stream")
val finalModelDir = layout.projectDirectory.dir("src/main/assets/models/shenava_v10_ctc_offline")

data class ModelAsset(
    val directory: String,
    val name: String,
    val url: String,
    val sha256: String
)

val modelAssets = listOf(
    ModelAsset(
        "src/main/assets/models/shenava_rizeh_stream",
        "model.onnx",
        "https://huggingface.co/mah92/sherpa-onnx-nemo-ctc-fa-shenava-rizeh-v1.0-streaming-int8-2026-06-26/resolve/main/model.int8.onnx?download=true",
        "889a4fdfeb25ea0858493294842d36c637acf391f7f49f6e98881709a468b6bc"
    ),
    ModelAsset(
        "src/main/assets/models/shenava_rizeh_stream",
        "tokens.txt",
        "https://huggingface.co/mah92/sherpa-onnx-nemo-ctc-fa-shenava-rizeh-v1.0-streaming-int8-2026-06-26/resolve/main/tokens.txt?download=true",
        "8e192963f6e666dfa5721e5cbd4710bc1ef592460a45f08cefc94b2db16a6954"
    ),
    ModelAsset(
        "src/main/assets/models/shenava_v10_ctc_offline",
        "model.onnx",
        "https://huggingface.co/mah92/sherpa-onnx-nemo-ctc-fa-shenava-koochik-v1.0-non-streaming-int8-2026-06-26/resolve/main/model.int8.onnx?download=true",
        "9d487fc2aa8cdb742a6e2f000be4c25e6b620faf73c9a335fdb310cc7879182e"
    ),
    ModelAsset(
        "src/main/assets/models/shenava_v10_ctc_offline",
        "tokens.txt",
        "https://huggingface.co/mah92/sherpa-onnx-nemo-ctc-fa-shenava-koochik-v1.0-non-streaming-int8-2026-06-26/resolve/main/tokens.txt?download=true",
        "8e192963f6e666dfa5721e5cbd4710bc1ef592460a45f08cefc94b2db16a6954"
    )
)

fun File.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            md.update(buffer, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

val fetchPersianModel by tasks.registering {
    group = "model"
    description = "Downloads and verifies the pinned Emrooz live and final Persian CTC models."
    outputs.dirs(liveModelDir, finalModelDir)
    doLast {
        modelAssets.forEach { asset ->
            val dir = layout.projectDirectory.dir(asset.directory).asFile
            dir.mkdirs()
            val out = File(dir, asset.name)
            val existingOk = out.exists() && out.length() >= 1024 && out.sha256().equals(asset.sha256, ignoreCase = true)
            if (!existingOk) {
                out.delete()
                val tmp = File(dir, asset.name + ".part")
                tmp.delete()
                println("Downloading ${asset.directory}/${asset.name} ...")
                URL(asset.url).openStream().use { input ->
                    tmp.outputStream().buffered(1024 * 1024).use { output -> input.copyTo(output, 1024 * 1024) }
                }
                if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
            }
            val actual = out.sha256()
            check(actual.equals(asset.sha256, ignoreCase = true)) {
                "SHA-256 mismatch for ${asset.directory}/${asset.name}: expected=${asset.sha256} actual=$actual"
            }
            println("${asset.directory}/${asset.name}: ${out.length() / 1024 / 1024} MB")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchPersianModel) }'''
s, d = model_block_pattern.subn(model_block, s, count=1)
if d != 1:
    raise SystemExit(f'Legacy Gradle model downloader patch failed: {d}')

GRADLE.write_text(s, encoding='utf-8')

print('V122_ACCURACY_UI_PATCH_OK')
