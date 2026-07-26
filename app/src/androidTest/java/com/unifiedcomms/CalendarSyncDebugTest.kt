package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test

class CalendarSyncDebugTest {
    @Test
    fun syncCalendar(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val calendarRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val engine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, this)

        val accounts = accountRepo.getAllActive().first()
        Log.e("CALDBG", "accounts found=${accounts.size}")
        val acc = accounts.firstOrNull { it.accountType == com.unifiedcomms.data.model.AccountType.MAILCOW }
            ?: accounts.firstOrNull()
        if (acc == null) { Log.e("CALDBG", "NO ACCOUNT"); return@runBlocking }
        try {
            val res = engine.syncAccount(acc)
            Log.e("CALDBG", "RESULT success=${res.success} err='${res.errorMessage}' items=${res.itemsSynced}")
            val events = calendarRepo.getAllEventsForAccount(acc.id).first()
            Log.e("CALDBG", "events=${events.size}")
        } catch (e: Exception) {
            Log.e("CALDBG", "EXCEPTION ${e.javaClass.name} '${e.message}'")
            e.printStackTrace()
        }
    }
}
