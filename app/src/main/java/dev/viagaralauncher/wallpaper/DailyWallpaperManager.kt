// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import dev.viagaralauncher.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

object DailyWallpaperManager {

    private val _isChangingWallpaper = MutableStateFlow(false)
    val isChangingWallpaper: StateFlow<Boolean> = _isChangingWallpaper.asStateFlow()

    fun isDifferentDay(lastMs: Long, nowMs: Long): Boolean {
        if (lastMs <= 0L) return true
        val calLast = Calendar.getInstance().apply { timeInMillis = lastMs }
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMs }
        return calLast.get(Calendar.ERA) != calNow.get(Calendar.ERA) ||
               calLast.get(Calendar.YEAR) != calNow.get(Calendar.YEAR) ||
               calLast.get(Calendar.DAY_OF_YEAR) != calNow.get(Calendar.DAY_OF_YEAR)
    }

    fun checkAndAdvanceIfNewDay(context: Context, coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.IO) {
            val prefs = Prefs(context)
            val isEnabled = prefs.autoDailyWallpaperEnabled.first()
            if (!isEnabled) return@launch

            val lastTimestamp = prefs.dailyWallpaperLastTimestamp.first()
            val currentId = prefs.dailyWallpaperCurrentId.first()
            val now = System.currentTimeMillis()

            if (lastTimestamp <= 0L || currentId.isBlank() || isDifferentDay(lastTimestamp, now)) {
                advanceToNextWallpaper(context, isUserDislike = false)
            }
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        prefs.setAutoDailyWallpaperEnabled(enabled)
        if (enabled) {
            val lastTimestamp = prefs.dailyWallpaperLastTimestamp.first()
            val currentId = prefs.dailyWallpaperCurrentId.first()
            val now = System.currentTimeMillis()
            if (lastTimestamp <= 0L || currentId.isBlank() || isDifferentDay(lastTimestamp, now)) {
                advanceToNextWallpaper(context, isUserDislike = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun advanceToNextWallpaper(
        context: Context,
        isUserDislike: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        if (_isChangingWallpaper.value) return@withContext false
        _isChangingWallpaper.value = true

        try {
            val catalog = WallpaperRepository.loadCatalog(context)
            val eligible = catalog.wallpapers.filter { !it.isLive && it.type != "video" }
                .ifEmpty { catalog.wallpapers }

            if (eligible.isEmpty()) {
                return@withContext false
            }

            val prefs = Prefs(context)
            val currentId = prefs.dailyWallpaperCurrentId.first()

            val currentIndex = eligible.indexOfFirst { it.id == currentId }
            val nextIndex = if (currentIndex >= 0) {
                (currentIndex + 1) % eligible.size
            } else {
                0
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            } else {
                0
            }

            var targetIndex = nextIndex
            var applySuccess = false
            var attempts = 0
            val maxAttempts = minOf(3, eligible.size)

            while (attempts < maxAttempts && !applySuccess) {
                val candidate = eligible[targetIndex]
                val applyResult = WallpaperRepository.applyWallpaper(context, candidate, flags)
                if (applyResult.isSuccess) {
                    prefs.setDailyWallpaperCurrentId(candidate.id)
                    prefs.setDailyWallpaperIndex(targetIndex)
                    prefs.setDailyWallpaperLastTimestamp(System.currentTimeMillis())
                    applySuccess = true
                } else {
                    targetIndex = (targetIndex + 1) % eligible.size
                    attempts++
                }
            }
            applySuccess
        } finally {
            _isChangingWallpaper.value = false
        }
    }

    suspend fun handleLike(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val currentId = prefs.dailyWallpaperCurrentId.first()
        if (currentId.isNotBlank()) {
            WallpaperRepository.addFavorite(context, currentId)
            true
        } else {
            false
        }
    }
}
