package com.kirill.glassnotes

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.kirill.glassnotes.ui.theme.GlassNotesTheme
import com.kirill.glassnotes.ui.theme.LocalGlassColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            val state by viewModel.state.collectAsState()
            val blurRadius = state.settings.blurRadius
            LaunchedEffect(blurRadius) {
                window.attributes = window.attributes.apply { blurBehindRadius = blurRadius }
            }

            GlassNotesTheme(
                isDarkGlass = state.settings.isDarkGlass,
                glassAlpha = state.settings.glassAlpha
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
    var currentTab by rememberSaveable { mutableStateOf("notes") }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val target = editorTarget
        if (target != null) {
            NoteEditorScreen(
                viewModel = viewModel,
                target = target,
                onClose = { editorTarget = null }
            )
        } else {
            when (currentTab) {
                "tasks" -> TasksScreen(viewModel)
                else -> NotesScreen(
                    viewModel = viewModel,
                    onOpenNote = { note -> editorTarget = EditorTarget.Edit(note.id) }
                )
            }
            GlassBottomNav(
                modifier = Modifier.align(Alignment.BottomCenter),
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                onAddClick = {
                    editorTarget = EditorTarget.New(viewModel.state.value.selectedFolderId)
                }
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
                .background(colors.card)
                .border(1.dp, colors.cardBorder, CircleShape)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Новая заметка",
                tint = colors.textPrimary,
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
            .clickable(onClick = onClick)
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


