package com.unifiedcomms.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for UnifiedCommsDatabase.
 *
 * Each entry handles transition from [startVersion] → [endVersion].
 * The current exported schema is version 1.
 *
 * Because earlier builds had exportSchema=false, the stored schema hash
 * may differ even at the same version number on an installed app.
 * If a pre-Migration-1 build is detected on device, Room will fall back
 * to destructive migration unless a specific fallback path is provided
 * in the builder.
 *
 * Future schema changes should add a new Migration(start, end) here and
 * bump the @Database version.
 */
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
     * Example shape for a real bump when schema changes:
     *
     * val MIGRATION_1_2 = object : Migration(1, 2) {
     *     override fun migrate(db: SupportSQLiteDatabase) {
     *         db.execSQL("ALTER TABLE emails ADD COLUMN newField TEXT")
     *     }
     * }
     */
}
