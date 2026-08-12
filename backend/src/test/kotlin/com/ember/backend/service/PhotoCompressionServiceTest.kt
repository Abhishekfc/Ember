package com.ember.backend.service

import com.ember.backend.exception.InvalidFriendRequestException
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The important case here is the decompression bomb: a compressed image's file size says nothing
 * about how much memory it decodes to, so a small upload — well inside the 25MB multipart limit —
 * could ask the JVM for gigabytes of raster and OOM the whole server. That guard is checked from
 * the file header, so it can be exercised without ever building a genuinely huge image.
 */
class PhotoCompressionServiceTest {

    private fun jpegOf(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.BLUE
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private fun pngOf(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `an image whose header declares more pixels than the cap is rejected before decoding`() {
        // A real PNG header, rewritten to claim 40000x40000 (1.6 billion pixels, ~6.4GB decoded).
        // The pixel data behind it is untouched and tiny — which is exactly the point: nothing
        // about this file's size hints at what decoding it would cost.
        val bomb = pngOf(8, 8).copyOf()
        // PNG IHDR: 8-byte signature, 4-byte length, 4-byte "IHDR", then width and height as
        // big-endian 32-bit integers at offsets 16 and 20.
        writeBigEndianInt(bomb, 16, 40_000)
        writeBigEndianInt(bomb, 20, 40_000)

        assertFailsWith<InvalidFriendRequestException> {
            PhotoCompressionService.compress(bomb, "image/png")
        }
    }

    @Test
    fun `an ordinary photo well under the cap is accepted`() {
        val result = PhotoCompressionService.compress(jpegOf(1080, 1920), "image/jpeg")
        assertEquals("image/jpeg", result.contentType)
        assertTrue(result.bytes.isNotEmpty())
    }

    @Test
    fun `an oversized but legitimate photo is scaled down rather than rejected`() {
        val result = PhotoCompressionService.compress(jpegOf(4000, 3000), "image/jpeg")
        val decoded = ImageIO.read(result.bytes.inputStream())
        assertTrue(decoded.width <= 2000 && decoded.height <= 2000, "not scaled: ${decoded.width}x${decoded.height}")
    }

    @Test
    fun `an already-small jpeg passes through byte-for-byte without a second lossy generation`() {
        val original = jpegOf(800, 600)
        val result = PhotoCompressionService.compress(original, "image/jpeg")
        assertTrue(original.contentEquals(result.bytes), "an in-spec JPEG was needlessly re-encoded")
    }

    @Test
    fun `a png photo is converted to jpeg`() {
        val result = PhotoCompressionService.compress(pngOf(600, 600), "image/png")
        assertEquals("image/jpeg", result.contentType)
    }

    @Test
    fun `webp is passed through untouched since the JDK cannot decode it`() {
        val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
        val result = PhotoCompressionService.compress(bytes, "image/webp")
        assertEquals("image/webp", result.contentType)
        assertTrue(bytes.contentEquals(result.bytes))
    }

    @Test
    fun `bytes that are not a readable image at all are passed through rather than crashing`() {
        val garbage = ByteArray(64) { it.toByte() }
        val result = PhotoCompressionService.compress(garbage, "image/jpeg")
        assertTrue(garbage.contentEquals(result.bytes))
    }

    private fun writeBigEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
