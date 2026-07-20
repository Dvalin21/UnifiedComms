package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
import com.unifiedcomms.data.db.converters.StringListConverter
import com.unifiedcomms.data.db.converters.ServerConfigConverter
import com.unifiedcomms.data.db.converters.AuthConfigConverter
import com.unifiedcomms.data.db.converters.SyncConfigConverter
import com.unifiedcomms.data.db.converters.UIConfigConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val accountType: AccountType,
    @TypeConverters(ServerConfigConverter::class) val serverConfig: ServerConfig,
    @TypeConverters(AuthConfigConverter::class) val authConfig: AuthConfig,
    @TypeConverters(SyncConfigConverter::class) val syncConfig: SyncConfig,
    @TypeConverters(UIConfigConverter::class) val uiConfig: UIConfig,
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val lastSyncAt: Instant? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false
) {
    companion object {
        fun createGoogle(email: String, name: String = ""): Account {
            return Account(
                name = name.ifBlank { email },
                email = email,
                accountType = AccountType.GOOGLE,
                serverConfig = ServerConfig.GoogleDefaults(),
                authConfig = AuthConfig.Password(username = email, password = ""),
                syncConfig = SyncConfig.Defaults(),
                uiConfig = UIConfig.Defaults()
            )
        }

        fun createMailcow(email: String, serverUrl: String, name: String = ""): Account {
            return Account(
                name = name.ifBlank { "Mailcow ($email)" },
                email = email,
                accountType = AccountType.MAILCOW,
                serverConfig = ServerConfig.MailcowDefaults(serverUrl, email),
                authConfig = AuthConfig.Password(username = email, password = ""),
                syncConfig = SyncConfig.Defaults(),
                uiConfig = UIConfig.Defaults()
            )
        }

        fun createGeneric(email: String, serverConfig: ServerConfig, name: String = ""): Account {
            return Account(
                name = name.ifBlank { email },
                email = email,
                accountType = AccountType.GENERIC_IMAP_SMTP,
                serverConfig = serverConfig,
                authConfig = AuthConfig.Password(username = email, password = ""),
                syncConfig = SyncConfig.Defaults(),
                uiConfig = UIConfig.Defaults()
            )
        }

        fun createExchange(email: String, serverUrl: String, name: String = ""): Account {
            return Account(
                name = name.ifBlank { "Exchange ($email)" },
                email = email,
                accountType = AccountType.EXCHANGE,
                serverConfig = ServerConfig.ExchangeDefaults(serverUrl),
                authConfig = AuthConfig.Password(username = email, password = ""),
                syncConfig = SyncConfig.Defaults(),
                uiConfig = UIConfig.Defaults()
            )
        }
    }
}

@Serializable
enum class AccountType {
    GOOGLE,
    MAILCOW,
    OUTLOOK,
    YAHOO,
    EXCHANGE,
    GENERIC_IMAP_SMTP,
    GENERIC_CALDAV_CARDDAV,
    ICLOUD,
    PROTONMAIL,
    FASTMAIL,
    ZOHO,
    GMX,
    AOL,
    CUSTOM
}

