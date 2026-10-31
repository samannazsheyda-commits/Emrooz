package com.nameemrooz.journal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nameemrooz.journal.writing.DraftController
import com.nameemrooz.journal.writing.MlKitNanoPromptClient
import com.nameemrooz.journal.writing.NanoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nameemrooz.journal.data.*
import com.nameemrooz.journal.model.JournalEntry
import com.nameemrooz.journal.reminder.ReminderWorker
import com.nameemrooz.journal.speech.*
import com.nameemrooz.journal.ui.theme.NameEmroozTheme
import com.nameemrooz.journal.util.JalaliDate
import kotlinx.coroutines.launch

private val LocalButtonFeedback = staticCompositionLocalOf<() -> Unit> { {} }
private val LocalJournalInteractive = staticCompositionLocalOf { true }

private enum class Screen { HOME, LIST, ARCHIVE, DETAIL, SETTINGS }

private fun AppTheme.faLabel(): String = when (this) {
    AppTheme.NIGHT -> "حالت شب"
    AppTheme.DAY -> "حالت روز"
}

@Composable
fun NameEmroozApp(vm: AppViewModel = viewModel(), interactive: Boolean = true) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val theme by settings.theme.collectAsState(initial = AppTheme.DAY)
    val language by settings.language.collectAsState(initial = AppLanguage.FA)
    val uiScale by settings.uiScale.collectAsState(initial = UiScale.MEDIUM)
    val sound by settings.soundEnabled.collectAsState(initial=true)
    val haptics by settings.hapticsEnabled.collectAsState(initial=true)
    val view = androidx.compose.ui.platform.LocalView.current
    val buttonFeedback: () -> Unit = {
        if (sound) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        if (haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        Unit
    }
    val active by vm.active.collectAsState()
    val archived by vm.archived.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selected by remember { mutableStateOf<JournalEntry?>(null) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(screen) {
        focusManager.clearFocus(force=true)
        keyboard?.hide()
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.ARCHIVE -> Screen.LIST
            Screen.DETAIL -> if (selected?.archived == true) Screen.ARCHIVE else Screen.LIST
            else -> Screen.HOME
        }
    }
    NameEmroozTheme(theme, language, uiScale) {
        val appDirection = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides appDirection, LocalButtonFeedback provides buttonFeedback, LocalJournalInteractive provides interactive) {
            Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
                when(screen) {
                    Screen.HOME -> Home(settings, language, vm.draft, { vm.saveDraft() }, { screen=Screen.LIST }, { screen=Screen.SETTINGS }, { selected=it; screen=Screen.DETAIL })
                    Screen.LIST -> Entries(language, false, active, { screen=Screen.HOME }, { selected=it; screen=Screen.DETAIL }, { screen=Screen.ARCHIVE })
                    Screen.ARCHIVE -> Entries(language, true, archived, { screen=Screen.LIST }, { selected=it; screen=Screen.DETAIL }, null)
                    Screen.DETAIL -> Detail(language, selected, { screen=if(selected?.archived==true) Screen.ARCHIVE else Screen.LIST },
                        { entry -> vm.update(entry).also { if(it) selected=entry } },
                        { entry -> vm.delete(entry).also { if(it) screen=Screen.LIST } })
                    Screen.SETTINGS -> Settings(settings, { screen=Screen.HOME })
                }
            }
        }
    }
}

