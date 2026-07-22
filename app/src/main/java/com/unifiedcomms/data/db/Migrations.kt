package com.unifiedcomms.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /**
     * No-op baseline: defines the 1 → 1 path so Room has a verified
     * schema anchor for the freshly exported v1 JSON.
     */
    val MIGRATION_1_1 = object : Migration(1, 1) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No columns changed in this transition.
        }
    }

    /**
     * Adds IMAP UID-based sync state to emails so sequence number changes
     * after reconnect can’t cause duplicates or missed mail (K-9 pattern).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE emails ADD COLUMN uidValidity TEXT")
            db.execSQL("ALTER TABLE emails ADD COLUMN imapUid TEXT")
            // Backfill stable ID from existing uid where possible.
            db.execSQL("UPDATE emails SET imapUid = uid WHERE imapUid IS NULL")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_emails_account_folder_imapuid " +
                    "ON emails(accountId, folder, imapUid)"
            )
        }
    }

    /**
     * Adds startAtMs (Long epoch mirror of startAt) to calendar_events and dueAtMs to tasks
     * so SQL range/date queries work (startAt/dueAt are JSON TEXT via type converters).
     * Backfill from existing rows: startAtMs = 0, dueAtMs = 0 (re-populated on next sync).
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE calendar_events ADD COLUMN startAtMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN dueAtMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * Example shape for a real bump when schema changes:
     *
     * val MIGRATION_3_4 = object : Migration(3, 4) {
     *     override fun migrate(db: SupportSQLiteDatabase) {
     *         db.execSQL("ALTER TABLE emails ADD COLUMN newField TEXT")
     *     }
     * }
     */
}
