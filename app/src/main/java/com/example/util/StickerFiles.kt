package com.example.util

import java.io.File
import java.util.zip.GZIPInputStream

internal object StickerFiles {
    fun format(path: String, declared: String?): String {
        if (declared in setOf("webp", "tgs", "webm")) return declared!!
        val header = File(path).inputStream().use { stream -> ByteArray(4).also { stream.read(it) } }
        return when {
            header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte() -> "tgs"
            header.contentEquals(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())) -> "webm"
            else -> "webp"
        }
    }

    fun tgsJson(path: String): String = File(path).inputStream().buffered().use { input ->
        input.mark(2)
        val compressed = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        if (compressed) GZIPInputStream(input).bufferedReader().use { it.readText() }
        else input.bufferedReader().use { it.readText() }
    }
}