@Serializable
data class ServerConfig(
    val imapHost: String? = null,
    val imapPort: Int = 993,
    val imapUseSsl: Boolean = true,
    val smtpHost: String? = null,
    val smtpPort: Int = 587,
    val smtpUseStartTls: Boolean = true,
    val caldavUrl: String? = null,
    val carddavUrl: String? = null,
    val webdavUrl: String? = null,
    val oauthTokenUrl: String? = null,
    val oauthAuthUrl: String? = null,
    val oauthClientId: String? = null,
    val oauthScopes: List<String> = emptyList(),
    val supportsPush: Boolean = false,
    // ponytail: opt-in escape for self-signed / internal-CA IMAP servers.
    // android-mail 1.6.7 enforces cert hostname verification by default
    // (Angus 1.1.0: "check server identity by default"), so a mismatched
    // or self-signed cert hard-fails store.connect() even with a correct
    // password. Default false = strict (secure). Only true when the user
    // explicitly opts in for their own/internal server.
    val acceptAllCerts: Boolean = false,
    val pushConfig: PushConfig? = null
) {
    companion object {
        fun GoogleDefaults(): ServerConfig = ServerConfig(
            imapHost = "imap.gmail.com",
            imapPort = 993,
            imapUseSsl = true,
            smtpHost = "smtp.gmail.com",
            smtpPort = 587,
            smtpUseStartTls = true,
            caldavUrl = "https://apidata.googleusercontent.com/caldav/v2/",
            carddavUrl = "https://www.googleapis.com/carddav/v1/",
            oauthTokenUrl = "https://oauth2.googleapis.com/token",
            oauthAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth",
            oauthScopes = listOf(
                "https://mail.google.com/",
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/contacts",
                "https://www.googleapis.com/auth/tasks"
            ),
            supportsPush = true,
            pushConfig = PushConfig.Google()
        )

        fun MailcowDefaults(serverUrl: String, email: String = ""): ServerConfig {
            val host = serverUrl.removeSuffix("/").removeSuffix("https://").removeSuffix("http://")
            // Evidence (openssl/curl, 2026-07-20): mailcow serves SOGo over the mailcow WEB FQDN
            // (default `mail.<domain>`; this server uses `email.<domain>` via ADDITIONAL_SERVER_NAME).
            // The email-domain APEX is invalid — `*.houseofmanns.com` wildcard cert excludes the apex
            // and nginx rejects it (TLSV1_ALERT_UNRECOGNIZED_NAME). The DAV host MUST be a subdomain SAN,
            // not the bare domain. `mail.` is the mailcow default and is editable in Advanced if the
            // server uses a different ADDITIONAL_SERVER_NAME. Path is the exact SOGo principal:
            // `https://<webFqdn>/SOGo/dav/<user>/Calendar/personal/`.
            val davBase = "https://mail.$host/SOGo/dav/"
            val caldavUrl = if (email.isNotBlank()) "${davBase}$email/Calendar/personal/" else davBase
            val carddavUrl = if (email.isNotBlank()) "${davBase}$email/Contacts/personal/" else davBase
            return ServerConfig(
                imapHost = "imap.$host",
                imapPort = 993,
                imapUseSsl = true,
                smtpHost = "smtp.$host",
                smtpPort = 587,
                smtpUseStartTls = true,
                caldavUrl = caldavUrl,
                carddavUrl = carddavUrl,
                supportsPush = false
            )
        }

        fun ExchangeDefaults(serverUrl: String): ServerConfig = ServerConfig(
            imapHost = serverUrl.removeSuffix("/").removeSuffix("https://").removeSuffix("http://"),
            imapPort = 993,
            imapUseSsl = true,
            smtpHost = serverUrl.removeSuffix("/").removeSuffix("https://").removeSuffix("http://"),
            smtpPort = 587,
            smtpUseStartTls = true,
            caldavUrl = "$serverUrl/EWS/Exchange.asmx",
            carddavUrl = "$serverUrl/EWS/Exchange.asmx",
            oauthTokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            oauthAuthUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            oauthScopes = listOf(
                "https://outlook.office.com/IMAP.AccessAsUser.All",
                "https://outlook.office.com/SMTP.Send",
                "https://outlook.office.com/Calendars.ReadWrite",
                "https://outlook.office.com/Contacts.ReadWrite",
                "https://outlook.office.com/Tasks.ReadWrite"
            ),
            supportsPush = true,
            pushConfig = PushConfig.Exchange()
        )

        fun OutlookDefaults(): ServerConfig = ExchangeDefaults("outlook.office365.com")

        fun YahooDefaults(): ServerConfig = ServerConfig(
            imapHost = "imap.mail.yahoo.com",
            imapPort = 993,
            imapUseSsl = true,
            smtpHost = "smtp.mail.yahoo.com",
            smtpPort = 587,
            smtpUseStartTls = true,
            caldavUrl = "https://caldav.calendar.yahoo.com/dav/",
            carddavUrl = "https://carddav.addressbook.yahoo.com/dav/",
            supportsPush = false
        )

        fun ICantDefaults(): ServerConfig = ServerConfig(
            imapHost = "imap.mail.me.com",
            imapPort = 993,
            imapUseSsl = true,
            smtpHost = "smtp.mail.me.com",
            smtpPort = 587,
            smtpUseStartTls = true,
            caldavUrl = "https://caldav.icloud.com/",
            carddavUrl = "https://contacts.icloud.com/",
            supportsPush = true,
            pushConfig = PushConfig.ICant()
        )

        fun ICloudDefaults(): ServerConfig = ICantDefaults()
    }
}

