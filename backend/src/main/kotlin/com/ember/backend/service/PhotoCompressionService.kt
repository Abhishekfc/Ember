package com.ember.backend.service

import com.ember.backend.exception.InvalidFriendRequestException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.min

/** Re-encodes an uploaded photo down to a sane size before it's ever stored, so every future
 * fetch of it — from any screen, any client — pays for a reasonably-sized file rather than
 * whatever the uploader happened to send. Nothing before this stored or served bytes any
 * differently than the client uploaded them; a handful of this project's own local test images
 * are multi-MB, multi-thousand-pixel PNGs, and nothing stopped a real client from doing the same
 * by accident (or on purpose) — there was no server-side backstop at all.
 *
 * Deliberately conservative about when it re-encodes: an already-reasonably-sized JPEG (which is
 * what this app's own camera capture path already produces — see CameraViewModel's own JPEG
 * compression before upload) passes through untouched rather than paying a second lossy
 * generation for no benefit. PNG photographic content always gets re-encoded to JPEG regardless
 * of size — PNG's lossless encoding is enormously wasteful for a photo compared to a
 * visually-indistinguishable high-quality JPEG. WebP passes through unchanged: the JDK's ImageIO
 * has no built-in WebP reader, and this app's own client never actually produces WebP (allowed
 * defensively in PhotoService, not something anything here currently sends).
 */
object PhotoCompressionService {

    /** Long enough that no realistic phone screen ever needs more — this is about eliminating
     * pointless bloat (a several-thousand-pixel-wide source photo displayed on a ~1080px screen),
     * not about visibly reducing quality for anyone actually looking at the photo. */
    private const val MAX_DIMENSION = 2000
    private const val JPEG_QUALITY = 0.9f

    /**
     * Hard ceiling on how many pixels an upload is allowed to decode to, checked from the file's
     * *header* before any pixel data is read.
     *
     * Compressed image formats have an essentially unbounded compression ratio for synthetic
     * content: a 25MB PNG (the multipart limit) of flat colour decodes to hundreds of millions of
     * pixels, and `ImageIO.read` allocates the whole decoded raster up front at 4 bytes each — so
     * a single well-crafted upload, well inside every size limit already enforced, could ask the
     * JVM for multiple gigabytes and OOM the container. That's a one-request kill of the whole
     * server, from any authenticated account, and nothing before this looked at dimensions at all.
     *
     * 50 megapixels is far above any real phone camera (a 48MP sensor shooting full-res lands
     * around 48MP, and this app's own client downscales long before upload), so no genuine photo
     * is ever rejected by it — while capping the decode at roughly 200MB of raster, which the heap
     * can absorb.
     */
    private const val MAX_PIXELS = 50_000_000L

    data class CompressedImage(val bytes: ByteArray, val contentType: String)

    fun compress(bytes: ByteArray, contentType: String): CompressedImage {
        if (contentType == "image/webp") return CompressedImage(bytes, contentType)

        rejectIfOversizedRaster(bytes)

        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: return CompressedImage(bytes, contentType)
        val needsResize = image.width > MAX_DIMENSION || image.height > MAX_DIMENSION
        if (contentType == "image/jpeg" && !needsResize) return CompressedImage(bytes, contentType)

        val target = if (needsResize) scaleDown(image) else image
        return CompressedImage(encodeJpeg(target), "image/jpeg")
    }

    /**
     * Reads only the image header to learn its dimensions and rejects anything over [MAX_PIXELS]
     * *before* [ImageIO.read] would allocate the full raster — see [MAX_PIXELS] for the attack
     * this closes.
     *
     * `ImageReader.getWidth/getHeight` parse the format's size fields and nothing else, so this
     * costs a few bytes of I/O regardless of how large the file claims to be. A file whose header
     * can't be parsed at all is left alone rather than rejected: [compress] already treats an
     * undecodable image as "pass through untouched", and this must not turn that into a hard
     * failure for a format the JDK simply has no reader for (WebP is already short-circuited
     * above, but this keeps the same tolerance for anything else).
     */
    private fun rejectIfOversizedRaster(bytes: ByteArray) {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return
        stream.use {
            val readers = ImageIO.getImageReaders(it)
            if (!readers.hasNext()) return
            val reader = readers.next()
            try {
                reader.input = it
                val pixels = reader.getWidth(0).toLong() * reader.getHeight(0).toLong()
                if (pixels > MAX_PIXELS) {
                    throw InvalidFriendRequestException("That image is too large to process")
                }
            } catch (ex: InvalidFriendRequestException) {
                throw ex
            } catch (ex: Exception) {
                // Unreadable header — same tolerance as an unreadable image above.
            } finally {
                reader.dispose()
            }
        }
    }

    private fun scaleDown(image: BufferedImage): BufferedImage {
        val scale = min(MAX_DIMENSION.toDouble() / image.width, MAX_DIMENSION.toDouble() / image.height)
        val targetWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (image.height * scale).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null)
        g.dispose()
        return scaled
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        // A decoded PNG is usually ARGB (has an alpha channel) — JPEG has no alpha channel at
        // all, so writing one directly either fails or silently corrupts colors depending on the
        // writer. Flatten onto an opaque white canvas first; already-opaque input (the common
        // case post-resize) passes through this as a no-op copy.
        val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) {
            image
        } else {
            val flattened = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g = flattened.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, image.width, image.height)
            g.drawImage(image, 0, 0, null)
            g.dispose()
            flattened
        }

        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        val output = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(output).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(rgb, null, null), params)
        }
        writer.dispose()
        return output.toByteArray()
    }
}
