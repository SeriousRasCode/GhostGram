package com.example

import com.example.util.StickerFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.GZIPOutputStream

class StickerFilesTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun `legacy sticker files without extensions detect TGS WebM and WebP`() {
        val tgs = files.newFile("animated")
        val json = "{\"v\":\"5.7.4\",\"w\":512,\"h\":512,\"layers\":[]}"
        GZIPOutputStream(tgs.outputStream()).bufferedWriter().use { it.write(json) }
        assertEquals("tgs", StickerFiles.format(tgs.path, null))
        assertEquals(json, StickerFiles.tgsJson(tgs.path))
        val webm = files.newFile("video")
        webm.writeBytes(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()))
        assertEquals("webm", StickerFiles.format(webm.path, null))
        val webp = files.newFile("static")
        webp.writeText("RIFF----WEBP")
        assertEquals("webp", StickerFiles.format(webp.path, null))
    }

    @Test fun `declared sticker format is retained and uncompressed animation JSON is supported`() {
        val raw = files.newFile("animation")
        val json = "{\"layers\":[]}"
        raw.writeText(json)
        assertEquals("tgs", StickerFiles.format(raw.path, "tgs"))
        assertEquals(json, StickerFiles.tgsJson(raw.path))
    }
}
