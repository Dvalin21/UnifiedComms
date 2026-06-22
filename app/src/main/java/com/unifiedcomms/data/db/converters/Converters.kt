package com.unifiedcomms.data.db.converters

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DateTimeConverter {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }
}

class StringListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<String>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<String> = value?.let { json.decodeFromString(it) } ?: emptyList()
}

class MapConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromMap(value: Map<String, String>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toMap(value: String?): Map<String, String> = value?.let { json.decodeFromString(value) } ?: emptyMap()
}

class AttachmentListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.Attachment>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.Attachment> = value?.let { json.decodeFromString(value) } ?: emptyList()
}

class EventAttachmentListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.EventAttachment>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.EventAttachment> = value?.let { json.decodeFromString(value) } ?: emptyList()
}

class AttendeeListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.EventAttendee>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.EventAttendee> = value?.let { json.decodeFromString(value) } ?: emptyList()
}

class RecurrenceExceptionListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.RecurrenceException>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.RecurrenceException> = value?.let { json.decodeFromString(value) } ?: emptyList()
}

class EventColorConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromColor(value: com.unifiedcomms.data.model.EventColor?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toColor(value: String?): com.unifiedcomms.data.model.EventColor = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.EventColor.Default()
}

class EventDateTimeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromDateTime(value: com.unifiedcomms.data.model.EventDateTime?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toDateTime(value: String?): com.unifiedcomms.data.model.EventDateTime = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.EventDateTime()
}

class RecurrenceRuleConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromRule(value: com.unifiedcomms.data.model.RecurrenceRule?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toRule(value: String?): com.unifiedcomms.data.model.RecurrenceRule? = value?.let { json.decodeFromString(value) }
}

class EmailRecipientsConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromRecipients(value: com.unifiedcomms.data.model.EmailRecipients?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toRecipients(value: String?): com.unifiedcomms.data.model.EmailRecipients = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.EmailRecipients()
}

class EmailAddressConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromAddress(value: com.unifiedcomms.data.model.EmailAddress?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toAddress(value: String?): com.unifiedcomms.data.model.EmailAddress = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.EmailAddress(email = "")
}

class SystemLabelsConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromLabels(value: com.unifiedcomms.data.model.SystemLabels?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toLabels(value: String?): com.unifiedcomms.data.model.SystemLabels = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.SystemLabels()
}

class EmailFlagsConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFlags(value: com.unifiedcomms.data.model.EmailFlags?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toFlags(value: String?): com.unifiedcomms.data.model.EmailFlags = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.EmailFlags()
}

class TaskDateTimeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromDateTime(value: com.unifiedcomms.data.model.TaskDateTime?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toDateTime(value: String?): com.unifiedcomms.data.model.TaskDateTime = value?.let { json.decodeFromString(value) } ?: com.unifiedcomms.data.model.TaskDateTime()
}

class MessageAttachmentListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.MessageAttachment>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.MessageAttachment> = value?.let { json.decodeFromString(value) } ?: emptyList()
}

class GeoLocationConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromGeo(value: com.unifiedcomms.data.model.GeoLocation?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toGeo(value: String?): com.unifiedcomms.data.model.GeoLocation? = value?.let { json.decodeFromString(it) }
}

class EventReminderListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.EventReminder>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.EventReminder> = value?.let { json.decodeFromString(it) } ?: emptyList()
}

class InstantListConverter {
    @TypeConverter
    fun fromList(value: List<kotlinx.datetime.Instant>?): String? = value?.let { it.joinToString(",") { i -> i.toEpochMilliseconds().toString() } }

    @TypeConverter
    fun toList(value: String?): List<kotlinx.datetime.Instant> = value?.split(",")?.map { kotlinx.datetime.Instant.fromEpochMilliseconds(it.toLong()) } ?: emptyList()
}

class EventAttendeeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromAttendee(value: com.unifiedcomms.data.model.EventAttendee?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toAttendee(value: String?): com.unifiedcomms.data.model.EventAttendee? = value?.let { json.decodeFromString(it) }
}

class ConferenceDataConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromConference(value: com.unifiedcomms.data.model.ConferenceData?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toConference(value: String?): com.unifiedcomms.data.model.ConferenceData? = value?.let { json.decodeFromString(it) }
}

class TaskAssigneeConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromAssignee(value: com.unifiedcomms.data.model.TaskAssignee?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toAssignee(value: String?): com.unifiedcomms.data.model.TaskAssignee? = value?.let { json.decodeFromString(it) }
}

class TaskAttachmentListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromList(value: List<com.unifiedcomms.data.model.TaskAttachment>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toList(value: String?): List<com.unifiedcomms.data.model.TaskAttachment> = value?.let { json.decodeFromString(it) } ?: emptyList()
}

class ConversationSettingsConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromSettings(value: com.unifiedcomms.data.model.ConversationSettings?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toSettings(value: String?): com.unifiedcomms.data.model.ConversationSettings = value?.let { json.decodeFromString(it) } ?: com.unifiedcomms.data.model.ConversationSettings()
}

class ServerConfigConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromConfig(value: com.unifiedcomms.data.model.ServerConfig?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toConfig(value: String?): com.unifiedcomms.data.model.ServerConfig = value?.let { json.decodeFromString(it) } ?: com.unifiedcomms.data.model.ServerConfig(
        imapHost = null, imapPort = 993, imapUseSsl = true,
        smtpHost = null, smtpPort = 587, smtpUseStartTls = true,
        caldavUrl = null, carddavUrl = null, webdavUrl = null,
        oauthTokenUrl = null, oauthAuthUrl = null, oauthClientId = null,
        oauthScopes = emptyList(), supportsPush = false, pushConfig = null
    )
}

class AuthConfigConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromConfig(value: com.unifiedcomms.data.model.AuthConfig?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toConfig(value: String?): com.unifiedcomms.data.model.AuthConfig = value?.let { json.decodeFromString(it) } ?: com.unifiedcomms.data.model.AuthConfig(
        type = com.unifiedcomms.data.model.AuthType.PASSWORD, username = null, passwordEncrypted = null
    )
}

class SyncConfigConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromConfig(value: com.unifiedcomms.data.model.SyncConfig?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toConfig(value: String?): com.unifiedcomms.data.model.SyncConfig = value?.let { json.decodeFromString(it) } ?: com.unifiedcomms.data.model.SyncConfig.Defaults()
}

class UIConfigConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromConfig(value: com.unifiedcomms.data.model.UIConfig?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toConfig(value: String?): com.unifiedcomms.data.model.UIConfig = value?.let { json.decodeFromString(it) } ?: com.unifiedcomms.data.model.UIConfig.Defaults()
}