@Serializable
data class PushConfig(
    val type: PushType,
    val serverUrl: String? = null,
    val topicPrefix: String? = null,
    val credentials: Map<String, String> = emptyMap()
) {
    companion object {
        fun Google() = PushConfig(PushType.FCM, topicPrefix = "gmail")
        fun Exchange() = PushConfig(PushType.WEBHOOK, topicPrefix = "exchange")
        fun ICant() = PushConfig(PushType.APNS_WEBHOOK, topicPrefix = "icloud")
    }
}

@Serializable
enum class PushType {
    FCM,
    WEBHOOK,
    APNS_WEBHOOK,
    MQTT,
    WEBSOCKET,
    NONE
}

@Serializable
data class AuthConfig(
    val type: AuthType,
    val username: String? = null,
    val passwordEncrypted: String? = null,
    val oauthAccessToken: String? = null,
    val oauthRefreshToken: String? = null,
    @TypeConverters(DateTimeConverter::class) val oauthTokenExpiry: kotlinx.datetime.Instant? = null,
    val clientCertificate: String? = null,
    val clientKey: String? = null
) {
    companion object {
        fun Password(username: String, password: String): AuthConfig = AuthConfig(
            type = AuthType.PASSWORD,
            username = username,
            passwordEncrypted = password // Will be encrypted by CryptoManager
        )

        fun AppPassword(username: String, password: String): AuthConfig = AuthConfig(
            type = AuthType.APP_PASSWORD,
            username = username,
            passwordEncrypted = password // Will be encrypted by CryptoManager
        )

        fun OAuth2(
            accessToken: String? = null,
            refreshToken: String? = null,
            expiry: kotlinx.datetime.Instant? = null
        ): AuthConfig = AuthConfig(
            type = AuthType.OAUTH2,
            oauthAccessToken = accessToken,
            oauthRefreshToken = refreshToken,
            oauthTokenExpiry = expiry
        )

        fun Certificate(cert: String, key: String): AuthConfig = AuthConfig(
            type = AuthType.CERTIFICATE,
            clientCertificate = cert,
            clientKey = key
        )
    }
}

@Serializable
enum class AuthType {
    PASSWORD,
    OAUTH2,
    OAUTH2_DEVICE_CODE,
    CERTIFICATE,
    APP_PASSWORD,
    TOKEN
}

@Serializable
data class SyncConfig(
    val syncEmail: Boolean = true,
    val syncCalendar: Boolean = true,
    val syncContacts: Boolean = true,
    val syncTasks: Boolean = true,
    val syncIntervalMinutes: Int = 15,
    val pushEnabled: Boolean = true,
    val downloadAttachments: Boolean = true,
    val maxAttachmentSizeMb: Int = 25,
    val syncPastDays: Int = 30,
    val syncFutureDays: Int = 365,
    val foldersToSync: List<String> = listOf("INBOX", "Sent", "Drafts", "Trash", "Spam", "Archive"),
    val calendarColors: Map<String, String> = emptyMap(),
    val conflictResolution: ConflictResolution = ConflictResolution.SERVER_WINS,
    val onlyWifi: Boolean = false,
    val requireCharging: Boolean = false
) {
    companion object {
        fun Defaults(): SyncConfig = SyncConfig()
    }
}

@Serializable
enum class ConflictResolution {
    SERVER_WINS,
    CLIENT_WINS,
    MERGE,
    PROMPT
}

@Serializable
data class UIConfig(
    val color: Int = 0xFF2196F3.toInt(), // Material Blue default
    val avatar: String? = null,
    val signature: String? = null,
    val showInUnifiedInbox: Boolean = true,
    val notificationPriority: NotificationPriority = NotificationPriority.HIGH,
    val customRingtone: String? = null,
    val vibrationPattern: LongArray = longArrayOf(0, 250, 250, 250)
) {
    companion object {
        fun Defaults(): UIConfig = UIConfig()
    }
}

@Serializable
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    MAX
}