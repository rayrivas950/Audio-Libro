package com.example.audiobook.core.security

import java.io.InputStream
import javax.inject.Inject

class FileValidator @Inject constructor() {

    companion object {
        // PDF starts with "%PDF" (Hex: 25 50 44 46)
        private val PDF_HEADER = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        
        // EPUB (Zip) starts with "PK" (Hex: 50 4B)
        // Strictly speaking EPUBs are Zips, but we can check mimetype later too.
        // For now, checking for Zip signature is a good first line of defense against non-containers.
        private val ZIP_HEADER = byteArrayOf(0x50, 0x4B)
    }

    enum class FileType {
        PDF,
        EPUB,
        UNKNOWN
    }

    /**
     * Reads the first few bytes of the stream to determine the file type.
     * Does NOT consume the stream (resets it if supported, otherwise caller must handle new stream).
     * Note: InputStream must support marking.
     */
    fun validateFileType(inputStream: InputStream): FileType {
        if (!inputStream.markSupported()) {
            // If we can't rewind, we can't safely peek. 
            // In a real app, we might wrap this in a BufferedInputStream before passing it here.
            return FileType.UNKNOWN
        }

        val buffer = ByteArray(4)
        try {
            inputStream.mark(4)
            val bytesRead = inputStream.read(buffer)
            inputStream.reset() // Rewind so the parser can read it later

            if (bytesRead < 2) return FileType.UNKNOWN

            if (startsWith(buffer, PDF_HEADER)) {
                return FileType.PDF
            }
            
            if (startsWith(buffer, ZIP_HEADER)) {
                // EPUBs are ZIPs. We could do a deeper check here to look for 'mimetype' file inside,
                // but for a quick security check, verifying it's a valid ZIP structure is a good start.
                return FileType.EPUB
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return FileType.UNKNOWN
        }

        return FileType.UNKNOWN
    }

    private fun startsWith(buffer: ByteArray, header: ByteArray): Boolean {
        if (buffer.size < header.size) return false
        for (i in header.indices) {
            if (buffer[i] != header[i]) return false
        }
        return true
    }
}
