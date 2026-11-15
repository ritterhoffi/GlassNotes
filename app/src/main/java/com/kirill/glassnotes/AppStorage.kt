package com.kirill.glassnotes

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Снимок всех данных приложения, которые хранятся в едином JSON-файле. */
data class AppData(
    val folders: List<FolderItem>,
    val notes: List<NoteItem>,
    val tasks: List<TaskItem>
)

/**
 * Хранение данных: единый JSON-файл `glass_notes_data.json` во внутренней памяти
 * и `SharedPreferences` для настроек стекла. Сериализация только через `org.json`.
 */
class AppStorage(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dataFile: File = File(appContext.filesDir, FILE_NAME)

    fun loadData(): AppData {
        if (!dataFile.exists()) return AppData(emptyList(), emptyList(), emptyList())
        return try {
            val json = JSONObject(dataFile.readText())
            AppData(
                folders = parseFolders(json.optJSONArray(KEY_FOLDERS)),
                notes = parseNotes(json.optJSONArray(KEY_NOTES)),
                tasks = parseTasks(json.optJSONArray(KEY_TASKS))
            )
        } catch (e: Exception) {
            AppData(emptyList(), emptyList(), emptyList())
        }
    }

    fun saveData(data: AppData) {
        try {
            dataFile.writeText(buildBackupJson(data))
        } catch (e: Exception) {
            // Небольшие локальные данные — игнорируем ошибку ввода-вывода.
        }
    }

    /** Генерация JSON для экспорта бэкапа (та же структура, что и в файле данных). */
    fun buildBackupJson(data: AppData): String {
        val folders = JSONArray()
        data.folders.forEach { folders.put(it.toJson()) }

        val notes = JSONArray()
        data.notes.forEach { notes.put(it.toJson()) }

        val tasks = JSONArray()
        data.tasks.forEach { tasks.put(it.toJson()) }

        return JSONObject()
            .put(KEY_FOLDERS, folders)
            .put(KEY_NOTES, notes)
            .put(KEY_TASKS, tasks)
            .toString()
    }

    fun loadSettings(): GlassSettings = GlassSettings(
        isDarkGlass = prefs.getBoolean(KEY_DARK, true),
        isGlassEnabled = prefs.getBoolean(KEY_GLASS_ENABLED, true)
    )

    fun saveSettings(settings: GlassSettings) {
        prefs.edit()
            .putBoolean(KEY_DARK, settings.isDarkGlass)
            .putBoolean(KEY_GLASS_ENABLED, settings.isGlassEnabled)
            .apply()
    }

    // ---------- Парсинг ----------

    private fun parseFolders(arr: JSONArray?): List<FolderItem> {
        if (arr == null) return emptyList()
        val result = ArrayList<FolderItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            result.add(
                FolderItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    createdAt = o.optLong("createdAt"),
                    order = o.optInt("order", 0)
                )
            )
        }
        return result
    }

    private fun parseNotes(arr: JSONArray?): List<NoteItem> {
        if (arr == null) return emptyList()
        val result = ArrayList<NoteItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val checklistArr = o.optJSONArray("checklist")
            val checklist = ArrayList<ChecklistItem>()
            if (checklistArr != null) {
                for (j in 0 until checklistArr.length()) {
                    val c = checklistArr.optJSONObject(j) ?: continue
                    checklist.add(
                        ChecklistItem(
                            id = c.optString("id"),
                            text = c.optString("text"),
                            isChecked = c.optBoolean("isChecked")
                        )
                    )
                }
            }
            result.add(
                NoteItem(
                    id = o.optString("id"),
                    folderId = o.optString("folderId").takeIf { it.isNotEmpty() },
                    title = o.optString("title"),
                    content = o.optString("content"),
                    checklist = checklist,
                    isPinned = o.optBoolean("isPinned"),
                    isDeleted = o.optBoolean("isDeleted"),
                    createdAt = o.optLong("createdAt"),
                    order = o.optInt("order", 0)
                )
            )
        }
        return result
    }

    private fun parseTasks(arr: JSONArray?): List<TaskItem> {
        if (arr == null) return emptyList()
        val result = ArrayList<TaskItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            result.add(
                TaskItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    dueDate = o.optString("dueDate").takeIf { it.isNotEmpty() },
                    isCompleted = o.optBoolean("isCompleted"),
                    createdAt = o.optLong("createdAt")
                )
            )
        }
        return result
    }

    // ---------- Сериализация ----------

    private fun FolderItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("order", order)
    }

    private fun NoteItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        if (folderId != null) put("folderId", folderId)
        put("title", title)
        put("content", content)
        val checklistArr = JSONArray()
        checklist.forEach {
            checklistArr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("isChecked", it.isChecked)
            )
        }
        put("checklist", checklistArr)
        put("isPinned", isPinned)
        put("isDeleted", isDeleted)
        put("createdAt", createdAt)
        put("order", order)
    }

    private fun TaskItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        if (dueDate != null) put("dueDate", dueDate)
        put("isCompleted", isCompleted)
        put("createdAt", createdAt)
    }

    private companion object {
        const val PREFS_NAME = "glass_notes_prefs"
        const val FILE_NAME = "glass_notes_data.json"

        const val KEY_FOLDERS = "folders"
        const val KEY_NOTES = "notes"
        const val KEY_TASKS = "tasks"

        const val KEY_DARK = "isDarkGlass"
        const val KEY_GLASS_ENABLED = "isGlassEnabled"
    }
}

