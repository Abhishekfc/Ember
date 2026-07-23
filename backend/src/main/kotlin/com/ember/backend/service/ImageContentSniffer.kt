package com.ember.backend.service

/** Sniffs a file's actual byte header rather than trusting a client-declared multipart
 * Content-Type, which any raw API call (curl/Postman, bypassing the app entirely) can set to
 * whatever it likes regardless of the real bytes — that declared header used to be the *only*
 * check before storing and serving a file back as if it were a validated photo. */
object ImageContentSniffer {

    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Returns the detected content type ("image/jpeg", "image/png", "image/webp"), or null if
     * the bytes don't match any recognized image format's magic header. */
    fun detect(bytes: ByteArray): String? = when {
        startsWith(bytes, JPEG_MAGIC) -> "image/jpeg"
        startsWith(bytes, PNG_MAGIC) -> "image/png"
        isWebp(bytes) -> "image/webp"
        else -> null
    }

    private fun startsWith(bytes: ByteArray, magic: ByteArray): Boolean {
        if (bytes.size < magic.size) return false
        for (i in magic.indices) if (bytes[i] != magic[i]) return false
        return true
    }

    private fun isWebp(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val isRiff = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        val isWebp = bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        return isRiff && isWebp
    }
}
