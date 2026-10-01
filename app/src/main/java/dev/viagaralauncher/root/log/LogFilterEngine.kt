// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root.log

import java.util.Locale

object LogFilterEngine {

    fun filterAndSearch(
        lines: List<LogLine>,
        filters: List<UserLogFilter> = emptyList(),
        query: String? = null,
        caseSensitive: Boolean = false,
        selectedLevel: LogLevel? = null,
        targetPackage: String? = null,
        seenPids: Set<String> = emptySet(),
    ): List<LogLine> {
        val enabledFilters = filters.filter { it.enabled }
        val includingFilters = enabledFilters.filter { it.including }
        val excludingFilters = enabledFilters.filter { !it.including }

        val cleanQuery = query?.trim()?.ifBlank { null }
        val cleanPkg = targetPackage?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }

        return lines.filter { line ->
            // 1. Target Package / App Filter
            if (cleanPkg != null) {
                val matchesApp = line.packageName?.lowercase(Locale.ROOT) == cleanPkg ||
                        line.content.lowercase(Locale.ROOT).contains(cleanPkg) ||
                        line.tag.lowercase(Locale.ROOT).contains(cleanPkg) ||
                        seenPids.contains(line.pid)

                if (!matchesApp) return@filter false
            }

            // 2. Selected Log Level Filter
            if (selectedLevel != null && line.level != selectedLevel) {
                return@filter false
            }

            // 3. User Defined Exclude Filters
            if (excludingFilters.isNotEmpty()) {
                val shouldExclude = excludingFilters.any { filter ->
                    matchesFilter(filter, line)
                }
                if (shouldExclude) return@filter false
            }

            // 4. User Defined Include Filters
            if (includingFilters.isNotEmpty()) {
                val matchesAnyInclude = includingFilters.any { filter ->
                    matchesFilter(filter, line)
                }
                if (!matchesAnyInclude) return@filter false
            }

            // 5. Query Search Filter
            if (cleanQuery != null) {
                val matchesQuery = line.tag.contains(cleanQuery, ignoreCase = !caseSensitive) ||
                        line.content.contains(cleanQuery, ignoreCase = !caseSensitive) ||
                        line.pid == cleanQuery ||
                        (line.packageName?.contains(cleanQuery, ignoreCase = !caseSensitive) == true)

                if (!matchesQuery) return@filter false
            }

            true
        }
    }

    private fun matchesFilter(filter: UserLogFilter, line: LogLine): Boolean {
        if (filter.allowedLevels.isNotEmpty() && !filter.allowedLevels.contains(line.level)) {
            return false
        }
        if (!filter.packageName.isNullOrBlank() &&
            line.packageName?.contains(filter.packageName, ignoreCase = true) != true
        ) {
            return false
        }
        if (!filter.tag.isNullOrBlank() &&
            !line.tag.contains(filter.tag, ignoreCase = true)
        ) {
            return false
        }
        if (!filter.pid.isNullOrBlank() && line.pid != filter.pid.trim()) {
            return false
        }
        if (!filter.tid.isNullOrBlank() && line.tid != filter.tid.trim()) {
            return false
        }
        if (!filter.content.isNullOrBlank() &&
            !line.content.contains(filter.content, ignoreCase = true)
        ) {
            return false
        }
        return true
    }
}
