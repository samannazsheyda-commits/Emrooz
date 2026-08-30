from pathlib import Path
p=Path('app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt')
s=p.read_text()
# imports
s=s.replace('import android.content.pm.PackageManager\n', 'import android.content.pm.PackageManager\nimport android.media.MediaPlayer\n')
s=s.replace('import androidx.activity.compose.rememberLauncherForActivityResult\n', 'import androidx.activity.compose.BackHandler\nimport androidx.activity.compose.rememberLauncherForActivityResult\n')
s=s.replace('import androidx.compose.foundation.Canvas\n', 'import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.combinedClickable\n')
# Remove hardcoded colors
for line in [
'private val Cream = Color(0xFFF7EFE3)\n','private val Paper = Color(0xFFFFFAF1)\n','private val Olive = Color(0xFF3E5120)\n','private val OliveSoft = Color(0xFF7D895F)\n','private val Ink = Color(0xFF25261F)\n','private val Muted = Color(0xFF777468)\n','private val Danger = Color(0xFF9E4F48)\n']:
    s=s.replace(line,'')
# Material colors global replacements
repls={
'Cream':'MaterialTheme.colorScheme.background',
'Paper':'MaterialTheme.colorScheme.surface',
'OliveSoft':'MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)',
'Olive':'MaterialTheme.colorScheme.primary',
'Ink':'MaterialTheme.colorScheme.onBackground',
'Muted':'MaterialTheme.colorScheme.onSurfaceVariant',
'Danger':'MaterialTheme.colorScheme.error',
}
for a,b in repls.items(): s=s.replace(a,b)
# NameEmroozApp theme and archive preview + back
old='''    var screen by remember { mutableStateOf(Screen.HOME) }\n    var selected by remember { mutableStateOf<JournalEntry?>(null) }\n\n    NameEmroozTheme(AppTheme.LIGHT) {'''
new='''    var screen by remember { mutableStateOf(Screen.HOME) }\n    var selected by remember { mutableStateOf<JournalEntry?>(null) }\n    val theme by settings.theme.collectAsState(initial = AppTheme.LIGHT)\n    val archivePreview by settings.archivePreview.collectAsState(initial = false)\n\n    BackHandler(enabled = screen != Screen.HOME) {\n        screen = if (screen == Screen.DETAIL) Screen.ARCHIVE else Screen.HOME\n    }\n\n    NameEmroozTheme(theme) {'''
s=s.replace(old,new)
s=s.replace('''                    Screen.ARCHIVE -> ArchiveScreen(\n                        entries = entries,\n                        onBack = { screen = Screen.HOME },\n                        onEntry = { selected = it; screen = Screen.DETAIL }\n                    )''','''                    Screen.ARCHIVE -> ArchiveScreen(\n                        entries = entries,\n                        showPreview = archivePreview,\n                        onBack = { screen = Screen.HOME },\n                        onEntry = { selected = it; screen = Screen.DETAIL },\n                        onDelete = { vm.delete(it) }\n                    )''')
# home top archive action label
s=s.replace('''            RoundTopAction(Icons.Default.Inventory2, "آرشیو", onArchive)\n            Spacer(Modifier.weight(1f))\n            RoundTopAction(Icons.Default.Settings, "تنظیمات", onSettings)''','''            TopActionWithLabel(Icons.Default.Inventory2, "نامه‌های گذشته", onArchive)\n            Spacer(Modifier.weight(1f))\n            TopActionWithLabel(Icons.Default.Settings, "تنظیمات", onSettings)''')
# Home scope and begin recording sound
s=s.replace('''    val scrollState = rememberScrollState()\n    var ready''','''    val scrollState = rememberScrollState()\n    val scope = rememberCoroutineScope()\n    var ready''')
s=s.replace('''    fun beginRecording() {\n        text = ""\n        error = null\n        engine.start()\n    }''','''    fun beginRecording() {\n        text = ""\n        error = null\n        scope.launch {\n            playCue(context, R.raw.record_start, 0.18f)\n            delay(120)\n            engine.start()\n        }\n    }''')
# save sound
s=s.replace('''                        onSave(edited)\n                        text = ""''','''                        onSave(edited)\n                        playCue(context, R.raw.archive_ding, 0.20f)\n                        text = ""''')
# Today letter smaller
s=s.replace('''                    31.dp,\n                    MaterialTheme.colorScheme.primary,\n                    contentDescription = "نامه امروزت"''','''                    26.dp,\n                    MaterialTheme.colorScheme.primary,\n                    contentDescription = "نامه امروزت"''')
# Current clock/date replace whole function
start=s.index('@Composable\nprivate fun CurrentClockAndDate()')
end=s.index('\n@Composable\nprivate fun LetterEditorCard', start)
newfun='''@Composable\nprivate fun CurrentClockAndDate() {\n    var now by remember { mutableStateOf(System.currentTimeMillis()) }\n    LaunchedEffect(Unit) {\n        while (true) {\n            now = System.currentTimeMillis()\n            delay(30_000)\n        }\n    }\n    Text(\n        JalaliDate.time(now),\n        fontFamily = UiFont,\n        fontSize = 21.sp,\n        fontWeight = FontWeight.Normal,\n        color = MaterialTheme.colorScheme.onBackground\n    )\n    Spacer(Modifier.height(2.dp))\n    Text(\n        JalaliDate.pretty(now),\n        fontFamily = UiFont,\n        fontSize = 14.sp,\n        fontWeight = FontWeight.Normal,\n        color = MaterialTheme.colorScheme.onSurfaceVariant\n    )\n}\n'''
s=s[:start]+newfun+s[end:]
# RoundTopAction replace with labeled version
start=s.index('@Composable\nprivate fun RoundTopAction')
end=s.index('\n@Composable\nprivate fun DoranImage',start)
newfun='''@Composable\nprivate fun TopActionWithLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {\n    Column(horizontalAlignment = Alignment.CenterHorizontally) {\n        IconButton(\n            onClick = onClick,\n            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)\n                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)\n        ) {\n            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))\n        }\n        Spacer(Modifier.height(2.dp))\n        Text(label, fontFamily = UiFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)\n    }\n}\n\nprivate fun playCue(context: android.content.Context, resId: Int, volume: Float) {\n    runCatching {\n        MediaPlayer.create(context, resId)?.apply {\n            setVolume(volume, volume)\n            setOnCompletionListener { it.release() }\n            start()\n        }\n    }\n}\n'''
s=s[:start]+newfun+s[end:]
# recording button pulsing alpha
s=s.replace('''colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))''','''colors = ButtonDefaults.buttonColors(\n                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (listening) 0.72f + pulse * 0.28f else 1f),\n                contentColor = MaterialTheme.colorScheme.onPrimary,\n                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)\n            )''')
# Archive block replace
start=s.index('@Composable\nprivate fun ArchiveScreen')
end=s.index('\n@Composable\nprivate fun Detail',start)
archive='''@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun ArchiveScreen(\n    entries: List<JournalEntry>,\n    showPreview: Boolean,\n    onBack: () -> Unit,\n    onEntry: (JournalEntry) -> Unit,\n    onDelete: (JournalEntry) -> Unit\n) {\n    var pendingDelete by remember { mutableStateOf<JournalEntry?>(null) }\n    pendingDelete?.let { entry ->\n        AlertDialog(\n            onDismissRequest = { pendingDelete = null },\n            title = { Text("این نامه حذف شود؟", fontFamily = UiFont) },\n            text = { Text(JalaliDate.archiveCompact(entry.createdAt), fontFamily = UiFont) },\n            confirmButton = {\n                TextButton(onClick = { onDelete(entry); pendingDelete = null }) {\n                    Text("حذف", fontFamily = UiFont, color = MaterialTheme.colorScheme.error)\n                }\n            },\n            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("نه", fontFamily = UiFont) } }\n        )\n    }\n\n    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp, vertical = 12.dp)) {\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n            Text("نامه‌های گذشته", Modifier.weight(1f), textAlign = TextAlign.Right, fontFamily = UiFont, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)\n            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "برگشت", tint = MaterialTheme.colorScheme.primary) }\n        }\n        Spacer(Modifier.height(8.dp))\n        if (entries.isEmpty()) {\n            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n                Text("هنوز نامه‌ای ذخیره نشده", fontFamily = UiFont, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)\n            }\n        } else {\n            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {\n                items(entries, key = { it.id }) { entry ->\n                    ArchiveCard(entry, showPreview, onEntry) { pendingDelete = entry }\n                }\n            }\n        }\n    }\n}\n\n@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun ArchiveCard(\n    entry: JournalEntry,\n    showPreview: Boolean,\n    onEntry: (JournalEntry) -> Unit,\n    onLongDelete: () -> Unit\n) {\n    Card(\n        modifier = Modifier.fillMaxWidth()\n            .combinedClickable(onClick = { onEntry(entry) }, onLongClick = onLongDelete)\n            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(15.dp)),\n        shape = RoundedCornerShape(15.dp),\n        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n        elevation = CardDefaults.cardElevation(0.dp)\n    ) {\n        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (showPreview) 10.dp else 8.dp)) {\n            Text(\n                JalaliDate.archiveCompact(entry.createdAt),\n                Modifier.fillMaxWidth(),\n                textAlign = TextAlign.Right,\n                fontFamily = UiFont,\n                fontSize = 15.sp,\n                fontWeight = FontWeight.Normal,\n                color = MaterialTheme.colorScheme.onSurface\n            )\n            if (showPreview) {\n                Spacer(Modifier.height(5.dp))\n                Text(\n                    entry.text.replace("\\n", " ").take(90),\n                    Modifier.fillMaxWidth(),\n                    textAlign = TextAlign.Right,\n                    maxLines = 2,\n                    fontFamily = TranscriptFont,\n                    fontSize = 15.sp,\n                    lineHeight = 24.sp,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant\n                )\n            }\n        }\n    }\n}\n'''
s=s[:start]+archive+s[end:]
# Detail date lines -> compact text
s=s.replace('''        DoranDateLine(entry.createdAt, MaterialTheme.colorScheme.primary, 17.dp, centered = false, words = true)\n        Spacer(Modifier.height(5.dp))\n        DoranTimeLine(entry.createdAt, MaterialTheme.colorScheme.onSurfaceVariant, 16.dp, includeLabel = true, centered = false)''','''        Text(\n            JalaliDate.archiveCompact(entry.createdAt),\n            fontFamily = UiFont, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant\n        )''')
# Settings add theme and preview state
s=s.replace('''    val reminder by store.reminderEnabled.collectAsState(initial = true)''','''    val reminder by store.reminderEnabled.collectAsState(initial = true)\n    val theme by store.theme.collectAsState(initial = AppTheme.LIGHT)\n    val archivePreview by store.archivePreview.collectAsState(initial = false)''')
insert='''\n        Spacer(Modifier.height(16.dp))\n        Text("ظاهر برنامه", Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)\n        Spacer(Modifier.height(8.dp))\n        ThemeSelector(theme) { scope.launch { store.setTheme(it) } }\n        Spacer(Modifier.height(12.dp))\n        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {\n            Column(Modifier.padding(horizontal = 15.dp, vertical = 4.dp)) {\n                SettingsRow(Icons.Default.Inventory2, "نمایش خلاصه متن در نامه‌های گذشته") {\n                    Switch(archivePreview, { scope.launch { store.setArchivePreview(it) } })\n                }\n            }\n        }\n'''
needle='''        Spacer(Modifier.height(26.dp))\n        Text("حریم خصوصی"'''
s=s.replace(needle,insert+'''        Spacer(Modifier.height(26.dp))\n        Text("حریم خصوصی"''')
# Add ThemeSelector before SettingsRow
idx=s.index('@Composable\nprivate fun SettingsRow')
themefun='''@Composable\nprivate fun ThemeSelector(current: AppTheme, onTheme: (AppTheme) -> Unit) {\n    val options = listOf(\n        AppTheme.LIGHT to "کرم زیتونی",\n        AppTheme.NAVY to "شب سرمه‌ای",\n        AppTheme.WARM to "ذغالی کهربایی",\n        AppTheme.FOREST to "سبز مه‌آلود"\n    )\n    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {\n        options.chunked(2).forEach { row ->\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                row.forEach { (value, label) ->\n                    OutlinedButton(\n                        onClick = { onTheme(value) },\n                        modifier = Modifier.weight(1f).height(44.dp),\n                        shape = RoundedCornerShape(14.dp),\n                        colors = ButtonDefaults.outlinedButtonColors(\n                            containerColor = if (current == value) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,\n                            contentColor = MaterialTheme.colorScheme.onSurface\n                        )\n                    ) { Text(label, fontFamily = UiFont, fontSize = 13.sp) }\n                }\n            }\n        }\n    }\n}\n\n'''
s=s[:idx]+themefun+s[idx:]
p.write_text(s)

