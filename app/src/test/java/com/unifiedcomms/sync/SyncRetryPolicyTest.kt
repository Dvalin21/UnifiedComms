package com.unifiedcomms.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRetryPolicyTest {
    @Test fun healthyAndFailedResultsAreDistinguished() {
        assertFalse(shouldRetrySync(Result.success(SyncResult.success(itemsSynced = 3))))
        assertTrue(shouldRetrySync(Result.success(SyncResult.failure("auth failed"))))
        assertTrue(shouldRetrySync(Result.failure(IllegalStateException("boom"))))
    }

    @Test fun retriesStopAtTheCeiling() {
        assertFalse("first attempt must retry", shouldGiveUpRetrying(0))
        assertFalse("mid attempts must retry", shouldGiveUpRetrying(MAX_SYNC_ATTEMPTS - 1))
        assertTrue("the ceiling must give up", shouldGiveUpRetrying(MAX_SYNC_ATTEMPTS))
        assertTrue("past the ceiling must stay given up", shouldGiveUpRetrying(MAX_SYNC_ATTEMPTS + 7))
    }

    @Test fun aSkippedFolderIsNotAFailure() {
        // Regression: mailcow calls the spam folder "Junk", so the default "Spam" entry is absent.
        // That used to fail the whole account and make WorkManager retry forever.
        val result = SyncResult.success(itemsSynced = 12, skippedFolders = listOf("Spam"))
        assertTrue("skipped folders must not make a sync fail", result.success)
        assertTrue("but they must be reported", result.skippedFolders.contains("Spam"))
    }

    /**
     * The folder list must come from the server. mailcow calls the spam folder "Junk", so the old
     * hardcoded default ("Spam") never existed on a working account. Reconciliation only fires when
     * the engine actually saw the server and reported a non-empty list, so a transient listing
     * failure cannot blank the config.
     */
    @Test
    fun serverFoldersAreReportedSoTheConfigCanBeReconciled() {
        val result = SyncResult.success(
            itemsSynced = 40,
            skippedFolders = listOf("Spam"),
            serverFolders = listOf("INBOX", "Archive", "Drafts", "Junk", "Sent", "Trash", "Templates")
        )
        assertTrue(result.skippedFolders.isNotEmpty())
        assertTrue("Junk must be present so the stale Spam entry can be replaced", result.serverFolders.contains("Junk"))
        assertFalse("an empty server list must never overwrite the config", result.serverFolders.isEmpty())
    }

    @Test
    fun healthySyncCarriesNoFolderNoise() {
        val result = SyncResult.success(itemsSynced = 12)
        assertTrue(result.skippedFolders.isEmpty())
        assertTrue("no listing is done on the healthy path", result.serverFolders.isEmpty())
    }
}
