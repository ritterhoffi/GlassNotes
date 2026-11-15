package com.kirill.glassnotes

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.kirill.glassnotes.ui.theme.ClaretAccent
import com.kirill.glassnotes.ui.theme.GlassNotesTheme
import com.kirill.glassnotes.ui.theme.LocalGlassColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        val initialBlurRadius = AppStorage(this).loadSettings().blurRadius
        window.attributes = window.attributes.apply { blurBehindRadius = initialBlurRadius }
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            val state by viewModel.state.collectAsState()
            val isGlassEnabled = state.settings.isGlassEnabled
            val blurRadius = state.settings.blurRadius
            LaunchedEffect(isGlassEnabled, blurRadius) {
                window.attributes = window.attributes.apply { blurBehindRadius = if (isGlassEnabled) 90 else 0 }
                if (isGlassEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        window.setBackgroundBlurRadius(90)
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        window.setBackgroundBlurRadius(0)
                    }
                }
            }

            GlassNotesTheme(
                isDarkGlass = state.settings.isDarkGlass,
                glassAlpha = state.settings.glassAlpha,
                backgroundAlpha = state.settings.backgroundAlpha,
                isGlassEnabled = state.settings.isGlassEnabled
            ) {
                GlassNotesApp(viewModel)
            }
        }
    }
}

sealed interface EditorTarget {
    data class New(val folderId: String?) : EditorTarget
    data class Edit(val noteId: String) : EditorTarget
}

@Composable
private fun GlassNotesApp(viewModel: MainViewModel) {
    val colors = LocalGlassColors.current
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf("notes") }
    var editorState by rememberSaveable { mutableStateOf<String?>(null) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    val notesScrollState = rememberScrollState()
    val cardHeights = remember { mutableStateMapOf<String, Int>() }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.buildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Копия сохранена", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось сохранить копию", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val rawEditor = editorState
    val editorTarget: EditorTarget? = when {
        rawEditor == null -> null
        rawEditor.startsWith("edit:") -> EditorTarget.Edit(rawEditor.removePrefix("edit:"))
        rawEditor.startsWith("new:") -> EditorTarget.New(rawEditor.removePrefix("new:").ifEmpty { null })
        else -> null
    }

    var lastActiveEditorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    if (editorTarget != null) {
        lastActiveEditorTarget = editorTarget
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val target = editorTarget ?: lastActiveEditorTarget
        val overlayVisible = target != null || showTrash || showFolders

        // Основной слой остаётся в композиции, пока поверх открыт редактор,
        // чтобы NotesScreen сохранял позицию прокрутки.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (overlayVisible) 0f else 1f }
        ) {
            Crossfade(targetState = currentTab, label = "tab") { tab ->
                when (tab) {
                    "tasks" -> TasksScreen(
                        viewModel = viewModel,
                        onOpenTrash = { showTrash = true },
                        onSaveBackup = { backupLauncher.launch("glass_notes_backup.json") },
                        onOpenSettings = { showSettings = true }
                    )
                    else -> NotesScreen(
                        viewModel = viewModel,
                        scrollState = notesScrollState,
                        cardHeights = cardHeights,
                        onOpenNote = { note -> editorState = "edit:${note.id}" },
                        onOpenTrash = { showTrash = true },
                        onSaveBackup = { backupLauncher.launch("glass_notes_backup.json") },
                        onOpenSettings = { showSettings = true },
                        onOpenFolders = { showFolders = true }
                    )
                }
            }
            GlassBottomNav(
                modifier = Modifier.align(Alignment.BottomCenter),
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                onAddClick = {
                    if (currentTab == "tasks") {
                        showTaskDialog = true
                    } else {
                        editorState = "new:${viewModel.state.value.selectedFolderId.orEmpty()}"
                    }
                }
            )
        }

        // Прозрачный экран-заглушка: пока открыт полноэкранный слой, он
        // перехватывает касания, чтобы скрытый основной слой не реагировал на ввод.
        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }

        if (target != null) {
            NoteEditorScreen(
                viewModel = viewModel,
                target = target,
                onClose = {
                    viewModel.setSearchQuery("")
                    editorState = null
                    lastActiveEditorTarget = null
                }
            )
        }

        if (showTrash) {
            TrashScreen(
                viewModel = viewModel,
                onBack = { showTrash = false }
            )
        }

        if (showFolders) {
            FoldersScreen(
                viewModel = viewModel,
                onBack = { showFolders = false }
            )
        }

        if (showSettings) {
            GlassSettingsSheet(
                viewModel = viewModel,
                onDismiss = { showSettings = false }
            )
        }

        if (showTaskDialog) {
            NewTaskDialog(
                viewModel = viewModel,
                onDismiss = { showTaskDialog = false }
            )
        }
    }
}

@Composable
private fun GlassBottomNav(
    modifier: Modifier = Modifier,
    currentTab: String,
    onTabChange: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTabItem(
                iconRes = R.drawable.ic_description,
                label = "Заметки",
                selected = currentTab == "notes",
                onClick = { onTabChange("notes") }
            )
            GlassTabItem(
                iconRes = R.drawable.ic_date_range,
                label = "Задачи",
                selected = currentTab == "tasks",
                onClick = { onTabChange("tasks") }
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(ClaretAccent)
                .springClickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Добавить",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun GlassTabItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.innerBlock else Color.Transparent)
            .springClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (selected) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}


