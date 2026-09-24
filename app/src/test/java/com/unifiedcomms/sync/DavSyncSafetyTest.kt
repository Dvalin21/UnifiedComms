package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DavSyncSafetyTest {

    @Test
    fun `principal multistatus parser keeps nested href`() {
        val parsed = CalDAVClient("http://localhost/dav/", "user", "password", OkHttpClient()).safeParseMultistatus(
            """<D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/</D:href><D:propstat><D:prop><D:current-user-principal><D:href>/principal/</D:href></D:current-user-principal></D:prop></D:propstat></D:response></D:multistatus>"""
        )

        assertTrue("parsed=$parsed", parsed.isNotEmpty())
        assertTrue(parsed.first().props.any { it.localName == "href" && it.text == "/principal/" })
    }

    @Test
    fun `valid empty multistatus is authoritative empty`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setResponseCode(207)
                    .setHeader("Content-Type", "application/xml")
                    .setBody("""<D:multistatus xmlns:D="DAV:"/>""")
            )

            val result = client(server).getETagList("/dav/calendar/")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
        }
    }

    @Test
    fun `task ETag listing filters VTODO with calendar-query`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/calendar/task.ics</D:href><D:propstat><D:prop><D:getetag>"task-etag"</D:getetag></D:prop></D:propstat></D:response></D:multistatus>"""
            ))

            val result = client(server).getTaskETagList("/dav/calendar/")

            assertTrue(result.isSuccess)
            assertEquals("/dav/calendar/task.ics", result.getOrThrow().single().href)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.body.readUtf8().contains("comp-filter name=\"VTODO\""))
        }
    }

    @Test
    fun `HTTP listing failure is not authoritative empty`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(503))

            val result = client(server).getETagList("/dav/calendar/")

            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `malformed listing is not authoritative empty`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setResponseCode(207)
                    .setHeader("Content-Type", "application/xml")
                    .setBody("""<D:multistatus xmlns:D="DAV:">""")
            )

            val result = client(server).getETagList("/dav/calendar/")

            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `calendar listing failure does not delete local events`(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val path = "/dav/calendar/"
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/</D:href><D:propstat><D:prop><D:current-user-principal><D:href>/principal/</D:href></D:current-user-principal></D:prop></D:propstat></D:response></D:multistatus>"""
            ))
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"><D:response><D:href>/principal/</D:href><D:propstat><D:prop><C:calendar-home-set><D:href>$path</D:href></C:calendar-home-set></D:prop></D:propstat></D:response></D:multistatus>"""
            ))
            server.enqueue(
                MockResponse().setResponseCode(207)
                    .setHeader("Content-Type", "application/xml")
                    .setBody(
                        """
                        <D:multistatus xmlns:D="DAV:">
                          <D:response>
                            <D:href>$path</D:href>
                            <D:propstat>
                              <D:prop><D:displayname>Personal</D:displayname><D:resourcetype><D:collection/><D:calendar/></D:resourcetype></D:prop>
                              <D:status>HTTP/1.1 200 OK</D:status>
                            </D:propstat>
                          </D:response>
                        </D:multistatus>
                        """.trimIndent()
                    )
            )
            server.enqueue(MockResponse().setResponseCode(500))
            val account = account(server.url("/dav/").toString())
            val event = CalendarEvent(
                accountId = account.id,
                calendarId = path.trimEnd('/'),
                uid = "keep-event",
                title = "Keep",
                startAt = EventDateTime(dateTime = LocalDateTime.parse("2026-09-23T10:00"), timeZone = "UTC"),
                endAt = EventDateTime(dateTime = LocalDateTime.parse("2026-09-23T11:00"), timeZone = "UTC")
            )
            val repository: CalendarRepository = mock()
            val accountRepository: AccountRepository = mock()
            whenever(repository.getAllEventsForAccount(account.id)).thenReturn(flowOf(listOf(event)))
            val engine = CalendarSyncEngineImpl(repository, accountRepository, cryptoReturning(account.authConfig), TestScope())

            val result = engine.syncAccount(account)

            assertFalse(result.success)
            assertTrue(result.errorMessage?.contains("HTTP 500") == true)
            assertEquals(4, server.requestCount)
            verify(repository, never()).deleteEvent(any())
        }
    }

    @Test
    fun `relative DAV href keeps the collection path`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(200).setBody("BEGIN:VCALENDAR\nEND:VCALENDAR"))

            CalDAVClient(server.url("/dav/collection").toString(), "user", "password", OkHttpClient())
                .fetchItem("", "event.ics")

            assertEquals("/dav/collection/event.ics", server.takeRequest().path)
        }
    }

    @Test
    fun `successful PUT without ETag is still a write success`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(201))

            val etag = client(server).putResource(
                "/dav/calendar/u1.ics",
                "BEGIN:VCALENDAR\nEND:VCALENDAR"
            )

            assertEquals("*", etag)
            assertEquals("PUT", server.takeRequest().method)
        }
    }

    @Test
    fun `failed PUT returns no ETag`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(507))

            val etag = client(server).putResource("/dav/calendar/u1.ics", "BEGIN:VCALENDAR\nEND:VCALENDAR")

            assertEquals(null, etag)
        }
    }

    @Test
    fun `newly pushed task is not deleted by the same sync pass`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:"><D:response><D:href>/dav/</D:href><D:propstat><D:prop><D:current-user-principal><D:href>/principal/</D:href></D:current-user-principal></D:prop></D:propstat></D:response></D:multistatus>"""
            ))
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"><D:response><D:href>/principal/</D:href><D:propstat><D:prop><C:calendar-home-set><D:href>/tasks/</D:href></C:calendar-home-set></D:prop></D:propstat></D:response></D:multistatus>"""
            ))
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"><D:response><D:href>/tasks/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response><D:response><D:href>/tasks/</D:href><D:propstat><D:prop><D:displayname>Tasks</D:displayname><D:resourcetype><D:collection/><D:calendar/></D:resourcetype><C:supported-calendar-component-set><C:comp name="VTODO"/></C:supported-calendar-component-set></D:prop></D:propstat></D:response></D:multistatus>"""
            ))
            server.enqueue(MockResponse().setResponseCode(207).setBody(
                """<D:multistatus xmlns:D="DAV:"/>"""
            ))
            server.enqueue(MockResponse().setResponseCode(201))

            val account = account(server.url("/dav/").toString())
            val local = Task(
                id = "local-task",
                accountId = account.id,
                listId = "local",
                uid = "local-task",
                title = "Pending",
                status = TaskStatus.NEEDS_ACTION,
                isLocalOnly = true,
                needsSync = true
            )
            val repository: TaskRepository = mock()
            var stored = local
            whenever(repository.getNeedingSync(account.id)).thenReturn(listOf(local))
            whenever(repository.getByList(account.id, server.url("/tasks").toString().trimEnd('/')))
                .thenReturn(flow { emit(listOf(stored)) })
            whenever(repository.update(any())).thenAnswer { invocation ->
                stored = invocation.getArgument(0)
                1
            }
            val engine = TaskSyncEngineImpl(repository, mock(), cryptoReturning(account.authConfig), TestScope())

            val result = engine.syncAccount(account)

            assertTrue(result.success)
            assertFalse(stored.isLocalOnly)
            assertFalse(stored.needsSync)
            verify(repository, never()).delete(any())
        }
    }

    @Test
    fun `background retry includes returned sync failure`() {
        assertTrue(shouldRetrySync(Result.success(SyncResult.failure("failed"))))
        assertTrue(shouldRetrySync(Result.failure<SyncResult>(IllegalStateException("failed"))))
        assertFalse(shouldRetrySync(Result.success(SyncResult.success())))
    }

    private fun client(server: MockWebServer): CalDAVClient =
        CalDAVClient(server.url("/dav/").toString(), "user", "password", OkHttpClient())

    private fun account(caldavUrl: String): Account = Account(
        id = "dav-safety",
        name = "DAV Safety",
        email = "test@example.com",
        accountType = AccountType.GENERIC_CALDAV_CARDDAV,
        serverConfig = ServerConfig(caldavUrl = caldavUrl),
        authConfig = AuthConfig.AppPassword("user", "password"),
        syncConfig = SyncConfig.Defaults(),
        uiConfig = UIConfig.Defaults()
    )

    private fun cryptoReturning(authConfig: AuthConfig): CryptoManager {
        val crypto: CryptoManager = mock()
        whenever(crypto.decryptAuthConfig(authConfig)).thenReturn(authConfig)
        return crypto
    }
}
