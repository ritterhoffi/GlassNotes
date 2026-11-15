package com.kirill.glassnotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Единое состояние приложения, которым управляет [MainViewModel]. */
data class GlassNotesState(
    val folders: List<FolderItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val settings: GlassSettings = GlassSettings(),
    val searchQuery: String = "",
    val selectedFolderId: String? = null
)

/** Управление заметками, папками, задачами, поиском, корзиной и настройками стекла. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = AppStorage(application)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<GlassNotesState> = _state.asStateFlow()

    private fun loadInitialState(): GlassNotesState {
        val data = storage.loadData()
        return GlassNotesState(
            folders = data.folders,
            notes = data.notes,
            tasks = data.tasks,
            settings = storage.loadSettings()
        )
    }

    // ---------- Папки ----------

    fun addFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val order = (_state.value.folders.maxOfOrNull { it.order } ?: -1) + 1
        val folder = FolderItem(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            order = order
        )
        _state.update { it.copy(folders = it.folders + folder) }
        persistData()
    }

    fun deleteFolder(folderId: String) {
        _state.update { current ->
            current.copy(
                folders = current.folders.filterNot { it.id == folderId },
                notes = current.notes.map { note ->
                    if (note.folderId == folderId) note.copy(folderId = null) else note
                },
                selectedFolderId = if (current.selectedFolderId == folderId) null else current.selectedFolderId
            )
        }
        persistData()
    }

    // ---------- Заметки ----------

    fun saveNote(note: NoteItem) {
        _state.update { current ->
            val exists = current.notes.any { it.id == note.id }
            val notes = if (exists) {
                current.notes.map { if (it.id == note.id) note else it }
            } else {
                val order = (current.notes.maxOfOrNull { it.order } ?: -1) + 1
                current.notes + note.copy(order = order)
            }
            current.copy(notes = notes)
        }
        persistData()
    }

    fun moveNoteToTrash(noteId: String) {
        _state.update { current ->
            current.copy(
                notes = current.notes.map { if (it.id == noteId) it.copy(isDeleted = true) else it }
            )
        }
        persistData()
    }

    fun moveNotesToTrash(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        _state.update { current ->
            current.copy(
                notes = current.notes.map { if (it.id in noteIds) it.copy(isDeleted = true) else it }
            )
        }
        persistData()
    }

    fun toggleNotePin(noteId: String) {
        _state.update { current ->
            current.copy(
                notes = current.notes.map { if (it.id == noteId) it.copy(isPinned = !it.isPinned) else it }
            )
        }
        persistData()
    }

    fun toggleChecklistItem(noteId: String, itemId: String) {
        _state.update { current ->
            current.copy(
                notes = current.notes.map { note ->
                    if (note.id == noteId) {
                        note.copy(
                            checklist = note.checklist.map { item ->
                                if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
                            }
                        )
                    } else {
                        note
                    }
                }
            )
        }
        persistData()
    }

    // ---------- Корзина ----------

    fun restoreNote(noteId: String) {
        _state.update { current ->
            current.copy(
                notes = current.notes.map { if (it.id == noteId) it.copy(isDeleted = false) else it }
            )
        }
        persistData()
    }

    fun deleteNoteForever(noteId: String) {
        _state.update { current ->
            current.copy(notes = current.notes.filterNot { it.id == noteId })
        }
        persistData()
    }

    fun clearTrash() {
        _state.update { current ->
            current.copy(notes = current.notes.filterNot { it.isDeleted })
        }
        persistData()
    }

    // ---------- Задачи ----------

    fun addTask(title: String, dueDate: String?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            title = trimmed,
            dueDate = dueDate,
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )
        _state.update { it.copy(tasks = it.tasks + task) }
        persistData()
    }

    fun toggleTask(taskId: String) {
        _state.update { current ->
            current.copy(
                tasks = current.tasks.map { if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it }
            )
        }
        persistData()
    }

    fun deleteTask(taskId: String) {
        _state.update { current ->
            current.copy(tasks = current.tasks.filterNot { it.id == taskId })
        }
        persistData()
    }

    // ---------- Настройки стекла ----------

    fun updateSettings(settings: GlassSettings) {
        _state.update { it.copy(settings = settings) }
        storage.saveSettings(settings)
    }

    /** Переключение темы стекла. */
    fun setDarkGlass(isDark: Boolean) {
        val current = _state.value.settings
        if (current.isDarkGlass == isDark) return
        updateSettings(current.copy(isDarkGlass = isDark))
    }

    /** Переключение режима «прозрачного стекла». */
    fun setGlassEnabled(enabled: Boolean) {
        val current = _state.value.settings
        if (current.isGlassEnabled == enabled) return
        updateSettings(current.copy(isGlassEnabled = enabled))
    }

    /** Переименование папки. */
    fun renameFolder(folderId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            current.copy(
                folders = current.folders.map { if (it.id == folderId) it.copy(name = trimmed) else it }
            )
        }
        persistData()
    }

    /** Перемещение папки на новую позицию (drag-and-drop). */
    fun moveFolder(fromIndex: Int, toIndex: Int) {
        _state.update { current ->
            val sorted = current.folders.sortedBy { it.order }.toMutableList()
            if (fromIndex !in sorted.indices || toIndex !in sorted.indices || fromIndex == toIndex) {
                return@update current
            }
            val item = sorted.removeAt(fromIndex)
            sorted.add(toIndex, item)
            current.copy(folders = sorted.mapIndexed { i, f -> f.copy(order = i) })
        }
        persistData()
    }

    /** Обмен порядком двух заметок (drag-and-drop в сетке). */
    fun swapNotesOrder(firstId: String, secondId: String) {
        _state.update { current ->
            val first = current.notes.firstOrNull { it.id == firstId } ?: return@update current
            val second = current.notes.firstOrNull { it.id == secondId } ?: return@update current
            current.copy(
                notes = current.notes.map { note ->
                    when (note.id) {
                        firstId -> note.copy(order = second.order)
                        secondId -> note.copy(order = first.order)
                        else -> note
                    }
                }
            )
        }
        persistData()
    }

    /** Актуальный JSON для экспорта резервной копии. */
    fun buildBackupJson(): String {
        val s = _state.value
        return storage.buildBackupJson(AppData(folders = s.folders, notes = s.notes, tasks = s.tasks))
    }

    // ---------- Поиск и выбранная папка ----------

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setSelectedFolderId(folderId: String?) {
        _state.update { it.copy(selectedFolderId = folderId) }
    }

    // ---------- Персистентность ----------

    private fun persistData() {
        val s = _state.value
        storage.saveData(AppData(folders = s.folders, notes = s.notes, tasks = s.tasks))
    }
}
