// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.root

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TaskManagerEvents {
    private val _targetPackage = MutableStateFlow<String?>(null)
    val targetPackage: StateFlow<String?> = _targetPackage.asStateFlow()

    private val _openRequested = MutableStateFlow(false)
    val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()

    fun open(packageName: String? = null) {
        _targetPackage.value = packageName
        _openRequested.value = true
    }

    fun consume() {
        _openRequested.value = false
        _targetPackage.value = null
    }
}
