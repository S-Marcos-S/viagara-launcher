// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root.log

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FilterStorage(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val filterFile = File(context.filesDir, "log_user_filters.json")

    private val _filters = MutableStateFlow<List<UserLogFilter>>(emptyList())
    val filters: StateFlow<List<UserLogFilter>> = _filters.asStateFlow()

    init {
        loadFilters()
    }

    fun addFilter(filter: UserLogFilter) {
        val updated = _filters.value + filter
        _filters.value = updated
        saveFilters(updated)
    }

    fun updateFilter(filter: UserLogFilter) {
        val updated = _filters.value.map { if (it.id == filter.id) filter else it }
        _filters.value = updated
        saveFilters(updated)
    }

    fun toggleFilter(id: Long, enabled: Boolean) {
        val updated = _filters.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        _filters.value = updated
        saveFilters(updated)
    }

    fun deleteFilter(id: Long) {
        val updated = _filters.value.filter { it.id != id }
        _filters.value = updated
        saveFilters(updated)
    }

    private fun saveFilters(list: List<UserLogFilter>) {
        scope.launch {
            runCatching {
                val array = JSONArray()
                for (item in list) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("including", item.including)
                        put("packageName", item.packageName ?: "")
                        put("tag", item.tag ?: "")
                        put("pid", item.pid ?: "")
                        put("tid", item.tid ?: "")
                        put("content", item.content ?: "")
                        put("enabled", item.enabled)

                        val levelsArr = JSONArray()
                        item.allowedLevels.forEach { levelsArr.put(it.name) }
                        put("allowedLevels", levelsArr)
                    }
                    array.put(obj)
                }
                filterFile.writeText(array.toString())
            }
        }
    }

    private fun loadFilters() {
        scope.launch {
            runCatching {
                if (!filterFile.exists()) {
                    // Initialize with some helpful default filters
                    val defaults = listOf(
                        UserLogFilter(
                            id = 1L,
                            name = "Ocultar Chatty/Logd",
                            including = false,
                            tag = "chatty",
                            enabled = true,
                        ),
                    )
                    _filters.value = defaults
                    saveFilters(defaults)
                    return@launch
                }

                val array = JSONArray(filterFile.readText())
                val list = mutableListOf<UserLogFilter>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val levels = mutableSetOf<LogLevel>()
                    val levelsArr = obj.optJSONArray("allowedLevels")
                    if (levelsArr != null) {
                        for (j in 0 until levelsArr.length()) {
                            runCatching { levels.add(LogLevel.valueOf(levelsArr.getString(j))) }
                        }
                    }

                    list.add(
                        UserLogFilter(
                            id = obj.optLong("id", System.currentTimeMillis()),
                            name = obj.getString("name"),
                            including = obj.optBoolean("including", true),
                            allowedLevels = levels,
                            packageName = obj.optString("packageName").ifBlank { null },
                            tag = obj.optString("tag").ifBlank { null },
                            pid = obj.optString("pid").ifBlank { null },
                            tid = obj.optString("tid").ifBlank { null },
                            content = obj.optString("content").ifBlank { null },
                            enabled = obj.optBoolean("enabled", true),
                        )
                    )
                }
                _filters.value = list
            }
        }
    }
}
