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
     * Example shape for a real bump when schema changes:
     *
     * val MIGRATION_2_3 = object : Migration(2, 3) {
     *     override fun migrate(db: SupportSQLiteDatabase) {
     *         db.execSQL("ALTER TABLE emails ADD COLUMN newField TEXT")
     *     }
     * }
     */
}
