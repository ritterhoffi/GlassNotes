package com.kirill.glassnotes

/** Папка для группировки заметок. */
data class FolderItem(
    val id: String,
    val name: String,
    val createdAt: Long
)

/** Пункт чек-листа внутри заметки. */
data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean
)

/** Заметка с опциональным чек-листом и закреплением. */
data class NoteItem(
    val id: String,
    val folderId: String?,
    val title: String,
    val content: String,
    val checklist: List<ChecklistItem>,
    val isPinned: Boolean,
    val isDeleted: Boolean,
    val createdAt: Long
)

/** Задача с опциональной датой выполнения. */
data class TaskItem(
    val id: String,
    val title: String,
    val dueDate: String?,
    val isCompleted: Boolean,
    val createdAt: Long
)

/** Настройки внешнего вида стекла. */
data class GlassSettings(
    val isDarkGlass: Boolean = true,
    /** Сила размытия рабочего стола в пикселях (0..150). */
    val blurRadius: Int = 25,
    /** Прозрачность карточек (0f..1f). */
    val glassAlpha: Float = 0.7f
)
