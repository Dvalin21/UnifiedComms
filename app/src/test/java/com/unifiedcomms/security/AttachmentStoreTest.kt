package com.unifiedcomms.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentStoreTest {
    private fun tmpRoot(): File = File(System.getProperty("java.io.tmpdir"), "uc-attstore-${System.nanoTime()}")

    @Test
    fun writeKeepsOnlyTheNewestFile() {
        val root = tmpRoot()
        val store = AttachmentStore.forApp(root)

        val first = store.write("a1_report.pdf", "first".toByteArray())
        val second = store.write("a2_notes.txt", "second".toByteArray())

        assertTrue("first file must be gone after the second write", !first.exists())
        assertTrue("newest file must exist", second.exists())
        assertEquals("second", second.readText())
        assertEquals(1, File(root, AttachmentStore.TMP_DIR).listFiles()!!.size)
        root.deleteRecursively()
    }

    @Test
    fun purgeRemovesPlaintextAndTheLegacyUnencryptedCache() {
        val root = tmpRoot()
        val store = AttachmentStore.forApp(root)
        // Legacy layout written by the old fetchAttachment: unencrypted, never cleaned.
        val legacy = File(root, AttachmentStore.LEGACY_DIR).apply { mkdirs() }
        File(legacy, "a1_song.mp3").writeBytes("music".toByteArray())
        val tmp = File(root, AttachmentStore.TMP_DIR).apply { mkdirs() }
        File(tmp, "leftover.pdf").writeBytes("secret".toByteArray())

        store.purge()

        assertEquals(0, legacy.listFiles()?.size ?: 0)
        assertEquals(0, tmp.listFiles()?.size ?: 0)
        root.deleteRecursively()
    }

    @Test
    fun writeSanitizesTheFileName() {
        val root = tmpRoot()
        val file = AttachmentStore.forApp(root).write("../../etc/passwd", "x".toByteArray())
        // Separators are the thing that must not survive: dots are legal, so the traversal
        // segments collapse to ".._.._etc_passwd" rather than being stripped.
        assertFalse("no path separator may survive sanitization", file.name.contains('/'))
        assertEquals(".._.._etc_passwd", file.name)
        assertTrue("must stay inside the temp dir", file.absolutePath.startsWith(File(root, AttachmentStore.TMP_DIR).absolutePath))
        root.deleteRecursively()
    }
}
