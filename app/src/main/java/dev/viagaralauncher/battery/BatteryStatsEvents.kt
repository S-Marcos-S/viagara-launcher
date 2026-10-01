// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.battery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BatteryStatsEvents {
    private val _openRequested = MutableStateFlow(false)
    val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()

    fun open() {
        _openRequested.value = true
    }

    fun consume() {
        _openRequested.value = false
    }
}