# SettingsStore
p=Path('app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt')
s=p.read_text()
s=s.replace('private val reminderKey = booleanPreferencesKey("reminder")','private val reminderKey = booleanPreferencesKey("reminder")\n    private val archivePreviewKey = booleanPreferencesKey("archive_preview")')
s=s.replace('AppTheme.DARK_ELEGANT.name) }.getOrDefault(AppTheme.DARK_ELEGANT)','AppTheme.LIGHT.name) }.getOrDefault(AppTheme.LIGHT)')
s=s.replace('val reminderEnabled = context.dataStore.data.map { it[reminderKey] ?: true }','val reminderEnabled = context.dataStore.data.map { it[reminderKey] ?: true }\n    val archivePreview = context.dataStore.data.map { it[archivePreviewKey] ?: false }')
s=s.replace('suspend fun setReminder(value: Boolean) = context.dataStore.edit { it[reminderKey] = value }','suspend fun setReminder(value: Boolean) = context.dataStore.edit { it[reminderKey] = value }\n    suspend fun setArchivePreview(value: Boolean) = context.dataStore.edit { it[archivePreviewKey] = value }')
p.write_text(s)

# JalaliDate compact format
p=Path('app/src/main/java/com/nameemrooz/journal/util/JalaliDate.kt')
s=p.read_text()
needle='''    fun archiveStamp(millis: Long): String {\n        val p = parts(millis)\n        return "${week[p.weekday]} ${ordinalDay(p.day)} ${months[p.month - 1]} ${numberToWords(p.year)}، ساعت ${time(millis)}"\n    }\n'''
new=needle+'''\n    fun archiveCompact(millis: Long): String {\n        val p = parts(millis)\n        return persianDigits("${p.day} ${months[p.month - 1]} ${p.year}، ساعت %02d:%02d".format(p.hour, p.minute))\n    }\n'''
s=s.replace(needle,new)
p.write_text(s)

