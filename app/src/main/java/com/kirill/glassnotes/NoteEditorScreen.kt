package com.kirill.glassnotes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.kirill.glassnotes.ui.theme.ClaretAccent
import com.kirill.glassnotes.ui.theme.LocalGlassColors
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun NoteEditorScreen(
    viewModel: MainViewModel,
    target: EditorTarget,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalGlassColors.current

    val existing = when (target) {
        is EditorTarget.Edit -> state.notes.firstOrNull { it.id == target.noteId }
        is EditorTarget.New -> null
    }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    val checklist = remember {
        mutableStateListOf<ChecklistItem>().apply {
            existing?.checklist?.forEach { add(it) }
        }
    }
    var folderId by remember {
        mutableStateOf(existing?.folderId ?: (target as? EditorTarget.New)?.folderId)
    }
    val createdAt = remember { existing?.createdAt ?: System.currentTimeMillis() }

    val contentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focusedChecklistItemId by remember { mutableStateOf<String?>(null) }
    var draggedChecklistItemId by remember { mutableStateOf<String?>(null) }
    var checklistDragOffsetY by remember { mutableFloatStateOf(0f) }
    var checkedExpanded by remember { mutableStateOf(true) }
    val enterVisible = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var imeDismissed by remember { mutableStateOf(false) }
    var prevImeBottomPx by remember { mutableIntStateOf(0) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeClosing = imeBottomPx < prevImeBottomPx
    SideEffect {
        if (imeBottomPx > prevImeBottomPx) {
            imeDismissed = false
        }
        prevImeBottomPx = imeBottomPx
    }
    val imeVisible = !imeDismissed && !imeClosing && imeBottomPx > with(density) { 80.dp.toPx() }
    val itemHeightPx = with(density) { 44.dp.toPx() }
    val focusedChecklistItemIds = remember { mutableStateListOf<String>() }
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var dragStartFocusedItemId by remember { mutableStateOf<String?>(null) }

    fun requestClose() {
        if (!closing) {
            closing = true
            enterVisible.targetState = false
        }
    }

    // Режим быстрой записи: автофокус на основном тексте (или первом пункте чек-листа).
    LaunchedEffect(Unit) {
        val isNewNote = existing == null
        val isEmptyNote = title.isBlank() && content.isBlank()
        if (isNewNote || isEmptyNote) {
            delay(120)
            val firstActiveId = checklist.firstOrNull { !it.isChecked }?.id
            if (firstActiveId != null) {
                focusedChecklistItemId = firstActiveId
            } else {
                contentFocusRequester.requestFocus()
            }
            keyboardController?.show()
        }
    }

    fun saveAndClose() {
        val hasAny = title.isNotBlank() || content.isNotBlank() || checklist.any { it.text.isNotBlank() }
        if (hasAny) {
            val cleanedChecklist = checklist
                .filter { it.text.isNotBlank() }
                .map { it.copy(text = it.text.trim()) }
            val note = if (existing != null) {
                existing.copy(
                    title = title.trim(),
                    content = content.trim(),
                    checklist = cleanedChecklist,
                    folderId = folderId
                )
            } else {
                NoteItem(
                    id = UUID.randomUUID().toString(),
                    folderId = folderId,
                    title = title.trim(),
                    content = content.trim(),
                    checklist = cleanedChecklist,
                    isPinned = false,
                    isDeleted = false,
                    createdAt = createdAt
                )
            }
            viewModel.saveNote(note)
        }
        requestClose()
    }

    BackHandler { saveAndClose() }

    LaunchedEffect(Unit) { enterVisible.targetState = true }

    LaunchedEffect(closing, enterVisible.isIdle) {
        if (closing && enterVisible.isIdle && !enterVisible.currentState) {
            onClose()
        }
    }

    AnimatedVisibility(
        visibleState = enterVisible,
        enter = fadeIn(animationSpec = tween(220)) +
            slideInVertically(initialOffsetY = { it / 16 }, animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)) +
            slideOutHorizontally(animationSpec = tween(180), targetOffsetX = { it / 4 })
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = ClaretAccent.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = { saveAndClose() },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Назад",
                            tint = colors.textPrimary
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        FolderPicker(
                            folderId = folderId,
                            folders = state.folders,
                            onSelect = { folderId = it }
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (imeVisible) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ClaretAccent)
                                    .springClickable {
                                        imeDismissed = true
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        saveAndClose()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Готово",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            existing?.let { note ->
                                IconButton(onClick = {
                                    viewModel.moveNoteToTrash(note.id)
                                    requestClose()
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = "В корзину",
                                        tint = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp)
                ) {
                    val titleStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp
                    )
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = false,
                        maxLines = Int.MAX_VALUE,
                        textStyle = titleStyle,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        cursorBrush = SolidColor(colors.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) {
                                    Text("Заголовок", color = colors.textSecondary, style = titleStyle)
                                }
                                inner()
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (content.isNotBlank() || checklist.isEmpty()) {
                        val contentStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp)
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = contentStyle,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            cursorBrush = SolidColor(colors.textPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(contentFocusRequester),
                            decorationBox = { inner ->
                                Box {
                                    if (content.isEmpty()) {
                                        Text("Начните писать…", color = colors.textSecondary, style = contentStyle)
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    if (checklist.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        val activeItems = checklist.filter { !it.isChecked }
                        val checkedItems = checklist.filter { it.isChecked }
                        val activeIds = activeItems.map { it.id }
                        val draggedFromIndex = draggedChecklistItemId
                            ?.let { id -> activeIds.indexOf(id).takeIf { it >= 0 } }
                        val draggedTargetIndex = draggedFromIndex?.let { from ->
                            (from + (checklistDragOffsetY / itemHeightPx).roundToInt())
                                .coerceIn(0, (activeIds.size - 1).coerceAtLeast(0))
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            activeItems.forEachIndexed { index, item ->
                                val itemFocusRequester = focusRequesters.getOrPut(item.id) { FocusRequester() }
                                LaunchedEffect(item.id, focusedChecklistItemId) {
                                    if (focusedChecklistItemId == item.id) {
                                        itemFocusRequester.requestFocus()
                                        keyboardController?.show()
                                        focusedChecklistItemId = null
                                    }
                                }
                                val shiftPx = if (draggedFromIndex != null && draggedTargetIndex != null && draggedChecklistItemId != item.id) {
                                    when {
                                        draggedTargetIndex > draggedFromIndex && index in (draggedFromIndex + 1)..draggedTargetIndex -> -itemHeightPx
                                        draggedTargetIndex < draggedFromIndex && index in draggedTargetIndex until draggedFromIndex -> itemHeightPx
                                        else -> 0f
                                    }
                                } else 0f
                                ChecklistEditorRow(
                                    item = item,
                                    focusRequester = itemFocusRequester,
                                    isDragging = draggedChecklistItemId == item.id,
                                    dragOffsetY = if (draggedChecklistItemId == item.id) checklistDragOffsetY else 0f,
                                    shiftPx = shiftPx,
                                    isAnyDragging = draggedChecklistItemId != null,
                                    onDragStart = {
                                        draggedChecklistItemId = item.id
                                        checklistDragOffsetY = 0f
                                        dragStartFocusedItemId = focusedChecklistItemIds.firstOrNull()
                                    },
                                    onDragOffsetChange = { checklistDragOffsetY += it },
                                    onDragEnd = {
                                        val from = draggedFromIndex
                                        val to = draggedTargetIndex
                                        if (from != null && to != null && from != to) {
                                            val fromPos = checklist.indexOfFirst { it.id == activeIds[from] }
                                            val toPos = checklist.indexOfFirst { it.id == activeIds[to] }
                                            if (fromPos >= 0 && toPos >= 0 && fromPos != toPos) {
                                                val moved = checklist.removeAt(fromPos)
                                                checklist.add(toPos, moved)
                                            }
                                        }
                                        draggedChecklistItemId = null
                                        checklistDragOffsetY = 0f
                                        val focusedId = dragStartFocusedItemId
                                        dragStartFocusedItemId = null
                                        if (focusedId != null) {
                                            focusedChecklistItemId = focusedId
                                        }
                                    },
                                    onFocusChanged = { focused ->
                                        if (focused) {
                                            if (item.id !in focusedChecklistItemIds) focusedChecklistItemIds.add(item.id)
                                        } else {
                                            focusedChecklistItemIds.remove(item.id)
                                        }
                                    },
                                    onChange = { newText ->
                                        val idx = checklist.indexOfFirst { it.id == item.id }
                                        if (idx >= 0) checklist[idx] = checklist[idx].copy(text = newText)
                                    },
                                    onToggle = {
                                        val idx = checklist.indexOfFirst { it.id == item.id }
                                        if (idx >= 0) checklist[idx] = checklist[idx].copy(isChecked = !checklist[idx].isChecked)
                                    },
                                    onDelete = { checklist.removeAll { it.id == item.id } }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .springClickable {
                                        val newItem = ChecklistItem(UUID.randomUUID().toString(), "", false)
                                        checklist.add(newItem)
                                        focusedChecklistItemId = newItem.id
                                    }
                                    .padding(start = 34.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = null,
                                    tint = ClaretAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Новый пункт",
                                    color = ClaretAccent,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        if (checkedItems.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { checkedExpanded = !checkedExpanded }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (checkedExpanded) 0f else -90f)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "${checkedItems.size} отмеченных пунктов",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (checkedExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    checkedItems.forEach { item ->
                                        CheckedItemRow(
                                            item = item,
                                            onToggle = {
                                                val idx = checklist.indexOfFirst { it.id == item.id }
                                                if (idx >= 0) checklist[idx] = checklist[idx].copy(isChecked = false)
                                            },
                                            onChange = { newText ->
                                                val idx = checklist.indexOfFirst { it.id == item.id }
                                                if (idx >= 0) checklist[idx] = checklist[idx].copy(text = newText)
                                            },
                                            onFocusChanged = { focused ->
                                                if (focused) {
                                                    if (item.id !in focusedChecklistItemIds) focusedChecklistItemIds.add(item.id)
                                                } else {
                                                    focusedChecklistItemIds.remove(item.id)
                                                }
                                            },
                                            onDelete = { checklist.removeAll { it.id == item.id } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_date_range),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Создано: " + formatTimestamp(createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = !imeVisible,
                        enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                        exit = fadeOut(tween(140)) + scaleOut(tween(140))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ClaretAccent)
                                .springClickable {
                                    val newItem = ChecklistItem(UUID.randomUUID().toString(), "", false)
                                    checklist.add(newItem)
                                    focusedChecklistItemId = newItem.id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Добавить пункт",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPicker(
    folderId: String?,
    folders: List<FolderItem>,
    onSelect: (String?) -> Unit
) {
    val colors = LocalGlassColors.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    val label = folders.firstOrNull { it.id == folderId }?.name ?: "Без папки"

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(density) { 10.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(230.dp)
                        .shadow(8.dp, RoundedCornerShape(22.dp))
                        .background(colors.card, RoundedCornerShape(22.dp))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                        .padding(vertical = 6.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Без папки", color = colors.textPrimary) },
                        trailingIcon = {
                            if (folderId == null) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = "Выбрано",
                                    tint = ClaretAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        onClick = { onSelect(null); expanded = false }
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 276.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name, color = colors.textPrimary) },
                                trailingIcon = {
                                    if (folderId == folder.id) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check),
                                            contentDescription = "Выбрано",
                                            tint = ClaretAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                                onClick = { onSelect(folder.id); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistEditorRow(
    item: ChecklistItem,
    focusRequester: FocusRequester,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    shiftPx: Float = 0f,
    isAnyDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragOffsetChange: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalGlassColors.current
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnOffsetChange = rememberUpdatedState(onDragOffsetChange)
    val currentOnFocusChanged = rememberUpdatedState(onFocusChanged)

    val neighborShift = remember(item.id) { Animatable(0f) }
    LaunchedEffect(isAnyDragging, shiftPx) {
        if (isAnyDragging) {
            neighborShift.animateTo(shiftPx, spring(dampingRatio = 0.85f, stiffness = 480f))
        } else {
            neighborShift.snapTo(0f)
        }
    }
    val neighborShiftY = neighborShift.value

    Row(
        modifier = Modifier
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 10f
                } else {
                    translationY = neighborShiftY
                    scaleX = 1f
                    scaleY = 1f
                    shadowElevation = 0f
                }
            }
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (isDragging) {
                    Modifier
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .background(colors.card, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(vertical = 8.dp)
                }
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_indicator),
                contentDescription = "Перетащить",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { currentOnDragStart.value() },
                            onDrag = { change, amount ->
                                change.consume()
                                currentOnOffsetChange.value(amount.y)
                            },
                            onDragEnd = { currentOnDragEnd.value() },
                            onDragCancel = { currentOnDragEnd.value() }
                        )
                    }
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (item.isChecked) colors.textPrimary else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (item.isChecked) colors.textPrimary else colors.textSecondary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggle),
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
        }
        Spacer(Modifier.width(14.dp))
        val itemTextStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = item.text,
                onValueChange = onChange,
                singleLine = false,
                maxLines = 10,
                textStyle = itemTextStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { currentOnFocusChanged.value(it.isFocused) },
                decorationBox = { inner ->
                    Box {
                        if (item.text.isEmpty()) {
                            Text("Пункт", color = colors.textSecondary, style = itemTextStyle)
                        }
                        inner()
                    }
                }
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Удалить",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
private fun AddChecklistButton(onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Добавить пункт",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun CheckedItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    onDelete: () -> Unit
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 28.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.textPrimary)
                    .border(1.5.dp, colors.textPrimary, RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onTextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        val itemTextStyle = TextStyle(
            color = colors.textSecondary,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textDecoration = TextDecoration.LineThrough
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = item.text,
                onValueChange = onChange,
                singleLine = false,
                maxLines = 10,
                textStyle = itemTextStyle,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(colors.textSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                decorationBox = { inner ->
                    Box {
                        if (item.text.isEmpty()) {
                            Text("Пункт", color = colors.textSecondary, style = itemTextStyle)
                        }
                        inner()
                    }
                }
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Удалить",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}