package com.unifiedcomms.security

import java.io.File

/**
 * Owns attachment bytes on disk.
 *
 * ponytail: attachment files are NOT encrypted at rest, they are never persisted. An external
 * viewer is handed a real file through FileProvider, which cannot read ciphertext, so a plaintext
 * window is unavoidable while the user has a file open. Persisting an encrypted copy would buy
 * nothing for bytes the server can re-serve, and would cost a second key hierarchy to protect it.
 *
 * The rule is therefore:
 *   - bytes live in `cacheDir/attachment_tmp` and nowhere else;
 *   - every write clears the directory first, so at most what the user just opened is on disk;
 *   - every app start clears it too, so a reboot or crash cannot leave plaintext behind.
 *
 * That also retires the old `cacheDir/attachments` directory, which held unencrypted, never-cleaned
 * copies of everything the user had ever opened.
 */
internal class AttachmentStore(private val cacheRoot: File) {

    private val tmpDir: File get() = File(cacheRoot, TMP_DIR)
    private val legacyDir: File get() = File(cacheRoot, LEGACY_DIR)

    /** Stores [bytes] as the only attachment file on disk and returns it. */
    fun write(fileName: String, bytes: ByteArray): File {
        purge()
        tmpDir.mkdirs()
        val file = File(tmpDir, sanitize(fileName))
        file.writeBytes(bytes)
        return file
    }

    /** Removes every plaintext attachment file, including the legacy unencrypted cache. */
    fun purge() {
        listOrEmpty(tmpDir).forEach { deleteRecursively(it) }
        listOrEmpty(legacyDir).forEach { deleteRecursively(it) }
    }

    private fun listOrEmpty(dir: File): List<File> = dir.listFiles()?.toList() ?: emptyList()

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val TMP_DIR = "attachment_tmp"
        const val LEGACY_DIR = "attachments"

        fun forApp(cacheRoot: File): AttachmentStore = AttachmentStore(cacheRoot)
    }
}
