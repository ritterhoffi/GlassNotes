package com.kirill.glassnotes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirill.glassnotes.ui.theme.ClaretAccent
import com.kirill.glassnotes.ui.theme.ClaretLight
import com.kirill.glassnotes.ui.theme.ClaretPinnedBorder
import com.kirill.glassnotes.ui.theme.ClaretPinnedTint
import com.kirill.glassnotes.ui.theme.LocalGlassColors

@Composable
fun NotesScreen(
    viewModel: MainViewModel,
    scrollState: ScrollState,
    onOpenNote: (NoteItem) -> Unit,
    onOpenTrash: () -> Unit,
    onSaveBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolders: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalGlassColors.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val haptic = LocalHapticFeedback.current

    val activeNotes = state.notes.filter { !it.isDeleted }.sortedBy { it.order }
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

    Box(Modifier.fillMaxSize()) {
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
                    selectedCount = selectedNoteIds.size,
                    onClearSelection = { selectedNoteIds = emptySet() },
                    onTrashSelected = {
                        viewModel.moveNotesToTrash(selectedNoteIds)
                        selectedNoteIds = emptySet()
                    },
                    onSearchClick = { searchOpen = true },
                    onOpenTrash = onOpenTrash,
                    onSaveBackup = onSaveBackup,
                    onOpenSettings = onOpenSettings,
                    onOpenFolders = onOpenFolders,
                    onMenuExpandedChange = { menuOpen = it }
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .then(
                            if (state.settings.isGlassEnabled) {
                                Modifier
                                    .graphicsLayer { alpha = 0.99f }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.04f to Color.Black,
                                                1.0f to Color.Black
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                    if (pinnedNotes.isNotEmpty()) {
                        SectionHeader("Закреплённые")
                        Spacer(Modifier.height(6.dp))
                        NotesGrid(notes = pinnedNotes) { note ->
                            NoteCard(
                                note = note,
                                isDarkTheme = state.settings.isDarkGlass,
                                isSelected = note.id in selectedNoteIds,
                                selectionMode = selectedNoteIds.isNotEmpty(),
                                onClick = {
                                    if (selectedNoteIds.isNotEmpty()) {
                                        selectedNoteIds = if (note.id in selectedNoteIds) selectedNoteIds - note.id else selectedNoteIds + note.id
                                    } else {
                                        onOpenNote(note)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedNoteIds = selectedNoteIds + note.id
                                },
                                onTogglePin = { viewModel.toggleNotePin(note.id) }
                            )
                        }
                    }
                    if (regularNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                        }
                        SectionHeader("Другие")
                        Spacer(Modifier.height(6.dp))
                        NotesGrid(notes = regularNotes) { note ->
                            NoteCard(
                                note = note,
                                isDarkTheme = state.settings.isDarkGlass,
                                isSelected = note.id in selectedNoteIds,
                                selectionMode = selectedNoteIds.isNotEmpty(),
                                onClick = {
                                    if (selectedNoteIds.isNotEmpty()) {
                                        selectedNoteIds = if (note.id in selectedNoteIds) selectedNoteIds - note.id else selectedNoteIds + note.id
                                    } else {
                                        onOpenNote(note)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedNoteIds = selectedNoteIds + note.id
                                },
                                onTogglePin = { viewModel.toggleNotePin(note.id) }
                            )
                        }
                    }
                    // Отступ под плавающую нижнюю навигацию.
                    Spacer(Modifier.height(92.dp))
                    }

                    if (!state.settings.isGlassEnabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to colors.background,
                                        1.0f to Color.Transparent
                                    )
                                )
                        )
                    }
                }
            }
        }

        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }
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
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onTrashSelected: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenTrash: () -> Unit,
    onSaveBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolders: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    val colors = LocalGlassColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        AnimatedContent(
            targetState = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 })
                        .togetherWith(fadeOut(tween(150)))
                } else {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 })
                        .togetherWith(fadeOut(tween(150)))
                }
            },
            label = "notesHeader"
        ) { selectionActive ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionActive) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Выбрано: $selectedCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Отменить выделение",
                            tint = colors.textPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(onClick = onClearSelection)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ClaretAccent)
                            .springClickable(onClick = onTrashSelected)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Корзина",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Заметки",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (count > 0) {
                            Text(
                                text = pluralize(count, "заметка", "заметки", "заметок"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(R.drawable.ic_search, "Поиск", onClick = onSearchClick)
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(R.drawable.ic_folder, "Папки", onClick = onOpenFolders)
                        Spacer(Modifier.width(8.dp))
                        GlassOverflowMenu(
                            onOpenTrash = onOpenTrash,
                            onSaveBackup = onSaveBackup,
                            onOpenSettings = onOpenSettings,
                            onExpandedChange = onMenuExpandedChange
                        )
                    }
                }
            }
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
            .springClickable(onClick = onClick),
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
            .background(if (selected) ClaretAccent else colors.card)
            .border(1.dp, if (selected) ClaretAccent else colors.cardBorder, RoundedCornerShape(18.dp))
            .pressable(onClick, onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.textSecondary,
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
            .springClickable(onClick = onClick),
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalGlassColors.current.textSecondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun NotesGrid(
    notes: List<NoteItem>,
    content: @Composable (NoteItem) -> Unit
) {
    val left = notes.filterIndexed { index, _ -> index % 2 == 0 }
    val right = notes.filterIndexed { index, _ -> index % 2 == 1 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            left.forEach { content(it) }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            right.forEach { content(it) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteItem,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colors = LocalGlassColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "noteCardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .then(if (note.isPinned && isDarkTheme) Modifier.background(ClaretPinnedTint) else Modifier)
            .border(
                border = if (note.isPinned) BorderStroke(1.5.dp, ClaretPinnedBorder) else BorderStroke(1.dp, colors.cardBorder),
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color(0xFF09090B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 24.dp)
                            .padding(bottom = 10.dp)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    fontSize = 14.sp,
                    color = colors.textPrimary.copy(alpha = 0.78f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (note.checklist.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                note.checklist.take(5).forEach { item ->
                    ChecklistRow(item = item)
                    Spacer(Modifier.height(4.dp))
                }
                if (note.checklist.size > 5) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "+ ещё ${note.checklist.size - 5}",
                        fontSize = 12.sp,
                        color = colors.textSecondary.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTimestamp(note.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }

        AnimatedVisibility(
            visible = !selectionMode,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = scaleIn(tween(180)) + fadeIn(tween(180)),
            exit = scaleOut(tween(140)) + fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, end = 10.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePin),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = "Закрепить",
                    tint = if (note.isPinned) ClaretLight else colors.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = scaleIn(tween(180)) + fadeIn(tween(180)),
            exit = scaleOut(tween(140)) + fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, end = 10.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ClaretAccent else Color.Transparent)
                    .border(1.5.dp, if (isSelected) ClaretAccent else colors.textSecondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "Выбрано",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem) {
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
                ),
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
            fontSize = 14.sp,
            color = if (item.isChecked) colors.textSecondary else colors.textPrimary.copy(alpha = 0.78f),
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .imePadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 36.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Новая папка",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(16.dp))
            val fieldTextStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.innerBlock)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = fieldTextStyle,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    cursorBrush = SolidColor(colors.textPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (name.isEmpty()) {
                                Text("Название папки", color = colors.textSecondary, style = fieldTextStyle)
                            }
                            inner()
                        }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена", color = colors.textSecondary) }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ClaretAccent)
                        .clickable {
                            if (name.isNotBlank()) {
                                onConfirm(name)
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Создать",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}





