// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.root.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpenLogViewerRequest(
    val packageName: String? = null,
    val initialTab: String? = null,
)

object LogViewerEvents {
    private val _request = MutableStateFlow<OpenLogViewerRequest?>(null)
    val request: StateFlow<OpenLogViewerRequest?> = _request.asStateFlow()

    fun open(packageName: String? = null, initialTab: String? = null) {
        _request.value = OpenLogViewerRequest(packageName, initialTab)
    }

    fun consume() {
        _request.value = null
    }
}
