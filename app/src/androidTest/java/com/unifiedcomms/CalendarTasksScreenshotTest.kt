package com.unifiedcomms

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.TaskSyncEngineImpl
import com.unifiedcomms.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Visual proof for Calendar + Tasks: seeds a REAL active account in code (correct
 * creds), syncs calendar + tasks to populate Room, launches MainActivity, navigates
 * the tabs, and captures screenshots to /sdcard/Download/ for human review.
 */
class CalendarTasksScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun password(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply live password via: -e password '...'")

    private val user = "testbox@houseofmanns.com"

    private fun shot(name: String) {
        val f = File("/sdcard/Download", name)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(f)
        android.util.Log.e("CALTXSHOT", "shot $name exists=${f.exists()} size=${f.length()}")
    }

    @Test
    fun captureCalendarAndTasks(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())

        com.unifiedcomms.util.PreferencesManager.initialize(app)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)

        val acc = Account(
            id = "cal-${UUID.randomUUID().toString().take(8)}",
            name = "Rend ($user)",
            email = user,
            accountType = AccountType.MAILCOW,
            serverConfig = ServerConfig(
                imapHost = "imap.houseofmanns.com", imapPort = 993, imapUseSsl = true, acceptAllCerts = true,
                smtpHost = "smtp.houseofmanns.com", smtpPort = 587, smtpUseStartTls = true,
                caldavUrl = "https://email.houseofmanns.com/SOGo/dav/$user/Calendar/personal/"
            ),
            authConfig = AuthConfig.AppPassword(user, password()),
            syncConfig = SyncConfig.Defaults().copy(syncEmail = false, syncCalendar = true, syncTasks = true, syncContacts = false),
            uiConfig = UIConfig.Defaults()
        )
        accountRepo.insert(acc)
        val stored = accountRepo.getById(acc.id) ?: acc
        CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, this).syncAccount(stored)
        TaskSyncEngineImpl(taskRepo, accountRepo, crypto, this).syncAccount(stored)

        composeTestRule.waitForIdle()
        Thread.sleep(3000)

        // Calendar (default tab is Inbox=0; navigate to Calendar=1)
        composeTestRule.onNodeWithText("Calendar", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2500)
        shot("calendar_month.png")

        // Switch to Day view
        composeTestRule.onNodeWithText("Day", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        shot("calendar_day.png")

        // Switch to Week view
        composeTestRule.onNodeWithText("Week", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        shot("calendar_week.png")

        // Tasks tab
        composeTestRule.onNodeWithText("Tasks", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        shot("tasks_list.png")

        accountRepo.delete(acc.id)
    }
}
