/*
 * YueYing (月影) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 * Copyright (C) 2026 月影 (YueYing) Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yueying.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yueying.app.data.security.CredentialCipher
import com.yueying.app.data.security.CredentialStore
import com.yueying.app.util.DiagnosticLog

@Database(
    entities = [QuarkAccountEntity::class, DownloadTaskEntity::class, UCAccountEntity::class, XunleiAccountEntity::class, BaiduAccountEntity::class, C139AccountEntity::class, Pan123AccountEntity::class, Pan115AccountEntity::class, GuangYaAccountEntity::class, ILanzouAccountEntity::class, LanzouAccountEntity::class, BookmarkEntity::class, UserEntity::class],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rawQuarkAccountDao(): QuarkAccountDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun rawUcAccountDao(): UCAccountDao

    abstract fun rawXunleiAccountDao(): XunleiAccountDao

    abstract fun rawBaiduAccountDao(): BaiduAccountDao

    abstract fun rawC139AccountDao(): C139AccountDao

    abstract fun rawPan123AccountDao(): Pan123AccountDao

    abstract fun rawPan115AccountDao(): Pan115AccountDao

    abstract fun rawGuangYaAccountDao(): GuangYaAccountDao

    abstract fun rawILanzouAccountDao(): ILanzouAccountDao

    abstract fun rawLanzouAccountDao(): LanzouAccountDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun userDao(): UserDao

    private lateinit var credentialCipher: CredentialCipher

    fun quarkAccountDao(): QuarkAccountDao = SecureAccountDaos.quark(rawQuarkAccountDao(), credentialCipher)
    fun ucAccountDao(): UCAccountDao = SecureAccountDaos.uc(rawUcAccountDao(), credentialCipher)
    fun xunleiAccountDao(): XunleiAccountDao = SecureAccountDaos.xunlei(rawXunleiAccountDao(), credentialCipher)
    fun baiduAccountDao(): BaiduAccountDao = SecureAccountDaos.baidu(rawBaiduAccountDao(), credentialCipher)
    fun c139AccountDao(): C139AccountDao = SecureAccountDaos.c139(rawC139AccountDao(), credentialCipher)
    fun pan123AccountDao(): Pan123AccountDao = SecureAccountDaos.pan123(rawPan123AccountDao(), credentialCipher)
    fun pan115AccountDao(): Pan115AccountDao = SecureAccountDaos.pan115(rawPan115AccountDao(), credentialCipher)
    fun guangyaAccountDao(): GuangYaAccountDao = SecureAccountDaos.guangya(rawGuangYaAccountDao(), credentialCipher)
    fun ilanzouAccountDao(): ILanzouAccountDao = SecureAccountDaos.ilanzou(rawILanzouAccountDao(), credentialCipher)
    fun lanzouAccountDao(): LanzouAccountDao = SecureAccountDaos.lanzou(rawLanzouAccountDao(), credentialCipher)

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yueying.db"
                )
                    .addMigrations(
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19
                    )
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) = logSchema("create", db)
                        override fun onOpen(db: SupportSQLiteDatabase) = logSchema("open", db)

                        private fun logSchema(action: String, db: SupportSQLiteDatabase) {
                            runCatching {
                                val tables = mutableListOf<String>()
                                db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { c ->
                                    while (c.moveToNext()) {
                                        val name = c.getString(0) ?: continue
                                        if (!name.startsWith("sqlite_") && !name.startsWith("android_") && name != "room_master_table") {
                                            tables.add(name)
                                        }
                                    }
                                }
                                val version = runCatching { db.version }.getOrDefault(-1)
                                DiagnosticLog.log(
                                    DiagnosticLog.DB,
                                    "db_$action",
                                    status = "v$version",
                                    size = tables.size.toLong(),
                                    summary = "file=yueying.db | tables=${tables.joinToString(",")}"
                                )
                                if (DiagnosticLog.isEnabled()) {
                                    tables.forEach { table ->
                                        runCatching {
                                            db.query("SELECT COUNT(*) FROM $table").use { c ->
                                                if (c.moveToFirst()) DiagnosticLog.db(table, "count", 0L, c.getInt(0))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })
                    .build()
                    .also { database ->
                        database.credentialCipher = com.yueying.app.data.security.AndroidKeystoreCredentialCipher.shared
                        CredentialStore.installRecovery(context)
                        instance = database
                    }
            }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN requestHeadersJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE download_task ADD COLUMN chunkCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN plannedTotalSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN cleanupId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN platform TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN avgSpeed INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmark` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`link` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`pwd` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`createTime` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `yy_user` (" +
                        "`email` TEXT NOT NULL PRIMARY KEY, " +
                        "`passHash` TEXT NOT NULL, " +
                        "`salt` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookmark ADD COLUMN homePinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookmark ADD COLUMN homeLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pan115_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`cookie` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `download_task` ADD COLUMN `engineTaskId` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `guangya_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`accessToken` TEXT NOT NULL, " +
                        "`refreshToken` TEXT NOT NULL, " +
                        "`deviceId` TEXT NOT NULL, " +
                        "`deviceSign` TEXT NOT NULL, " +
                        "`account` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ilanzou_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`appToken` TEXT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`account` TEXT NOT NULL, " +
                        "`password` TEXT NOT NULL, " +
                        "`userId` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lanzou_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`cookie` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `xunlei_account` ADD COLUMN `authType` TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
