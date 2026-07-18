package com.unifiedcomms.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the RFC 6764 principal-discovery href parser. The previous regex
 * required a non-namespaced `<href>` tag, which DAV XML never produces
 * (`<D:href>` / `<d:href>`), so principal discovery silently returned null and
 * CalDAV/CardDAV fell back to a wrong guessed URL. These tests pin the fixed
 * namespace-agnostic parser against realistic server responses.
 */
class AutodiscoverDavTest {

    // Real Nextcloud/ownCloud-style response: home-set href carries attributes
    // and is namespaced; the response also has its OWN href (the principal).
    private val calendarHomeSetXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:response>
            <d:href>/remote.php/dav/principals/users/keith/</d:href>
          </d:response>
          <d:response>
            <d:propstat>
              <d:prop>
                <c:calendar-home-set>
                  <d:href xmlns:d="DAV:">/remote.php/dav/calendars/keith/</d:href>
                </c:calendar-home-set>
              </d:prop>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    // Lowercase-default-namespace DAV: principal href is wrapped in its property.
    private val principalXml = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:propstat>
              <d:prop>
                <d:current-user-principal>
                  <d:href>/principals/users/keith/</d:href>
                </d:current-user-principal>
              </d:prop>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `calendar-home-set href parsed from namespaced multistatus`() {
        val href = Autodiscover.parsePropHref(calendarHomeSetXml, "calendar-home-set")
        assertEquals("/remote.php/dav/calendars/keith/", href)
    }

    @Test
    fun `current-user-principal href parsed from namespaced response`() {
        val href = Autodiscover.parsePropHref(principalXml, "current-user-principal")
        assertEquals("/principals/users/keith/", href)
    }

    @Test
    fun `does not return the response's own href when property absent`() {
        // No calendar-home-set property here -> must be null, NOT the principal href.
        val href = Autodiscover.parsePropHref(principalXml, "calendar-home-set")
        assertNull(href)
    }

    @Test
    fun `old non-namespaced regex form would have failed on this xml`() {
        // Regression guard: the OLD code used Regex("<href>(.*?)</href>") which does
        // NOT match <d:href>. Prove the old pattern returns nothing on this input.
        val oldPattern = Regex("<href>(.*?)</href>", RegexOption.DOT_MATCHES_ALL)
        val oldMatch = oldPattern.find(calendarHomeSetXml)
        assertNull("legacy regex must not match namespaced DAV hrefs", oldMatch)
    }
}
