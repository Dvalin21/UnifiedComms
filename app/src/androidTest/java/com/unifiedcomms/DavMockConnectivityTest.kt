package com.unifiedcomms

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Confirms the emulator can reach the host DAV mock via `adb reverse tcp:8088`. */
class DavMockConnectivityTest {
    @Test
    fun emulatorReachesMock(): Unit = runBlocking {
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
        val code = withContext(Dispatchers.IO) {
            try {
                val resp = http.newCall(
                    Request.Builder().url("http://127.0.0.1:8088/")
                        .header("Authorization", "Basic " + android.util.Base64.encodeToString("tester:secret".toByteArray(), android.util.Base64.NO_WRAP))
                        .method("PROPFIND", okhttp3.RequestBody.create(null, "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:current-user-principal/></D:prop></D:propfind>"))
                        .header("Depth", "0").build()
                ).execute()
                val body = resp.body?.string()?.take(200) ?: ""
                android.util.Log.i("DAVMOCKPROBE", "code=${resp.code} body=$body")
                resp.code
            } catch (e: Exception) {
                android.util.Log.i("DAVMOCKPROBE", "EXCEPTION ${e.message}")
                -1
            }
        }
        assertTrue("Emulator could not reach DAV mock (adb reverse missing?): code=$code", code == 207)
    }
}
