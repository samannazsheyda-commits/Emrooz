from pathlib import Path

root = Path('.')

gradle = root / 'app/build.gradle.kts'
s = gradle.read_text()
s = s.replace('versionCode = 31', 'versionCode = 32').replace('versionName = "2.0.1"', 'versionName = "2.0.2"')
marker = '    buildFeatures { compose = true; buildConfig = true }\n'
jvm17 = '''    compileOptions {\n        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17\n        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17\n    }\n\n'''
if 'sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17' not in s:
    assert marker in s
    s = s.replace(marker, jvm17 + marker, 1)
s = s.replace('androidx.room:room-runtime:2.6.1', 'androidx.room:room-runtime:2.8.5')
s = s.replace('androidx.room:room-ktx:2.6.1', 'androidx.room:room-ktx:2.8.5')
s = s.replace('androidx.room:room-compiler:2.6.1', 'androidx.room:room-compiler:2.8.5')
gradle.write_text(s)

vm = root / 'app/src/main/java/com/nameemrooz/journal/ui/AppViewModel.kt'
s = vm.read_text().replace('import com.nameemrooz.journal.util.TitleGenerator\n', '')
s = s.replace('repo.save(clean, TitleGenerator.generate(clean))', 'repo.save(clean, "")')
vm.write_text(s)

jal = root / 'app/src/main/java/com/nameemrooz/journal/util/JalaliDate.kt'
s = jal.read_text()
old = '''    fun pretty(millis: Long): String {\n        val cal = Calendar.getInstance().apply { timeInMillis = millis }\n        val (jy, jm, jd) = toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH))\n        return "${week[cal.get(Calendar.DAY_OF_WEEK)] ?: ""}  $jd ${months[jm-1]} $jy"\n    }\n'''
new = '''    fun pretty(millis: Long): String = "${archiveDay(millis)}  ${archiveDate(millis)}"\n\n    fun archiveDay(millis: Long): String {\n        val cal = Calendar.getInstance().apply { timeInMillis = millis }\n        return week[cal.get(Calendar.DAY_OF_WEEK)] ?: ""\n    }\n\n    fun archiveDate(millis: Long): String {\n        val cal = Calendar.getInstance().apply { timeInMillis = millis }\n        val (jy, jm, jd) = toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH))\n        return "$jd ${months[jm-1]} $jy"\n    }\n\n    fun time(millis: Long): String = SimpleDateFormat("HH:mm", Locale("fa", "IR")).format(Date(millis))\n'''
assert old in s
jal.write_text(s.replace(old, new))

