package com.kirill.glassnotes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kirill.glassnotes.ui.theme.ClaretAccent
import com.kirill.glassnotes.ui.theme.LocalGlassColors

/** Меню «три точки»: кнопка и выпадающее меню внутри одного контейнера. */
@Composable
fun GlassOverflowMenu(
    onOpenTrash: () -> Unit,
    onSaveBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val colors = LocalGlassColors.current
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) { onExpandedChange(expanded) }

    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.cardBorder, CircleShape)
                .springClickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "Меню",
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(230.dp),
            offset = DpOffset(0.dp, 10.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = colors.card,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 8.dp
        ) {
            DropdownMenuItem(
                text = { Text("Корзина") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                onClick = { expanded = false; onOpenTrash() }
            )
            DropdownMenuItem(
                text = { Text("Сохранить копию") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                onClick = { expanded = false; onSaveBackup() }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                color = colors.divider,
                thickness = 1.dp
            )
            DropdownMenuItem(
                text = { Text("Внешний вид / Стекло") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                onClick = { expanded = false; onOpenSettings() }
            )
        }
    }
}

/** Экран корзины со списком удалённых заметок. */
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalGlassColors.current
    val deletedNotes = state.notes.filter { it.isDeleted }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Назад",
                    tint = colors.textPrimary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Корзина",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (deletedNotes.isNotEmpty()) {
                    Text(
                        text = pluralize(deletedNotes.size, "заметка", "заметки", "заметок"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
            if (deletedNotes.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearTrash() }) {
                    Text("Очистить корзину", color = colors.textSecondary)
                }
            }
        }

        if (deletedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.cardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Корзина пуста",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(deletedNotes, key = { it.id }) { note ->
                    TrashNoteCard(
                        note = note,
                        onRestore = { viewModel.restoreNote(note.id) },
                        onDeleteForever = { viewModel.deleteNoteForever(note.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashNoteCard(
    note: NoteItem,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val colors = LocalGlassColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        if (note.title.isNotBlank()) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
        }
        if (note.content.isNotBlank()) {
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            TextButton(onClick = onRestore) { Text("Восстановить", color = colors.textPrimary) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDeleteForever) { Text("Удалить навсегда", color = colors.textSecondary) }
        }
    }
}

/** Нижняя шторка «Внешний вид» с тумблером прозрачности стекла. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSettingsSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings
    val colors = LocalGlassColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        contentColor = colors.textPrimary,
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "Внешний вид",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(colors.innerBlock)
                    .padding(4.dp)
            ) {
                ThemeOptionChip(
                    label = "Тёмная",
                    selected = settings.isDarkGlass,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setDarkGlass(true) }
                )
                ThemeOptionChip(
                    label = "Светлая",
                    selected = !settings.isDarkGlass,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setDarkGlass(false) }
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.innerBlock)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Прозрачность стекла",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (settings.isGlassEnabled) "Размытие и полупрозрачные слои" else "Сплошной непрозрачный фон",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = settings.isGlassEnabled,
                    onCheckedChange = { viewModel.setGlassEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ClaretAccent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.cardBorder
                    )
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalGlassColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}



