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
        UnifiedContact::class
    ],
    version = 11,
    // ponytail: exporting the schema is what makes migrations verifiable.
    // With this false the room.schemaLocation arg configured below produced
    // nothing, so the 11 migrations could not be checked by any tool.
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
    GeoLocationConverter::class,
    ServerConfigConverter::class,
    AuthConfigConverter::class,
    SyncConfigConverter::class,
    UIConfigConverter::class,
    EventAttendeeConverter::class,
    ConferenceDataConverter::class,
    TaskAssigneeConverter::class,
    TaskAttachmentListConverter::class,
    EventReminderListConverter::class,
    InviteMessageConverter::class
)
abstract class UnifiedCommsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun emailDao(): EmailDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun calendarDao(): CalendarDao
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
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
                    .addMigrations(Migrations.MIGRATION_1_1, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5, Migrations.MIGRATION_5_6, Migrations.MIGRATION_6_7, Migrations.MIGRATION_7_8, Migrations.MIGRATION_8_9, Migrations.MIGRATION_9_10, Migrations.MIGRATION_10_11)
                    .also { builder ->
                        if (com.unifiedcomms.BuildConfig.DEBUG) {
                            builder.fallbackToDestructiveMigration()
                        }
                    }
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}