ui = root / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
s = ui.read_text()
s = s.replace('Screen.LIST -> Entries("نوشته‌های قبلی من", active,', 'Screen.LIST -> Entries("نوشته‌های قبلی", active,')
s = s.replace('        if(recent.isNotEmpty()) TextButton(onClick=onList) { Text(if(language==AppLanguage.FA) "نامه‌های قبلی" else "Previous letters") }\n', '        TextButton(onClick=onList) { Text(if(language==AppLanguage.FA) "نوشته‌های قبلی" else "Previous letters") }\n')
old = '''                    Column(Modifier.padding(16.dp)) {\n                        Text(e.title, Modifier.fillMaxWidth(), textAlign=TextAlign.Right, fontSize=21.sp, fontWeight=FontWeight.Bold)\n                        Text(JalaliDate.pretty(e.createdAt), Modifier.fillMaxWidth(), textAlign=TextAlign.Right, color=MaterialTheme.colorScheme.primary)\n                        Text(e.text.take(110), Modifier.fillMaxWidth(), textAlign=TextAlign.Right, maxLines=2)\n                    }\n'''
new = '''                    Column(Modifier.padding(horizontal=18.dp, vertical=15.dp)) {\n                        Text(JalaliDate.archiveDay(e.createdAt), Modifier.fillMaxWidth(), textAlign=TextAlign.Right, fontSize=21.sp, fontWeight=FontWeight.Bold)\n                        Text(JalaliDate.archiveDate(e.createdAt), Modifier.fillMaxWidth().padding(top=4.dp), textAlign=TextAlign.Right, color=MaterialTheme.colorScheme.primary, fontSize=16.sp)\n                        Text(JalaliDate.time(e.createdAt), Modifier.fillMaxWidth().padding(top=4.dp), textAlign=TextAlign.Right, color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=16.sp)\n                    }\n'''
assert old in s
s = s.replace(old, new)
old = '''        Row(verticalAlignment=Alignment.CenterVertically) {\n            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"برگشت")}\n            Text(entry.title, Modifier.weight(1f), textAlign=TextAlign.Right, fontSize=25.sp, fontWeight=FontWeight.Bold)\n        }\n        Text(JalaliDate.pretty(entry.createdAt), Modifier.fillMaxWidth(), textAlign=TextAlign.Right, color=MaterialTheme.colorScheme.primary)\n'''
new = '''        Row(verticalAlignment=Alignment.CenterVertically) {\n            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"برگشت")}\n            Text(JalaliDate.archiveDay(entry.createdAt), Modifier.weight(1f), textAlign=TextAlign.Right, fontSize=25.sp, fontWeight=FontWeight.Bold)\n        }\n        Text(JalaliDate.archiveDate(entry.createdAt), Modifier.fillMaxWidth(), textAlign=TextAlign.Right, color=MaterialTheme.colorScheme.primary)\n        Text(JalaliDate.time(entry.createdAt), Modifier.fillMaxWidth().padding(top=3.dp), textAlign=TextAlign.Right, color=MaterialTheme.colorScheme.onSurfaceVariant)\n'''
assert old in s
s = s.replace(old, new)
s = s.replace('    val lock by store.lockEnabled.collectAsState(initial=false)\n', '')
old = '''                SettingsToggle(if(language==AppLanguage.FA) "قفل و اثر انگشت" else "Passcode & fingerprint", lock, Icons.Default.Fingerprint) { scope.launch { store.setLock(it) } }\n                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.35f))\n'''
new = '''                Row(Modifier.fillMaxWidth().heightIn(min=64.dp), verticalAlignment=Alignment.CenterVertically) {\n                    Icon(Icons.Default.Fingerprint, null, tint=MaterialTheme.colorScheme.primary)\n                    Spacer(Modifier.width(12.dp))\n                    Text(if(language==AppLanguage.FA) "ورود با اثر انگشت" else "Fingerprint unlock", Modifier.weight(1f), style=MaterialTheme.typography.bodyLarge)\n                    Text(if(language==AppLanguage.FA) "همیشه روشن" else "Always on", color=MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.labelMedium)\n                }\n                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.35f))\n'''
assert old in s
ui.write_text(s.replace(old, new))

manifest = root / 'app/src/main/AndroidManifest.xml'
manifest.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools">\n    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.USE_BIOMETRIC" />\n    <uses-permission android:name="android.permission.USE_FINGERPRINT" />\n    <application\n        android:name=".NameEmroozApplication"\n        android:allowBackup="false"\n        android:dataExtractionRules="@xml/data_extraction_rules"\n        android:fullBackupContent="false"\n        android:label="امروز"\n        android:icon="@mipmap/ic_launcher"\n        android:roundIcon="@mipmap/ic_launcher_round"\n        android:supportsRtl="true"\n        android:theme="@style/Theme.NameEmrooz">\n        <activity android:name=".MainActivity" android:exported="true" android:screenOrientation="portrait">\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity>\n        <provider\n            android:name="androidx.startup.InitializationProvider"\n            android:authorities="${applicationId}.androidx-startup"\n            android:exported="false"\n            tools:node="merge">\n            <meta-data\n                android:name="androidx.work.WorkManagerInitializer"\n                android:value="androidx.startup"\n                tools:node="remove" />\n        </provider>\n    </application>\n</manifest>\n''')

(root / 'app/src/main/res/values/colors.xml').write_text('<resources>\n    <color name="ic_launcher_background">#F7F2EA</color>\n</resources>\n')
(root / 'app/src/main/res/values/styles.xml').write_text('''<resources>\n    <style name="Theme.NameEmrooz" parent="android:style/Theme.Material.NoActionBar">\n        <item name="android:fontFamily">sans</item>\n        <item name="android:windowLightStatusBar">true</item>\n        <item name="android:navigationBarColor">#F7F2EA</item>\n        <item name="android:statusBarColor">#F7F2EA</item>\n        <item name="android:windowBackground">#F7F2EA</item>\n    </style>\n</resources>\n''')
adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/ic_launcher_background"/>\n    <foreground android:drawable="@drawable/app_icon_emrooz_final"/>\n</adaptive-icon>\n'''
(root / 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').write_text(adaptive)
(root / 'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml').write_text(adaptive)