# VM remove title generator behavior
p=Path('app/src/main/java/com/nameemrooz/journal/ui/AppViewModel.kt')
s=p.read_text().replace('import com.nameemrooz.journal.util.TitleGenerator\n','')
s=s.replace('repo.save(edited, TitleGenerator.resolve(title, edited))','repo.save(edited, "")')
s=s.replace('repo.update(e.copy(text = edited, title = TitleGenerator.resolve(e.title, edited)))','repo.update(e.copy(text = edited, title = ""))')
p.write_text(s)

# Version/package for install-safe final build
p=Path("app/build.gradle.kts")
s=p.read_text()
s=s.replace("com.nameemrooz.journal.v116final","com.nameemrooz.journal.v117final")
s=s.replace("versionCode = 19","versionCode = 20")
s=s.replace("versionName = \"1.1.6\"","versionName = \"1.1.7\"")
p.write_text(s)

# Generate tiny offline UI sound cues; no audio recording is stored.
import wave, math, struct
raw=Path("app/src/main/res/raw"); raw.mkdir(parents=True, exist_ok=True)
def write_wav(path, samples, sr=22050):
    with wave.open(str(path),"wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(b"".join(struct.pack("<h", max(-32767,min(32767,int(x)))) for x in samples))
sr=22050
dur=.18; samples=[]
for i in range(int(sr*dur)):
    t=i/sr; env=max(0,min(1,t/.025,(dur-t)/.06))
    samples.append(32767*.11*env*math.sin(2*math.pi*520*t))
write_wav(raw/"record_start.wav", samples, sr)
dur=.5; samples=[]
for i in range(int(sr*dur)):
    t=i/sr; env=max(0,min(1,t/.025,(dur-t)/.15))
    v=math.sin(2*math.pi*660*t)
    if t>.1: v += .75*math.sin(2*math.pi*880*(t-.1))
    samples.append(32767*.12*env*v/1.75)
write_wav(raw/"archive_ding.wav", samples, sr)
print("v1.1.7 UI/archive/theme/audio patch applied")
