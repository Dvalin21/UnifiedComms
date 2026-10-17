# ProGuard rules for UnifiedComms

# Keep Room database classes
-keep class com.unifiedcomms.data.db.** { *; }
-keep class com.unifiedcomms.data.model.** { *; }
-keep class com.unifiedcomms.data.repository.** { *; }

# Keep kotlinx.serialization
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * {
    <fields>;
}

# Keep OkHttp and Retrofit
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class retrofit2.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keep class com.jakewharton.retrofit.** { *; }

# Keep JavaMail
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }

# Keep CalDAV/CardDAV
# (at.bitfire.* was removed with the unused ical4android/dav4jvm dependencies)

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep Security Crypto
-keep class androidx.security.crypto.** { *; }

# Keep Glance
# (Glance was never wired up; widgets are classic RemoteViews)

# Keep Coil
-keep class coil.** { *; }
-keep class io.coil.** { *; }

# Keep Notification
-keep class androidx.core.app.NotificationCompat { *; }
-keep class androidx.core.app.NotificationManagerCompat { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Kotlin datetime
-keep class kotlinx.datetime.** { *; }

# Keep annotated classes
-keep @androidx.room.Entity class * {
    public <init>();
}
-keep @androidx.room.Dao class * {
    public <init>();
}
-keep @androidx.room.Database class * {
    public <init>();
}
# kotlinx.serialization reflects on the synthetic constructor.
-keep @kotlinx.serialization.Serializable class * {
    public <init>();
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Optimize
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep line numbers
-keepattributes SourceFile,LineNumberTable

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}