package com.unifiedcomms.sync.accounts

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.annotation.Nullable

class UnifiedCommsContentProvider : ContentProvider() {

    companion object {
        private const val AUTHORITY = "com.unifiedcomms.provider"
        private const val EMAILS = "emails"
        private const val EVENTS = "events"
        private const val TASKS = "tasks"
        private const val CONVERSATIONS = "conversations"
        private const val MESSAGES = "messages"
        private const val CONTACTS = "contacts"

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, EMAILS, 1)
            addURI(AUTHORITY, EMAILS + "/#", 2)
            addURI(AUTHORITY, EVENTS, 3)
            addURI(AUTHORITY, EVENTS + "/#", 4)
            addURI(AUTHORITY, TASKS, 5)
            addURI(AUTHORITY, TASKS + "/#", 6)
            addURI(AUTHORITY, CONVERSATIONS, 7)
            addURI(AUTHORITY, CONVERSATIONS + "/#", 8)
            addURI(AUTHORITY, MESSAGES, 9)
            addURI(AUTHORITY, MESSAGES + "/#", 10)
            addURI(AUTHORITY, CONTACTS, 11)
            addURI(AUTHORITY, CONTACTS + "/#", 12)
        }
    }

    override fun onCreate(): Boolean = true

    @Nullable
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Return data from Room database
        // This would be implemented with actual database queries
        return MatrixCursor(projection ?: arrayOf("_id"))
    }

    @Nullable
    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            1, 3, 5, 7, 9, 11 -> "vnd.android.cursor.dir/vnd.unifiedcomms.${uri.lastPathSegment}"
            2, 4, 6, 8, 10, 12 -> "vnd.android.cursor.item/vnd.unifiedcomms.${uri.lastPathSegment}"
            else -> null
        }
    }

    @Nullable
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Insert into Room database
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }
}