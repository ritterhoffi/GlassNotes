package com.kirill.glassnotes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kirill.glassnotes.ui.theme.ClaretAccent
import com.kirill.glassnotes.ui.theme.ClaretLight
import com.kirill.glassnotes.ui.theme.LocalGlassColors
import kotlin.math.roundToInt

@Composable
fun FoldersScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalGlassColors.current
    val folders = state.folders.sortedBy { it.order }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FolderItem?>(null) }
    var draggedFolderId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }
    val draggedFromIndex = draggedFolderId
        ?.let { id -> folders.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val draggedTargetIndex = draggedFromIndex?.let { from ->
        (from + (dragOffsetY / rowHeightPx).roundToInt())
            .coerceIn(0, (folders.size - 1).coerceAtLeast(0))
    }

    val flingBehavior = rememberTelegramFlingBehavior()
    val (overscrollConnection, overscrollModifier) = rememberSpringOverscroll()
    val scrollState = rememberScrollState()
    var headerBottomPx by remember { mutableStateOf(0f) }
    var viewportTopPx by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    val fadeEndOffsetPx = with(density) { 28.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer { alpha = 0.99f }
                .onGloballyPositioned {
                    viewportTopPx = it.boundsInRoot().top
                    viewportHeightPx = it.size.height.toFloat()
                }
                .drawWithContent {
                    drawContent()
                    if (viewportHeightPx > 0f && headerBottomPx > viewportTopPx) {
                        val relHeaderBottom = (headerBottomPx - viewportTopPx).coerceAtLeast(0f)
                        val fadeStart = (relHeaderBottom / viewportHeightPx).coerceIn(0f, 1f)
                        val fadeEnd = ((relHeaderBottom + fadeEndOffsetPx) / viewportHeightPx).coerceIn(0f, 1f)
                        val bottomFadeHeight = 24.dp.toPx()
                        val bottomFadeStart = ((viewportHeightPx - bottomFadeHeight) / viewportHeightPx).coerceIn(fadeEnd, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                fadeStart to Color.Transparent,
                                fadeEnd to Color.Black,
                                bottomFadeStart to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, flingBehavior = flingBehavior)
                    .nestedScroll(overscrollConnection)
                    .then(overscrollModifier)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(64.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.5.dp, ClaretAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = ClaretLight,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Создавайте папки и перетаскивайте их, чтобы изменить порядок",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                ) {
                    if (folders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Нет папок", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().clipToBounds()) {
                            folders.forEachIndexed { index, folder ->
                                val shiftPx = if (draggedFromIndex != null && draggedTargetIndex != null && draggedFolderId != folder.id) {
                                    when {
                                        draggedTargetIndex > draggedFromIndex && index in (draggedFromIndex + 1)..draggedTargetIndex -> -rowHeightPx
                                        draggedTargetIndex < draggedFromIndex && index in draggedTargetIndex until draggedFromIndex -> rowHeightPx
                                        else -> 0f
                                    }
                                } else 0f
                                FolderRow(
                                    folder = folder,
                                    modifier = Modifier,
                                    isDragging = draggedFolderId == folder.id,
                                    dragOffsetY = if (draggedFolderId == folder.id) dragOffsetY else 0f,
                                    shiftPx = shiftPx,
                                    isAnyDragging = draggedFolderId != null,
                                    onDragStart = { draggedFolderId = folder.id; dragOffsetY = 0f },
                                    onDrag = { dy -> dragOffsetY += dy },
                                    onDragEnd = {
                                        val from = draggedFromIndex
                                        val to = draggedTargetIndex
                                        if (from != null && to != null && from != to) {
                                            viewModel.moveFolder(from, to)
                                        }
                                        draggedFolderId = null
                                        dragOffsetY = 0f
                                    },
                                    onRename = { renameTarget = folder },
                                    onDelete = { viewModel.deleteFolder(folder.id) }
                                )
                                if (index < folders.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = colors.divider,
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.divider,
                        thickness = 1.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .springClickable { showCreateDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            tint = ClaretAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Создать новую папку",
                            color = ClaretAccent,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .onGloballyPositioned { headerBottomPx = it.boundsInRoot().bottom }
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Назад",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = "Папки",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = "Новая папка",
            initialName = "",
            onConfirm = { name ->
                viewModel.addFolder(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { target ->
        FolderNameDialog(
            title = "Переименовать",
            initialName = target.name,
            onConfirm = { name ->
                viewModel.renameFolder(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun FolderRow(
    folder: FolderItem,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    shiftPx: Float = 0f,
    isAnyDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalGlassColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDrag = rememberUpdatedState(onDrag)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)

    val neighborShift = remember(folder.id) { Animatable(0f) }
    LaunchedEffect(isAnyDragging, shiftPx) {
        if (isAnyDragging) {
            neighborShift.animateTo(shiftPx, spring(dampingRatio = 0.85f, stiffness = 480f))
        } else {
            neighborShift.snapTo(0f)
        }
    }
    val neighborShiftY = neighborShift.value

    Row(
        modifier = modifier
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    scaleX = 1.03f
                    scaleY = 1.03f
                } else {
                    translationY = neighborShiftY
                    scaleX = 1f
                    scaleY = 1f
                }
            }
            .fillMaxWidth()
            .background(if (isDragging) colors.innerBlock else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_handle),
            contentDescription = "Перетащить",
            tint = colors.textSecondary,
            modifier = Modifier
                .size(24.dp)
                .pointerInput(folder.id) {
                    detectDragGestures(
                        onDragStart = { currentOnDragStart.value() },
                        onDrag = { change, amount -> change.consume(); currentOnDrag.value(amount.y) },
                        onDragEnd = { currentOnDragEnd.value() },
                        onDragCancel = { currentOnDragEnd.value() }
                    )
                }
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Box {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "Действия",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .springClickable { menuExpanded = true }
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.width(200.dp),
                offset = DpOffset(0.dp, 10.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = colors.card,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 8.dp
            ) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalGlassColors.current
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
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
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text(
                text = title,
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
                            if (name.text.isEmpty()) {
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
                        .springClickable {
                            if (name.text.isNotBlank()) {
                                onConfirm(name.text)
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Сохранить",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}