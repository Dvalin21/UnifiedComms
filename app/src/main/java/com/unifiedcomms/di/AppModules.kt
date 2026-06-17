package com.unifiedcomms.di

import android.content.Context
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.db.dao.*
import com.unifiedcomms.data.repository.*
import com.unifiedcomms.sync.*
import com.unifiedcomms.messaging.MessagingService
import com.unifiedcomms.push.PushManager
import com.unifiedcomms.security.CryptoManager
import com.unifiedcomms.security.BiometricManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@qualifiers.ApplicationContext context: Context): UnifiedCommsDatabase {
        return UnifiedCommsDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAccountDao(db: UnifiedCommsDatabase): AccountDao = db.accountDao()

    @Provides
    @Singleton
    fun provideEmailDao(db: UnifiedCommsDatabase): EmailDao = db.emailDao()

    @Provides
    @Singleton
    fun provideCalendarEventDao(db: UnifiedCommsDatabase): CalendarEventDao = db.calendarEventDao()

    @Provides
    @Singleton
    fun provideCalendarDao(db: UnifiedCommsDatabase): CalendarDao = db.calendarDao()

    @Provides
    @Singleton
    fun provideTaskDao(db: UnifiedCommsDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideTaskListDao(db: UnifiedCommsDatabase): TaskListDao = db.taskListDao()

    @Provides
    @Singleton
    fun provideMessageDao(db: UnifiedCommsDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideConversationDao(db: UnifiedCommsDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideContactDao(db: UnifiedCommsDatabase): ContactDao = db.contactDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAccountRepository(dao: AccountDao): AccountRepository = AccountRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideEmailRepository(dao: EmailDao): EmailRepository = EmailRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideCalendarRepository(eventDao: CalendarEventDao, calDao: CalendarDao): CalendarRepository =
        CalendarRepositoryImpl(eventDao, calDao)

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao, listDao: TaskListDao): TaskRepository =
        TaskRepositoryImpl(taskDao, listDao)

    @Provides
    @Singleton
    fun provideMessagingRepository(msgDao: MessageDao, convDao: ConversationDao): MessagingRepository =
        MessagingRepositoryImpl(msgDao, convDao)

    @Provides
    @Singleton
    fun provideContactRepository(dao: ContactDao): ContactRepository = ContactRepositoryImpl(dao)
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideSyncManager(
        emailSync: EmailSyncEngine,
        calendarSync: CalendarSyncEngine,
        taskSync: TaskSyncEngine,
        contactSync: ContactSyncEngine
    ): SyncManager = SyncManager(emailSync, calendarSync, taskSync, contactSync)

    @Provides
    @Singleton
    fun provideEmailSyncEngine(
        repo: EmailRepository,
        accountRepo: AccountRepository,
        crypto: CryptoManager
    ): EmailSyncEngine = EmailSyncEngineImpl(repo, accountRepo, crypto)

    @Provides
    @Singleton
    fun provideCalendarSyncEngine(
        repo: CalendarRepository,
        accountRepo: AccountRepository,
        crypto: CryptoManager
    ): CalendarSyncEngine = CalendarSyncEngineImpl(repo, accountRepo, crypto)

    @Provides
    @Singleton
    fun provideTaskSyncEngine(
        repo: TaskRepository,
        accountRepo: AccountRepository,
        crypto: CryptoManager
    ): TaskSyncEngine = TaskSyncEngineImpl(repo, accountRepo, crypto)

    @Provides
    @Singleton
    fun provideContactSyncEngine(
        repo: ContactRepository,
        accountRepo: AccountRepository,
        crypto: CryptoManager
    ): ContactSyncEngine = ContactSyncEngineImpl(repo, accountRepo, crypto)
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideCryptoManager(@qualifiers.ApplicationContext context: Context): CryptoManager =
        CryptoManagerImpl(context)

    @Provides
    @Singleton
    fun provideBiometricManager(@qualifiers.ApplicationContext context: Context): BiometricManager =
        BiometricManagerImpl(context)
}

@Module
@InstallIn(SingletonComponent::class)
object MessagingModule {
    @Provides
    @Singleton
    fun provideMessagingService(
        repo: MessagingRepository,
        pushManager: PushManager,
        crypto: CryptoManager
    ): MessagingService = MessagingService(repo, pushManager, crypto)

    @Provides
    @Singleton
    fun providePushManager(
        @qualifiers.ApplicationContext context: Context,
        crypto: CryptoManager
    ): PushManager = PushManagerImpl(context, crypto)
}