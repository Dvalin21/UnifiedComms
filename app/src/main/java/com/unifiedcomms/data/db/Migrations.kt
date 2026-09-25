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
                "CREATE UNIQUE INDEX IF NOT EXISTS index_emails_accountId_folder_imapUid " +
                    "ON emails(accountId, folder, imapUid)"
            )
            db.execSQL("DROP INDEX IF EXISTS idx_emails_account_folder_imapuid")
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
     * Adds the parsed calendar invite (TEXT/JSON via InviteMessageConverter) to emails
     * so invites can be rendered as a card with Accept/Decline/Add actions.
     * Idempotent: guards every ALTER so a partially-applied migration can re-run.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.columnExists("emails", "invite")) {
                db.execSQL("ALTER TABLE emails ADD COLUMN invite TEXT")
            }
            db.execSQL("DROP INDEX IF EXISTS idx_emails_thread")
        }
    }


    /**
     * Chat feature removed. The `conversations` table is chat-only; drop it.
     * `messages` table is retained for search.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE messages_new (
                    id TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    senderId TEXT NOT NULL,
                    recipientId TEXT NOT NULL,
                    content TEXT NOT NULL,
                    messageType TEXT NOT NULL,
                    status TEXT NOT NULL,
                    replyToId TEXT,
                    forwardFromId TEXT,
                    metadata TEXT NOT NULL,
                    isEncrypted INTEGER NOT NULL,
                    encryptionKeyId TEXT,
                    sentAt INTEGER NOT NULL,
                    deliveredAt INTEGER,
                    readAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    isLocalOnly INTEGER NOT NULL,
                    needsSync INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO messages_new (
                    id, conversationId, senderId, recipientId, content,
                    messageType, status, replyToId, forwardFromId, metadata,
                    isEncrypted, encryptionKeyId, sentAt, deliveredAt, readAt,
                    createdAt, isLocalOnly, needsSync
                )
                SELECT
                    id, conversationId, senderId, recipientId, content,
                    messageType, status, replyToId, forwardFromId, metadata,
                    isEncrypted, encryptionKeyId, sentAt, deliveredAt, readAt,
                    createdAt, isLocalOnly, needsSync
                FROM messages
                """.trimIndent()
            )
            db.execSQL("DROP TABLE messages")
            db.execSQL("ALTER TABLE messages_new RENAME TO messages")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId_sentAt ON messages(conversationId, sentAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_senderId ON messages(senderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_recipientId ON messages(recipientId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_status ON messages(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_messageType ON messages(messageType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_content ON messages(content)")
            db.execSQL("DROP TABLE IF EXISTS conversations")
        }
    }

    /** Persist the exact CalDAV item href returned by the server. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.columnExists("calendar_events", "serverHref")) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN serverHref TEXT")
            }
        }
    }

    /** Persist the exact CalDAV VTODO item href returned by the server. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.columnExists("tasks", "serverHref")) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN serverHref TEXT")
            }
        }
    }

    /**
     * Re-fetch calendar resources after fixing RFC5545 UNTIL parsing. Older rows
     * could contain a recurrence rule with a missing UNTIL boundary, which made
     * expired series render forever and duplicate newer replacements.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE calendar_events SET etag = NULL " +
                    "WHERE serverHref IS NOT NULL OR calendarId LIKE '%/%'"
            )
        }
    }

    /** Apply the recurrence cache refresh to databases that already reached version 8. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE calendar_events SET etag = NULL " +
                    "WHERE (serverHref IS NOT NULL OR calendarId LIKE '%/%') " +
                    "AND isLocalOnly = 0 AND needsSync = 0"
            )
            // Rows upgraded from schema 2/3 may also have a zeroed epoch mirror.
            // Refresh only server-backed, non-pending rows; pending local edits keep
            // their ETag and are pushed safely by the next sync.
            db.execSQL(
                "UPDATE tasks SET etag = NULL " +
                    "WHERE isLocalOnly = 0 AND needsSync = 0"
            )
        }
    }
}

/** True if [table] already has a column named [column]. Used to make migrations idempotent. */
private fun SupportSQLiteDatabase.columnExists(table: String, column: String): Boolean {
    return runCatching {
        query("PRAGMA table_info($table)", emptyArray()).use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx).equals(column, ignoreCase = true)) return@use true
            }
            false
        }
    }.getOrDefault(false)
}
