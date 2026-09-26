package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Attachment
import java.io.ByteArrayInputStream
import javax.mail.internet.MimeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression cover for the disagreement between the two MIME walks. Extraction parses the raw
 * message bytes with boundaryAwareMultipart; the on-demand fetch used to re-parse
 * `part.inputStream` of an already-parsed MimeMessage. On a forward (root multipart/alternative
 * wrapping message/rfc822) that produced a broken tree, matched nothing, and returned null — so an
 * attachment listed in the UI silently refused to open.
 *
 * Note on sizes: JavaMail reports `Part.getSize()` as the transfer-encoded length for these parts
 * (a 16-char base64 body reports 16), which is also what extraction stored in `sizeBytes`. The
 * expectations below are verified against that behaviour, not guessed.
 */
class FindAttachmentPartTest {
    // MIME on the wire is CRLF-terminated; MimeMultipart's boundary splitter is strict about it,
    // so normalise the triple-quoted fixtures before handing them to JavaMail.
    private fun crlf(raw: String) = raw.replace("\r\n", "\n").replace("\n", "\r\n")
    private fun parse(raw: String) = MimeMessage(null, ByteArrayInputStream(crlf(raw).toByteArray(Charsets.ISO_8859_1)))
    private fun parseRaw(raw: String) = crlf(raw).toByteArray(Charsets.ISO_8859_1)

    /**
     * The entry point the engine actually uses. Header-driven `findAttachmentPart` is kept for the
     * malformed-header case covered separately below.
     */
    private fun rawFind(raw: ByteArray, target: Attachment) = findAttachmentPartInRawMessage(raw, target)

    private val forwardWithPdf = """
        MIME-Version: 1.0
        Content-Type: multipart/alternative; boundary="outer"

        --outer
        Content-Type: text/plain; charset=utf-8

        hi
        --outer
        Content-Type: message/rfc822
        Content-Disposition: inline

        MIME-Version: 1.0
        From: sender@example.com
        To: me@example.com
        Subject: fwd
        Content-Type: multipart/mixed; boundary="inner"

        --inner
        Content-Type: text/plain; charset=utf-8

        orig
        --inner
        Content-Type: application/pdf
        Content-Disposition: attachment; filename="PROGRESS 1.pdf"
        Content-Transfer-Encoding: base64

        JVBERi0xLjQK
        --inner--
        --outer--
    """.trimIndent()

    @Test
    fun findsAttachmentInsideForwardedMessage() {
        val target = Attachment(id = "a1", fileName = "PROGRESS 1.pdf", mimeType = "application/pdf", sizeBytes = 12L)
        val found = rawFind(parseRaw(forwardWithPdf), target)
        assertNotNull("attachment inside a forwarded message must be found", found)
        assertEquals("PROGRESS 1.pdf", found!!.fileName)
    }

    @Test
    fun matchesByContentIdWhenTheFileNameChanged() {
        // Written out in full: patching headers inside a MIME blob breaks the header block.
        val renamed = """
            MIME-Version: 1.0
            Content-Type: multipart/alternative; boundary="outer"

            --outer
            Content-Type: text/plain; charset=utf-8

            hi
            --outer
            Content-Type: message/rfc822

            MIME-Version: 1.0
            From: sender@example.com
            To: me@example.com
            Subject: fwd
            Content-Type: multipart/mixed; boundary="inner"

            --inner
            Content-Type: text/plain; charset=utf-8

            orig
            --inner
            Content-Type: application/pdf
            Content-ID: <cid-42>
            Content-Disposition: attachment; filename="renamed.pdf"
            Content-Transfer-Encoding: base64

            JVBERi0xLjQ=
            --inner--
            --outer--
        """.trimIndent()
        val target = Attachment(id = "a2", fileName = "old-name.pdf", mimeType = "application/pdf", sizeBytes = 12L, contentId = "cid-42")
        val found = rawFind(parseRaw(renamed), target)
        assertNotNull("contentId must match when the filename no longer agrees", found)
        assertEquals("renamed.pdf", found!!.fileName)
    }

    @Test
    fun matchesNamelessInlinePartByStoredSize() {
        val inlineNoName = """
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary="b"

            --b
            Content-Type: text/plain; charset=utf-8

            body
            --b
            Content-Type: image/png
            Content-Disposition: inline
            Content-Transfer-Encoding: base64

            aGVsbG8gd29ybGQ=
            --b--
        """.trimIndent()
        // base64 body is 16 chars, and that is the size extraction would have stored.
        val target = Attachment(id = "a3", fileName = "attachment", mimeType = "image/png", sizeBytes = 16L)
        val found = rawFind(parseRaw(inlineNoName), target)
        assertNotNull("a part with no filename must still resolve by size", found)
        assertEquals("image/png", found!!.contentType)
    }

    @Test
    fun ambiguousSizeDoesNotMatchTheWrongPart() {
        val collision = """
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary="b"

            --b
            Content-Type: text/plain; charset=utf-8

            123456789012
            --b
            Content-Type: image/png
            Content-Disposition: inline
            Content-Transfer-Encoding: base64

            MTIzNDU2Nzg5
            --b--
        """.trimIndent()
        // Both parts report 12 bytes and neither has a filename. Guessing would hand back the
        // body text instead of the image, so an ambiguous size must match nothing.
        val target = Attachment(id = "a5", fileName = "attachment", mimeType = "image/png", sizeBytes = 12L)
        assertEquals(null, rawFind(parseRaw(collision), target))
    }

    @Test
    fun unrelatedMessageDoesNotMatch() {
        val target = Attachment(id = "a4", fileName = "absent.pdf", mimeType = "application/pdf", sizeBytes = 999L)
        assertEquals(null, rawFind(parseRaw(forwardWithPdf), target))
    }

    /**
     * The real-world shape, captured from the failing message: the top-level header declares
     * `multipart/alternative` with a boundary that never appears in the body, while the body is a
     * mixed blob under a different boundary. A header-driven parse therefore sees only the two
     * body alternatives and the forwarded PDF is invisible — which is why the fetch returned null
     * while the attachment had been listed all along.
     */
    @Test
    fun findsAttachmentWhenTheHeaderBoundaryIsALie() {
        val lying = """
            MIME-Version: 1.0
            Content-Type: multipart/alternative; boundary="000000000000892f94065b4c18dd"

            --000000000000892f94065b4c18dd
            Content-Type: text/plain; charset="UTF-8"

            see attached
            --000000000000892f94065b4c18dd
            Content-Type: multipart/mixed; boundary="realboundary"

            --realboundary
            Content-Type: text/plain; charset="UTF-8"

            original body
            --realboundary
            Content-Type: application/pdf; name="PROGRESS 1.pdf"
            Content-Disposition: attachment; filename="PROGRESS 1.pdf"
            Content-Transfer-Encoding: base64

            JVBERi0xLjQK
            --realboundary--
            --000000000000892f94065b4c18dd
        """.trimIndent()
        val target = Attachment(id = "a6", fileName = "PROGRESS 1.pdf", mimeType = "application/pdf", sizeBytes = 12L)
        val found = findAttachmentPartInRawMessage(parseRaw(lying), target)
        assertNotNull("byte-level parse must reach the attachment the header hides", found)
        assertEquals("PROGRESS 1.pdf", found!!.fileName)
    }
}
