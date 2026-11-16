package com.kirill.glassnotes

/** Папка для группировки заметок. */
data class FolderItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val order: Int = 0
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
    val createdAt: Long,
    val order: Int = 0
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
    /** Режим «прозрачного стекла» (размытие + полупрозрачные слои). */
    val isGlassEnabled: Boolean = true
)

/** Эффективный радиус размытия обоев в зависимости от режима стекла. */
val GlassSettings.blurRadius: Int
    get() = if (isGlassEnabled) (90 * 0.95f).toInt() else 0

/** Эффективная плотность фона экрана. */
val GlassSettings.backgroundAlpha: Float
    get() = if (isGlassEnabled) 0.30f else 1.0f

/** Эффективная плотность карточек. */
val GlassSettings.glassAlpha: Float
    get() = if (isGlassEnabled) 0.90f else 1.0f
