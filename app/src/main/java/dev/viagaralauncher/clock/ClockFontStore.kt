// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.clock

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

data class ClockFontFile(val id: String, val name: String)

class ClockFontStore(context: Context) {
    private val context = context.applicationContext
    private val directory: File get() = File(context.filesDir, "clock_fonts")

    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024 // 20 MB

        fun validId(name: String): Boolean =
            name.matches(Regex("^[a-zA-Z0-9_-]+\\.(ttf|otf|ttc)$"))

        fun displayName(id: String?): String? {
            if (id == null) return null
            return when (id) {
                "serif" -> "Serif"
                "sans_serif" -> "Sans Serif"
                "monospace" -> "Monospace"
                "cursive" -> "Cursive"
                else -> id.substringAfter('_').substringBeforeLast('.').ifBlank { id }
            }
        }
    }

    fun file(id: String): File? =
        id.takeIf(::validId)?.let { File(directory, it) }?.takeIf { it.isFile }

    suspend fun delete(ids: Set<String>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            file(id)?.let { target ->
                if (target.exists()) target.delete()
            }
        }
    }

    suspend fun list(): List<ClockFontFile> = withContext(Dispatchers.IO) {
        if (!directory.exists()) return@withContext emptyList()
        directory.listFiles().orEmpty()
            .filter { it.isFile && validId(it.name) }
            .map { ClockFontFile(it.name, it.name.substringAfter('_').substringBeforeLast('.')) }
            .sortedBy { it.name }
    }

    suspend fun import(uri: Uri): ClockFontFile = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Font.ttf"
        context.contentResolver.openInputStream(uri)?.use { importStream(it, name) }
            ?: throw IllegalArgumentException("Não foi possível ler o arquivo de fonte")
    }

    private fun importStream(input: InputStream, displayName: String): ClockFontFile {
        if (!directory.exists()) directory.mkdirs()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("ttf", "otf", "ttc")) { "Selecione uma fonte TTF, OTF ou TTC válida" }
        val label = displayName.substringBeforeLast('.').filter { it.isLetterOrDigit() || it in " _-" }
            .trim().take(80).ifBlank { "Font" }
        val target = File(directory, "${UUID.randomUUID()}_$label.$extension")
        target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "A fonte excede o tamanho máximo de 20 MB" }
                output.write(buffer, 0, count)
            }
        }
        return ClockFontFile(target.name, label)
    }
}
