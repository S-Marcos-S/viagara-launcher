// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.wallpaper

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File

/**
 * Native Android Live Wallpaper Service for playing loop MP4 videos smoothly
 * as home screen / lock screen wallpapers with minimal battery consumption.
 */
class VideoLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    private inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var surfaceHolder: SurfaceHolder? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            this.surfaceHolder = holder
            startVideo()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.surfaceHolder = null
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                if (mediaPlayer == null) {
                    startVideo()
                } else if (!mediaPlayer!!.isPlaying) {
                    runCatching { mediaPlayer?.start() }
                }
            } else {
                runCatching { mediaPlayer?.pause() }
            }
        }

        private fun startVideo() {
            val holder = surfaceHolder ?: return
            val videoFile = getVideoFile(this@VideoLiveWallpaperService)
            if (!videoFile.exists() || videoFile.length() == 0L) {
                return
            }

            releasePlayer()

            runCatching {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(videoFile.absolutePath)
                    isLooping = true
                    setVolume(0f, 0f) // Always muted for wallpaper
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        if (isVisible) {
                            mp.start()
                        }
                    }
                }
            }
        }

        private fun releasePlayer() {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    companion object {
        private const val LIVE_WALLPAPER_FILE_NAME = "current_live_wallpaper.mp4"

        fun getVideoFile(context: Context): File {
            return File(context.filesDir, LIVE_WALLPAPER_FILE_NAME)
        }

        fun setVideoFile(context: Context, sourceFile: File): Boolean {
            return runCatching {
                val target = getVideoFile(context)
                sourceFile.copyTo(target, overwrite = true)
                true
            }.getOrDefault(false)
        }
    }
}
