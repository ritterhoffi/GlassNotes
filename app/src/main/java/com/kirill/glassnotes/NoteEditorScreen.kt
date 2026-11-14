package com.kirill.glassnotes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirill.glassnotes.ui.theme.LocalGlassColors
import java.util.UUID

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
        onClose()
    }

    BackHandler { saveAndClose() }

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
            if (existing != null) {
                IconButton(onClick = {
                    viewModel.moveNoteToTrash(existing.id)
                    onClose()
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
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text("Заголовок", color = colors.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(contentFocusRequester),
                decorationBox = { inner ->
                    Box {
                        if (content.isEmpty()) {
                            Text("Начните писать…", color = colors.textSecondary, fontSize = 17.sp)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(20.dp))

            checklist.forEachIndexed { index, item ->
                ChecklistEditorRow(
                    item = item,
                    onChange = { newText -> checklist[index] = item.copy(text = newText) },
                    onToggle = { checklist[index] = item.copy(isChecked = !item.isChecked) },
                    onDelete = { checklist.removeAt(index) }
                )
                Spacer(Modifier.height(8.dp))
            }
            AddChecklistButton(
                onClick = {
                    checklist.add(ChecklistItem(UUID.randomUUID().toString(), "", false))
                }
            )
            Spacer(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_date_range),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Создано: " + formatTimestamp(createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
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
private fun ChecklistEditorRow(
    item: ChecklistItem,
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
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
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = item.text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.textPrimary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (item.text.isEmpty()) {
                        Text("Пункт", color = colors.textSecondary, fontSize = 16.sp)
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