@Composable
private fun Home(store: SettingsStore, language: AppLanguage, draft: DraftController, onSave:suspend ()->Boolean, onList:()->Unit, onSettings:()->Unit, onEntry:(JournalEntry)->Unit) {
    val context = LocalContext.current
    val buttonFeedback = LocalButtonFeedback.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val text by draft.text.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var recordingToken by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var resetAction by remember { mutableStateOf<Boolean?>(null) }
    var restartWhenReady by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    val interactive = LocalJournalInteractive.current
    val speechMode by store.speechMode.collectAsState(initial = SpeechMode.PRIVATE_OFFLINE)
    val enhancedEditing by store.enhancedOnDeviceEditingEnabled.collectAsState(initial = true)
    val clearClipboard by store.clearClipboardEnabled.collectAsState(initial = true)
    val languageTag = if (language == AppLanguage.FA) "fa-IR" else "en-US"

    val engine = remember(language, speechMode, enhancedEditing) {
        HybridSpeechController(
            context = context,
            configuredMode = speechMode,
            languageTag = languageTag,
            onText = { draft.acceptSpeech(recordingToken, it) },
            onListening = { listening = it },
            onReady = { ready = it },
            onError = { error = localizeSpeechError(it, language) },
            enhancedEditingEnabled = enhancedEditing,
        )
    }
    LaunchedEffect(engine) { engine.prepare() }
    DisposableEffect(engine) { onDispose { engine.close() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    DisposableEffect(engine, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                restartWhenReady = false
                permissionRequested = false
                resetAction = null
                engine.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun startRecording() {
        editing = false
        recordingToken = draft.beginRecording()
        engine.start()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = permissionRequested
        permissionRequested = false
        if (granted && requested && interactive) restartWhenReady = true
        else if (!granted && requested) error = if(language==AppLanguage.FA) "اجازه‌ی میکروفون داده نشد" else "Microphone permission was not granted"
    }

    LaunchedEffect(ready, restartWhenReady, interactive, lifecycleState) {
        if (!interactive) {
            restartWhenReady = false
            permissionRequested = false
            resetAction = null
        } else if (ready && restartWhenReady && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            restartWhenReady = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
            else {
                permissionRequested = true
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    if (resetAction != null && interactive) {
        AlertDialog(
            onDismissRequest = { resetAction = null },
            title = { Text(if(language==AppLanguage.FA) "نوشتهٔ فعلی پاک شود؟" else "Clear the current draft?") },
            text = { Text(if(language==AppLanguage.FA) "نوشتهٔ ذخیره‌نشده پاک می‌شود. نوشته‌های ذخیره‌شده باقی می‌مانند." else "This clears the unsaved draft. Saved notes are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    val recordAgain = resetAction == true
                    resetAction = null
                    engine.discard()
                    draft.clear()
                    editing = false
                    error = null
                    restartWhenReady = recordAgain
                }) {
                    Text(if(resetAction == true) { if(language==AppLanguage.FA) "پاک کردن و ضبط دوباره" else "Clear and record again" }
                        else { if(language==AppLanguage.FA) "پاک کردن" else "Clear" })
                }
            },
            dismissButton = { TextButton(onClick = { resetAction = null }) { Text(if(language==AppLanguage.FA) "انصراف" else "Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal=20.dp, vertical=10.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            GlassIconButton({ if (!listening && !saving) onSettings() }, Icons.Default.Menu, if(language==AppLanguage.FA) "منو" else "Menu")
            Spacer(Modifier.weight(1f))
            FixedLanguageSwitch(language) { if (!listening && !saving) scope.launch { store.setLanguage(it) } }
        }

        Spacer(Modifier.height(14.dp))
        Text(JalaliDate.pretty(System.currentTimeMillis()), Modifier.fillMaxWidth(), textAlign=if(language==AppLanguage.FA) TextAlign.Right else TextAlign.Left, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(language==AppLanguage.FA) "از امروزت بگو" else "Tell me about today", Modifier.fillMaxWidth(), textAlign=if(language==AppLanguage.FA) TextAlign.Right else TextAlign.Left, style=MaterialTheme.typography.headlineLarge)
        Text(if(language==AppLanguage.FA) "تو حرف بزن، من می‌نویسم" else "You talk, I write", Modifier.fillMaxWidth(), textAlign=if(language==AppLanguage.FA) TextAlign.Right else TextAlign.Left, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        LiveTranscriptSurface(listening = listening, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(horizontal=20.dp, vertical=18.dp)) {
                    Text(if(listening) { if(language==AppLanguage.FA) "در حال نوشتن…" else "Writing live…" } else { if(language==AppLanguage.FA) "نامهٔ امروز" else "Today’s letter" }, Modifier.fillMaxWidth(), textAlign=if(language==AppLanguage.FA) TextAlign.Right else TextAlign.Left, color=MaterialTheme.colorScheme.primary, style=MaterialTheme.typography.titleLarge)
                    HorizontalDivider(Modifier.padding(vertical=10.dp), color=MaterialTheme.colorScheme.outline.copy(alpha=.42f))
                    if (editing) {
                        OutlinedTextField(value=text, onValueChange={draft.edit(it)}, modifier=Modifier.fillMaxSize(), textStyle=MaterialTheme.typography.bodyLarge, shape=RoundedCornerShape(12.dp))
                    } else {
                        Text(if(text.isBlank()) { if(language==AppLanguage.FA) "وقتی شروع به صحبت کنی، متن زنده همین‌جا نمایش داده می‌شود." else "Your live transcript will appear here when you start speaking." } else text,
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), textAlign=if(language==AppLanguage.FA) TextAlign.Right else TextAlign.Left, style=MaterialTheme.typography.bodyLarge)
                    }
                    error?.let { Spacer(Modifier.height(10.dp)); Text(it, color=MaterialTheme.colorScheme.error) }
                }
        }

        Spacer(Modifier.height(14.dp))
        FilledIconButton(
            onClick={
                buttonFeedback()
                error=null
                if(listening) engine.stop()
                else if(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startRecording()
                else {
                    permissionRequested = true
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled=ready && !saving,
            modifier=Modifier.size(78.dp), shape=CircleShape,
        ) { Icon(if(listening) Icons.Default.Stop else Icons.Default.Mic, if(listening) { if(language==AppLanguage.FA) "توقف" else "Stop" } else { if(language==AppLanguage.FA) "شروع ضبط" else "Start recording" }, Modifier.size(32.dp)) }
        Text(if(listening) { if(language==AppLanguage.FA) "برای توقف لمس کن" else "Tap to stop" } else { if(language==AppLanguage.FA) "شروع صحبت" else "Start speaking" }, style=MaterialTheme.typography.titleMedium)

        if(text.isNotBlank() && !listening && !saving) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ResultAction(if(language==AppLanguage.FA) "ذخیره" else "Save", Icons.Default.Inventory2, Modifier.weight(1f)) {
                    saving = true
                    scope.launch {
                        try {
                            if (onSave()) { editing=false; error=null }
                            else error=if(language==AppLanguage.FA) "ذخیره انجام نشد؛ نوشته حفظ شده است" else "Save failed; your draft is still here"
                        } finally { saving=false }
                    }
                }
                ResultAction(if(language==AppLanguage.FA) "ویرایش" else "Edit", Icons.Default.Edit, Modifier.weight(1f)) { editing=!editing }
                ResultAction(if(language==AppLanguage.FA) "کپی" else "Copy", Icons.Default.ContentCopy, Modifier.weight(1f)) {
                    copyJournalText(context, text, clearClipboard)
                }
            }
        }
        if (text.isBlank() && !listening && !editing) {
            TextButton(onClick={ editing=true }) {
                Text(if(language==AppLanguage.FA) "نوشتن با صفحه‌کلید" else "Write with keyboard")
            }
        }
        if ((text.isNotBlank() || listening) && !saving) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(onClick={ resetAction=false }) { Text(if(language==AppLanguage.FA) "پاک کردن" else "Clear") }
                TextButton(onClick={ resetAction=true }, enabled=ready) { Text(if(language==AppLanguage.FA) "ضبط دوباره" else "Record again") }
            }
        }
        TextButton(onClick=onList, enabled=!listening && !saving) { Text(if(language==AppLanguage.FA) "آرشیو نوشته‌ها" else "Notes archive") }
    }
}

private fun copyJournalText(context: Context, text: String, clearEnabled: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Emrooz", text)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (clearEnabled) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        runCatching {
            val current = clipboard.primaryClip
            if (current?.description?.label == "Emrooz" && current.itemCount == 1 && current.getItemAt(0).text?.toString() == text) {
                if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, 30_000L)
}

private fun localizeSpeechError(message: String, language: AppLanguage): String {
    if (language == AppLanguage.FA) return message
    return when (message) {
        "مدل فارسی آماده نشد" -> "Offline speech model is not ready"
        "چند لحظه صبر کن تا نوشتن آماده شود" -> "Please wait while speech recognition gets ready"
        "برای نوشتن با صدا اجازه‌ی میکروفون لازم است" -> "Microphone permission is required"
        "میکروفون آماده نشد" -> "Microphone is not ready"
        "تبدیل صدا به متن انجام نشد" -> "Speech-to-text failed"
        "دریافت صدا از میکروفون قطع شد" -> "Microphone input stopped"
        "سرویس گفتار گوشی در دسترس نیست" -> "Device speech service is unavailable"
        "سرویس گفتار گوشی آماده نشد" -> "Device speech service is not ready"
        "دقت بیشتر در دسترس نبود؛ آفلاین ادامه می‌دهم" -> "Higher-accuracy speech is unavailable; continuing offline"
        "اجازه‌ی میکروفون برای تبدیل صدا لازم است" -> "Microphone permission is required for speech recognition"
        else -> message
    }
}

@Composable
private fun FixedLanguageSwitch(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surface.copy(alpha=.68f), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.7f))) {
            Row(Modifier.padding(3.dp)) {
                listOf(AppLanguage.EN to "EN", AppLanguage.FA to "فا").forEach { (item,label) ->
                    Surface(onClick={onChange(item)}, shape=RoundedCornerShape(11.dp), color=if(language==item) MaterialTheme.colorScheme.primary else Color.Transparent) {
                        Text(label, Modifier.width(42.dp).padding(vertical=8.dp), textAlign=TextAlign.Center, color=if(language==item) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(onClick:()->Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, description:String) {
    val feedback = LocalButtonFeedback.current
    Surface(onClick={ feedback(); onClick() }, modifier=Modifier.size(42.dp), shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surface.copy(alpha=.68f), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.7f))) {
        Box(contentAlignment=Alignment.Center) { Icon(icon, description, tint=MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun ResultAction(label:String, icon:androidx.compose.ui.graphics.vector.ImageVector, modifier:Modifier=Modifier, onClick:()->Unit) {
    val feedback = LocalButtonFeedback.current
    Surface(onClick={ feedback(); onClick() }, modifier=modifier.height(54.dp), shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surface.copy(alpha=.82f), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.7f))) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint=MaterialTheme.colorScheme.primary)
            Text(label, style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LiveTranscriptSurface(
    listening: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val transition = rememberInfiniteTransition(label = "live-transcript-glow")
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.64f,
        animationSpec = infiniteRepeatable(
            animation = tween(1450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-transcript-glow-alpha",
    )
    val accent = MaterialTheme.colorScheme.primary
    val border = if (listening) accent.copy(alpha = 0.42f + glow * 0.28f) else MaterialTheme.colorScheme.outline.copy(alpha = .62f)
    val shadowModifier = if (listening) {
        Modifier.shadow(
            elevation = (3f + glow * 7f).dp,
            shape = shape,
            ambientColor = accent.copy(alpha = .14f + glow * .10f),
            spotColor = accent.copy(alpha = .16f + glow * .12f),

        )
    } else Modifier

    Surface(
        modifier = modifier.then(shadowModifier),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (listening) .88f else .74f),
        shape = shape,
        border = BorderStroke(if (listening) 1.25.dp else 1.dp, border),
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (listening) {
                Box(
                    Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter)
                        .alpha(.20f + glow * .65f)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, accent.copy(alpha=.35f), Color.White.copy(alpha=.85f), accent.copy(alpha=.35f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun Entries(
    language: AppLanguage,
    archivedScreen: Boolean,
    entries: List<JournalEntry>,
    onBack: () -> Unit,
    onEntry: (JournalEntry) -> Unit,
    onArchive: (() -> Unit)?,
) {
    val title = if (language == AppLanguage.FA) {
        if (archivedScreen) "آرشیو" else "نوشته‌های قبلی"
    } else {
        if (archivedScreen) "Archive" else "Previous notes"
    }
    val align = if (language == AppLanguage.FA) TextAlign.Right else TextAlign.Left
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, if(language==AppLanguage.FA) "برگشت" else "Back")
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), textAlign = align, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(entries, key = { it.id }) { e ->
                Surface(
                    onClick = { onEntry(e) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha=.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.55f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = if(language==AppLanguage.FA) Alignment.End else Alignment.Start) {
                            Text(JalaliDate.weekday(e.createdAt), style = MaterialTheme.typography.titleMedium)
                            Text(JalaliDate.date(e.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(JalaliDate.time(e.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (onArchive != null) item {
                TextButton(onClick = onArchive, Modifier.fillMaxWidth()) { Text(if(language==AppLanguage.FA) "آرشیو" else "Archive") }
            }
        }
    }
}

@Composable
private fun Detail(
    language: AppLanguage,
    entry: JournalEntry?,
    onBack: () -> Unit,
    onSave: suspend (JournalEntry) -> Boolean,
    onDelete: suspend (JournalEntry) -> Boolean,
) {
    if (entry == null) { LaunchedEffect(Unit) { onBack() }; return }
    var text by remember(entry.id) { mutableStateOf(entry.text) }
    var confirmDelete by remember(entry.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun saveEntry(value: JournalEntry) {
        if (saving) return
        saving=true
        scope.launch {
            try {
                feedback = if (onSave(value)) {
                    if(language==AppLanguage.FA) "تغییرات ذخیره شد" else "Changes saved"
                } else if(language==AppLanguage.FA) "ذخیره انجام نشد؛ متن را نگه داشته‌ایم" else "Save failed; your text is still here"
            } finally { saving=false }
        }
    }
    val align = if (language == AppLanguage.FA) TextAlign.Right else TextAlign.Left
    if (confirmDelete && LocalJournalInteractive.current) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if(language==AppLanguage.FA) "این نوشته حذف شود؟" else "Delete this note?") },
            text = { Text(if(language==AppLanguage.FA) "بعد از حذف، نوشته قابل برگرداندن نیست." else "This cannot be undone.") },
            confirmButton = { TextButton(onClick = {
                confirmDelete=false
                saving=true
                scope.launch {
                    try {
                        if (!onDelete(entry)) feedback=if(language==AppLanguage.FA) "حذف انجام نشد" else "Delete failed"
                    } finally { saving=false }
                }
            }) { Text(if(language==AppLanguage.FA) "حذف" else "Delete", color=MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete=false }) { Text(if(language==AppLanguage.FA) "نه" else "Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp, vertical=14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, if(language==AppLanguage.FA) "برگشت" else "Back")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = if(language==AppLanguage.FA) Alignment.End else Alignment.Start) {
                Text(JalaliDate.weekday(entry.createdAt), textAlign=align, style=MaterialTheme.typography.titleLarge)
                Text("${JalaliDate.date(entry.createdAt)}  •  ${JalaliDate.time(entry.createdAt)}", textAlign=align, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value=text,
            onValueChange={text=it},
            modifier=Modifier.fillMaxWidth().weight(1f),
            textStyle=MaterialTheme.typography.bodyLarge.copy(textAlign=align),
            shape=RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick={saveEntry(entry.copy(text=text))}, modifier=Modifier.fillMaxWidth().height(52.dp), enabled=!saving && text.isNotBlank(), shape=RoundedCornerShape(12.dp)) {

            Text(if(language==AppLanguage.FA) "ذخیره تغییرات" else "Save changes")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={saveEntry(entry.copy(text=text, archived=!entry.archived))}, enabled=!saving && text.isNotBlank()) { Text(if(language==AppLanguage.FA) { if(entry.archived) "برگرداندن" else "انتقال به آرشیو" } else { if(entry.archived) "Restore" else "Move to archive" }) }
            TextButton(onClick={confirmDelete=true}, enabled=!saving) { Text(if(language==AppLanguage.FA) "حذف" else "Delete", color=MaterialTheme.colorScheme.error) }
        }
        feedback?.let { Text(it, style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Settings(store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ReminderWorker.schedule(context)
    }
    val theme by store.theme.collectAsState(initial=AppTheme.DAY)
    val language by store.language.collectAsState(initial=AppLanguage.FA)
    val uiScale by store.uiScale.collectAsState(initial=UiScale.MEDIUM)
    val speechMode by store.speechMode.collectAsState(initial=SpeechMode.PRIVATE_OFFLINE)
    val disclosureAccepted by store.highAccuracyDisclosureAccepted.collectAsState(initial=false)
    val enhancedEditing by store.enhancedOnDeviceEditingEnabled.collectAsState(initial=true)
    var nanoStatus by remember { mutableStateOf<NanoStatus?>(null) }
    var nanoCheckFailed by remember { mutableStateOf(false) }
    LaunchedEffect(enhancedEditing) {
        nanoStatus = null
        nanoCheckFailed = false
        if (enhancedEditing) {
            try {
                withContext(Dispatchers.IO) {
                    MlKitNanoPromptClient().use { client ->
                        do {
                            val status = withTimeoutOrNull(2500) { client.status() }
                            withContext(Dispatchers.Main) {
                                nanoStatus = status
                                nanoCheckFailed = status == null
                            }
                            if (status == null || status == NanoStatus.AVAILABLE || status == NanoStatus.UNAVAILABLE) break
                            delay(3000)
                        } while (true)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { nanoCheckFailed = true }
            catch (_: LinkageError) { nanoCheckFailed = true }
        }
    }
    val systemSpeechAvailable = remember { SystemSpeechEngine.isAvailable(context) }
    val systemOnDeviceAvailable = remember { SystemSpeechEngine.isOnDeviceAvailable(context) }
    var showSpeechDisclosure by remember { mutableStateOf(false) }
    val lock by store.lockEnabled.collectAsState(initial=true)
    val reminder by store.reminderEnabled.collectAsState(initial=true)
    val sound by store.soundEnabled.collectAsState(initial=true)
    val haptics by store.hapticsEnabled.collectAsState(initial=true)
    val clearClipboard by store.clearClipboardEnabled.collectAsState(initial=true)
    val align = if(language==AppLanguage.FA) TextAlign.Right else TextAlign.Left

    if (showSpeechDisclosure && LocalJournalInteractive.current) {
        AlertDialog(
            onDismissRequest = { showSpeechDisclosure = false },
            title = { Text(if(language==AppLanguage.FA) "دقت بیشتر" else "Higher accuracy") },
            text = {
                Text(
                    if(language==AppLanguage.FA) "سرویس گفتار گوشی ممکن است بسته به تنظیمات دستگاه، صدا را خارج از «امروز» پردازش کند و از اینترنت استفاده کند. خودِ امروز هیچ فایل صوتی ذخیره نمی‌کند."
                    else "Your device speech service may process speech outside Emrooz and may use the internet depending on device settings. Emrooz itself never saves an audio file."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSpeechDisclosure = false
                    scope.launch {
                        store.setHighAccuracyDisclosureAccepted(true)
                        store.setSpeechMode(SpeechMode.SYSTEM_HIGH_ACCURACY)
                    }
                }) { Text(if(language==AppLanguage.FA) "تأیید" else "Continue") }
            },
            dismissButton = { TextButton(onClick = { showSpeechDisclosure = false }) { Text(if(language==AppLanguage.FA) "لغو" else "Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal=18.dp, vertical=14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, if(language==AppLanguage.FA) "برگشت" else "Back")
            Spacer(Modifier.weight(1f))
            FixedLanguageSwitch(language) { scope.launch { store.setLanguage(it) } }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(18.dp))
        Text(if(language==AppLanguage.FA) "تنظیمات" else "Settings", Modifier.fillMaxWidth(), textAlign=align, style=MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))

        SettingsCard {
            SettingsToggle(if(language==AppLanguage.FA) "حالت شب" else "Dark mode", theme==AppTheme.NIGHT, Icons.Default.DarkMode) { enabled -> scope.launch { store.setTheme(if(enabled) AppTheme.NIGHT else AppTheme.DAY) } }
            DividerSoft()
            SettingsToggle(if(language==AppLanguage.FA) "صدای تأیید" else "Confirmation sounds", sound, Icons.AutoMirrored.Filled.VolumeUp) { scope.launch { store.setSound(it) } }
            DividerSoft()
            SettingsToggle(if(language==AppLanguage.FA) "لرزش دکمه‌ها" else "Button haptics", haptics, Icons.Default.Vibration) { scope.launch { store.setHaptics(it) } }
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            SettingsToggle(if(language==AppLanguage.FA) "قفل و اثر انگشت" else "Passcode & fingerprint", lock, Icons.Default.Fingerprint) { scope.launch { store.setLock(it) } }
            DividerSoft()
            SettingsToggle(if(language==AppLanguage.FA) "پاک‌کردن خودکار کلیپ‌بورد" else "Clear clipboard automatically", clearClipboard, Icons.Default.ContentPasteOff) { scope.launch { store.setClearClipboard(it) } }
            DividerSoft()
            SettingsToggle(if(language==AppLanguage.FA) "یادآوری ساعت ۸ شب" else "8 PM reminder", reminder, Icons.Default.Notifications) { value ->
                scope.launch { store.setReminder(value) }
                if (value) {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ReminderWorker.schedule(context)
                } else ReminderWorker.cancel(context)
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(if(language==AppLanguage.FA) "اندازه رابط کاربری" else "UI size", Modifier.fillMaxWidth(), textAlign=align, style=MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            UiScaleSelector(language, uiScale) { scope.launch { store.setUiScale(it) } }
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(if(language==AppLanguage.FA) "موتور تبدیل صدا به متن" else "Speech-to-text engine", Modifier.fillMaxWidth(), textAlign=align, style=MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(7.dp))
            SpeechModeOption(
                selected = speechMode == SpeechMode.PRIVATE_OFFLINE,
                enabled = true,
                title = if(language==AppLanguage.FA) "خصوصی و آفلاین — Shenava" else "Private & offline — Shenava",
                subtitle = if(language==AppLanguage.FA) "بدون اینترنت؛ صدا از گوشی خارج نمی‌شود" else "No internet; speech stays on this device",
                onClick = { scope.launch { store.setSpeechMode(SpeechMode.PRIVATE_OFFLINE) } },
            )
            DividerSoft()
            SpeechModeOption(
                selected = speechMode == SpeechMode.SYSTEM_HIGH_ACCURACY,
                enabled = systemSpeechAvailable,
                title = if(language==AppLanguage.FA) "دقت بیشتر — سرویس گفتار گوشی" else "Higher accuracy — device speech service",
                subtitle = if (!systemSpeechAvailable) {
                    if(language==AppLanguage.FA) "روی این گوشی در دسترس نیست" else "Not available on this device"
                } else if (systemOnDeviceAvailable) {
                    if(language==AppLanguage.FA) "این گوشی سرویس تشخیص گفتار روی دستگاه هم دارد" else "This device also has on-device recognition"
                } else {
                    if(language==AppLanguage.FA) "ممکن است برای تشخیص گفتار از اینترنت استفاده کند" else "May use internet for recognition"
                },
                onClick = {
                    if (disclosureAccepted) scope.launch { store.setSpeechMode(SpeechMode.SYSTEM_HIGH_ACCURACY) }
                    else showSpeechDisclosure = true
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            SettingsToggle(
                if(language==AppLanguage.FA) "ویراستاری با جمینای نانو" else "Gemini Nano editing",
                enhancedEditing,
                Icons.Default.AutoFixHigh,
            ) { enabled -> scope.launch { store.setEnhancedOnDeviceEditingEnabled(enabled) } }
            Text(
                if(language==AppLanguage.FA) "اگر مدل گوگل روی گوشی پشتیبانی شود، املا و نیم‌فاصله با حفظ معنی بهتر می‌شود. در غیر این صورت ویراستار آفلاین امروز استفاده می‌شود."
                else "Uses the device's supported Google on-device model when available; otherwise Emrooz keeps its local editor.",
                Modifier.fillMaxWidth().padding(start=31.dp, end=8.dp, bottom=9.dp),
                textAlign=align,
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enhancedEditing) {
            val statusText = when {
                nanoCheckFailed -> if(language==AppLanguage.FA) "مدل گوگل فعلاً در دسترس نیست؛ ویراستار محلی فعال است" else "Google model unavailable; local editing is active"
                nanoStatus == NanoStatus.AVAILABLE -> if(language==AppLanguage.FA) "مدل گوگل آماده است" else "Google model is ready"
                nanoStatus == NanoStatus.DOWNLOADING -> if(language==AppLanguage.FA) "مدل در حال دانلود است؛ ویراستار محلی فعال است" else "Model downloading; local editing is active"
                nanoStatus == NanoStatus.DOWNLOADABLE -> if(language==AppLanguage.FA) "مدل به دانلود نیاز دارد؛ با اولین نوشته آماده‌سازی آغاز می‌شود" else "Model download needed; provisioning starts with your first transcript"
                nanoStatus == NanoStatus.UNAVAILABLE -> if(language==AppLanguage.FA) "مدل گوگل فعلاً روی این گوشی در دسترس نیست؛ ویراستار محلی فعال است" else "Google model unavailable on this device; local editing is active"
                else -> if(language==AppLanguage.FA) "در حال بررسی مدل گوگل…" else "Checking Google model…"
            }
            Text(statusText, Modifier.fillMaxWidth().padding(vertical=8.dp), textAlign=align, style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        Text(if(language==AppLanguage.FA) "حریم خصوصی" else "Privacy", Modifier.fillMaxWidth(), textAlign=align, style=MaterialTheme.typography.titleLarge)
        Text(
            if (speechMode == SpeechMode.PRIVATE_OFFLINE) {
                if(language==AppLanguage.FA) "صدا ذخیره یا آپلود نمی‌شود. تبدیل صدا به متن و ویراستاری روی همین گوشی انجام می‌شود."
                else "Audio is never saved or uploaded. Speech-to-text and editing run on this device."
            } else {
                if(language==AppLanguage.FA) "امروز صدا را ذخیره نمی‌کند. سرویس گفتار گوشی ممکن است بسته به تنظیمات دستگاه برای تشخیص گفتار از اینترنت استفاده کند؛ ویراستاری داخل اپ محلی است."
                else "Emrooz never saves audio. Your device speech service may use the internet depending on device settings; editing inside Emrooz remains local."
            },
            Modifier.fillMaxWidth(), textAlign=align, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surface.copy(alpha=.76f),
        border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.55f)),
    ) { Column(Modifier.padding(horizontal=15.dp, vertical=8.dp), content=content) }
}

@Composable private fun DividerSoft() = HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.28f))

@Composable
private fun UiScaleSelector(language: AppLanguage, uiScale: UiScale, onChange: (UiScale) -> Unit) {
    val labels = if(language==AppLanguage.FA) listOf("کوچک", "معمولی", "بزرگ") else listOf("Small", "Medium", "Large")
    val scales = listOf(UiScale.SMALL, UiScale.MEDIUM, UiScale.LARGE)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            scales.zip(labels).forEach { (scale,label) ->
                Surface(
                    onClick={onChange(scale)},
                    modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(11.dp),
                    color=if(uiScale==scale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),
                    border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=.45f)),
                ) {
                    Text(label, Modifier.padding(vertical=9.dp), textAlign=TextAlign.Center, color=if(uiScale==scale) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SpeechModeOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled=enabled, onClick=onClick).padding(vertical=9.dp),
        verticalAlignment=Alignment.CenterVertically,
    ) {
        RadioButton(selected=selected, onClick=if(enabled) onClick else null, enabled=enabled)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style=MaterialTheme.typography.bodyMedium, color=if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.55f))
            Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled) 1f else .55f))
        }
    }
}

@Composable
private fun SettingsToggle(label:String, checked:Boolean, icon:androidx.compose.ui.graphics.vector.ImageVector, onChange:(Boolean)->Unit) {
    val feedback = LocalButtonFeedback.current
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable{feedback(); onChange(!checked)}, verticalAlignment=Alignment.CenterVertically) {
        Icon(icon, null, tint=MaterialTheme.colorScheme.primary, modifier=Modifier.size(20.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, Modifier.weight(1f), style=MaterialTheme.typography.bodyMedium)
        Switch(checked=checked, onCheckedChange={ feedback(); onChange(it) })
    }
}
