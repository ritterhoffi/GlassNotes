package com.kirill.glassnotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirill.glassnotes.ui.theme.LocalGlassColors

@Composable
fun NotesScreen(
    viewModel: MainViewModel,
    onOpenNote: (NoteItem) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalGlassColors.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }

    val activeNotes = state.notes.filter { !it.isDeleted }
    val folderFiltered = if (state.selectedFolderId == null) {
        activeNotes
    } else {
        activeNotes.filter { it.folderId == state.selectedFolderId }
    }
    val query = state.searchQuery.trim()
    val visibleNotes = if (query.isEmpty()) {
        folderFiltered
    } else {
        folderFiltered.filter { note ->
            note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true) ||
                note.checklist.any { it.text.contains(query, ignoreCase = true) }
        }
    }
    val pinnedNotes = visibleNotes.filter { it.isPinned }
    val regularNotes = visibleNotes.filter { !it.isPinned }

    Column(Modifier.fillMaxSize()) {
        if (searchOpen) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                onClose = {
                    viewModel.setSearchQuery("")
                    searchOpen = false
                }
            )
        } else {
            NotesHeader(
                count = activeNotes.size,
                onSearchClick = { searchOpen = true },
                onMenuClick = { menuExpanded = true }
            )
            FolderRibbon(
                folders = state.folders,
                selectedFolderId = state.selectedFolderId,
                onSelectFolder = viewModel::setSelectedFolderId,
                onAddFolder = { showAddFolderDialog = true },
                onDeleteFolder = { folder -> viewModel.deleteFolder(folder.id) }
            )
        }

        if (visibleNotes.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pinnedNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onOpenNote(note) },
                        onTogglePin = { viewModel.toggleNotePin(note.id) },
                        onToggleChecklist = { itemId -> viewModel.toggleChecklistItem(note.id, itemId) }
                    )
                }
                if (pinnedNotes.isNotEmpty() && regularNotes.isNotEmpty()) {
                    item(key = "gap") { Spacer(Modifier.height(4.dp)) }
                }
                items(regularNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onOpenNote(note) },
                        onTogglePin = { viewModel.toggleNotePin(note.id) },
                        onToggleChecklist = { itemId -> viewModel.toggleChecklistItem(note.id, itemId) }
                    )
                }
            }
        }
    }

    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(text = { Text("Корзина") }, onClick = { menuExpanded = false }, enabled = false)
        DropdownMenuItem(text = { Text("Настройка стекла") }, onClick = { menuExpanded = false }, enabled = false)
    }

    if (showAddFolderDialog) {
        AddFolderDialog(
            onConfirm = { name -> viewModel.addFolder(name) },
            onDismiss = { showAddFolderDialog = false }
        )
    }
}

@Composable
private fun NotesHeader(
    count: Int,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Заметки",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
        Row {
            CircleIconButton(R.drawable.ic_search, "Поиск", onClick = onSearchClick)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(R.drawable.ic_more_vert, "Меню", onClick = onMenuClick)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalGlassColors.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Поиск…", color = colors.textSecondary, fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Очистить",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(R.drawable.ic_arrow_back, "Назад", onClick = onClose)
    }
}

@Composable
private fun CircleIconButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit
) {
    val colors = LocalGlassColors.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = colors.textPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FolderRibbon(
    folders: List<FolderItem>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onAddFolder: () -> Unit,
    onDeleteFolder: (FolderItem) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FolderChip(
                label = "All",
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) }
            )
        }
        items(folders, key = { it.id }) { folder ->
            FolderChip(
                label = folder.name,
                selected = selectedFolderId == folder.id,
                onClick = { onSelectFolder(folder.id) },
                onLongClick = { onDeleteFolder(folder) }
            )
        }
        item(key = "add") {
            AddFolderChip(onClick = onAddFolder)
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.innerBlock else colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
            .pressable(onClick, onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun AddFolderChip(onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = "Новая папка",
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.pointerInput(onClick, onLongClick) {
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = { onLongClick?.invoke() }
        )
    }

@Composable
private fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleChecklist: (String) -> Unit
) {
    val colors = LocalGlassColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = "Закрепить",
                tint = if (note.isPinned) colors.textPrimary else colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onTogglePin)
            )
        }

        if (note.content.isNotBlank() || note.checklist.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.innerBlock)
                    .padding(12.dp)
            ) {
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (note.checklist.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    note.checklist.forEach { item ->
                        ChecklistRow(item = item, onClick = { onToggleChecklist(item.id) })
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = formatTimestamp(note.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (item.isChecked) colors.textPrimary else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (item.isChecked) colors.textPrimary else colors.textSecondary,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (item.isChecked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onTextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.isChecked) colors.textSecondary else colors.textPrimary,
            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_description),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Нет заметок",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun AddFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalGlassColors.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая папка", color = colors.textPrimary) },
        text = {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) {
                            Text("Название папки", color = colors.textSecondary, fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name); onDismiss() }) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}





