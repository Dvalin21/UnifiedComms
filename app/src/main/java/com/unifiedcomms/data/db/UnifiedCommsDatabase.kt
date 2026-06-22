package com.unifiedcomms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.*
import com.unifiedcomms.data.db.dao.*
import com.unifiedcomms.data.model.*

@Database(
    entities = [
        Account::class,
        Email::class,
        CalendarEvent::class,
        Calendar::class,
        Task::class,
        TaskList::class,
        Message::class,
        Conversation::class,
        UnifiedContact::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(
    DateTimeConverter::class,
    StringListConverter::class,
    MapConverter::class,
    AttachmentListConverter::class,
    EventAttachmentListConverter::class,
    AttendeeListConverter::class,
    RecurrenceExceptionListConverter::class,
    EventColorConverter::class,
    EventDateTimeConverter::class,
    RecurrenceRuleConverter::class,
    EmailRecipientsConverter::class,
    EmailAddressConverter::class,
    SystemLabelsConverter::class,
    EmailFlagsConverter::class,
    TaskDateTimeConverter::class,
    MessageAttachmentListConverter::class,
    GeoLocationConverter::class,
    ServerConfigConverter::class,
    AuthConfigConverter::class,
    SyncConfigConverter::class,
    UIConfigConverter::class,
    EventAttendeeConverter::class,
    ConferenceDataConverter::class,
    TaskAssigneeConverter::class,
    TaskAttachmentListConverter::class,
    ConversationSettingsConverter::class,
    EventReminderListConverter::class
)
abstract class UnifiedCommsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun emailDao(): EmailDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun calendarDao(): CalendarDao
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: UnifiedCommsDatabase? = null

        fun getInstance(context: Context): UnifiedCommsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UnifiedCommsDatabase::class.java,
                    "unifiedcomms.db"
                )
                    .enableMultiInstanceInvalidation()
                    .fallbackToDestructiveMigration() // For development - remove in production
                    .addCallback(object : Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Create indexes for performance
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_emails_thread ON emails(threadId)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_start ON calendar_events(startAt)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due ON tasks(dueAt)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversationId)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_name ON contacts(displayName)")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}