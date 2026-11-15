package com.kirill.glassnotes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirill.glassnotes.ui.theme.ClaretAccent
import com.kirill.glassnotes.ui.theme.LocalGlassColors
import java.util.UUID
import kotlin.math.roundToInt

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
    var focusedChecklistItemId by remember { mutableStateOf<String?>(null) }
    var draggedChecklistItemId by remember { mutableStateOf<String?>(null) }
    var checklistDragOffsetY by remember { mutableFloatStateOf(0f) }
    var checkedExpanded by remember { mutableStateOf(true) }
    val enterVisible = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val itemHeightPx = with(density) { 44.dp.toPx() }

    fun requestClose() {
        if (!closing) {
            closing = true
            enterVisible.targetState = false
        }
    }

    // Режим быстрой записи: автофокус на основном тексте.
    LaunchedEffect(Unit) {
        if (existing == null) {
            contentFocusRequester.requestFocus()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { saveAndClose() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Назад",
                        tint = colors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FolderPicker(
                        folderId = folderId,
                        folders = state.folders,
                        onSelect = { folderId = it }
                    )
                }
                if (imeVisible) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ClaretAccent)
                            .springClickable { saveAndClose() }
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
                } else if (existing != null) {
                    IconButton(onClick = {
                        viewModel.moveNoteToTrash(existing.id)
                        requestClose()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "В корзину",
                            tint = colors.textPrimary
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
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
                    singleLine = true,
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
                    Spacer(Modifier.height(8.dp))
                    val activeItems = checklist.filter { !it.isChecked }
                    val checkedItems = checklist.filter { it.isChecked }

                    activeItems.forEachIndexed { index, item ->
                        val itemFocusRequester = remember(item.id) { FocusRequester() }
                        LaunchedEffect(item.id, focusedChecklistItemId) {
                            if (focusedChecklistItemId == item.id) {
                                itemFocusRequester.requestFocus()
                                focusedChecklistItemId = null
                            }
                        }
                        ChecklistEditorRow(
                            item = item,
                            index = index,
                            activeCount = activeItems.size,
                            itemHeightPx = itemHeightPx,
                            focusRequester = itemFocusRequester,
                            isDragging = draggedChecklistItemId == item.id,
                            dragOffsetY = if (draggedChecklistItemId == item.id) checklistDragOffsetY else 0f,
                            onDragStart = { draggedChecklistItemId = item.id; checklistDragOffsetY = 0f },
                            onDragOffsetChange = { checklistDragOffsetY = it },
                            onDragEnd = {
                                draggedChecklistItemId = null
                                checklistDragOffsetY = 0f
                            },
                            onMoveItem = { from, to ->
                                val ids = checklist.filter { !it.isChecked }.map { it.id }
                                if (from in ids.indices && to in ids.indices && from != to) {
                                    val fromPos = checklist.indexOfFirst { it.id == ids[from] }
                                    val toPos = checklist.indexOfFirst { it.id == ids[to] }
                                    if (fromPos >= 0 && toPos >= 0 && fromPos != toPos) {
                                        val moved = checklist.removeAt(fromPos)
                                        checklist.add(toPos, moved)
                                    }
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
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .springClickable {
                                val newItem = ChecklistItem(UUID.randomUUID().toString(), "", false)
                                checklist.add(newItem)
                                focusedChecklistItemId = newItem.id
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            tint = ClaretAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Новый пункт",
                            color = ClaretAccent,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (checkedItems.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
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
                            checkedItems.forEach { item ->
                                CheckedItemRow(
                                    item = item,
                                    onToggle = {
                                        val idx = checklist.indexOfFirst { it.id == item.id }
                                        if (idx >= 0) checklist[idx] = checklist[idx].copy(isChecked = false)
                                    },
                                    onDelete = { checklist.removeAll { it.id == item.id } }
                                )
                                Spacer(Modifier.height(8.dp))
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
                        painter = painterResource(R.drawable.ic_check_box),
                        contentDescription = "Добавить пункт",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Без папки") },
                onClick = { onSelect(null); expanded = false }
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = { onSelect(folder.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun Modifier.animateIndexChange(
    index: Int,
    itemId: String,
    itemHeightPx: Float
): Modifier {
    val offsetY = remember(itemId) { Animatable(0f) }
    val lastIndex = remember(itemId) { mutableStateOf(index) }
    LaunchedEffect(index) {
        if (lastIndex.value != index) {
            val delta = (lastIndex.value - index) * itemHeightPx
            lastIndex.value = index
            offsetY.snapTo(offsetY.value + delta)
            offsetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
        }
    }
    return this.graphicsLayer { translationY = offsetY.value }
}

@Composable
private fun ChecklistEditorRow(
    item: ChecklistItem,
    index: Int,
    activeCount: Int,
    itemHeightPx: Float,
    focusRequester: FocusRequester,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDragOffsetChange: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalGlassColors.current
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "checklistDragY"
    )
    val currentIndex = rememberUpdatedState(index)
    val currentActiveCount = rememberUpdatedState(activeCount)
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnMoveItem = rememberUpdatedState(onMoveItem)
    val currentOnOffsetChange = rememberUpdatedState(onDragOffsetChange)

    Row(
        modifier = Modifier
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = animatedDragY
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 8.dp.toPx()
                } else {
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    shadowElevation = 0f
                }
            }
            .then(
                if (!isDragging) Modifier.animateIndexChange(index, item.id, itemHeightPx) else Modifier
            )
            .fillMaxWidth()
            .then(
                if (isDragging) {
                    Modifier
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .background(colors.card, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else Modifier
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_indicator),
                contentDescription = "Перетащить",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                    var localOffsetY = 0f
                    detectDragGestures(
                        onDragStart = {
                            localOffsetY = 0f
                            currentOnDragStart.value()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            localOffsetY += amount.y
                            if (localOffsetY > itemHeightPx / 2f && currentIndex.value < currentActiveCount.value - 1) {
                                currentOnMoveItem.value(currentIndex.value, currentIndex.value + 1)
                                localOffsetY -= itemHeightPx
                            } else if (localOffsetY < -itemHeightPx / 2f && currentIndex.value > 0) {
                                currentOnMoveItem.value(currentIndex.value, currentIndex.value - 1)
                                localOffsetY += itemHeightPx
                            }
                            currentOnOffsetChange.value(localOffsetY)
                        },
                        onDragEnd = {
                            localOffsetY = 0f
                            currentOnDragEnd.value()
                        },
                        onDragCancel = {
                            localOffsetY = 0f
                            currentOnDragEnd.value()
                        }
                    )
                }
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.size(width = 32.dp, height = 24.dp),
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
        Spacer(Modifier.width(8.dp))
        val itemTextStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp)
        BasicTextField(
            value = item.text,
            onValueChange = onChange,
            singleLine = false,
            maxLines = 10,
            textStyle = itemTextStyle,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(colors.textPrimary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (item.text.isEmpty()) {
                        Text("Пункт", color = colors.textSecondary, style = itemTextStyle)
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.width(4.dp))
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
    onDelete: () -> Unit
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.text,
            color = colors.textSecondary,
            fontSize = 16.sp,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
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



