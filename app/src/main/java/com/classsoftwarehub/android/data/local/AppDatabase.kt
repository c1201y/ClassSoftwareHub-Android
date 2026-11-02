package com.classsoftwarehub.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.classsoftwarehub.android.data.local.dao.AnnounceDeviceDao
import com.classsoftwarehub.android.data.local.dao.DraftDao
import com.classsoftwarehub.android.data.local.dao.DownloadTaskDao
import com.classsoftwarehub.android.data.local.dao.SoftwareDao
import com.classsoftwarehub.android.data.local.entity.AnnounceDeviceEntity
import com.classsoftwarehub.android.data.local.entity.DraftEntity
import com.classsoftwarehub.android.data.local.entity.DownloadTaskEntity
import com.classsoftwarehub.android.data.local.entity.SoftwareEntity

@Database(
    entities = [
        SoftwareEntity::class,
        DownloadTaskEntity::class,
        DraftEntity::class,
        AnnounceDeviceEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun softwareDao(): SoftwareDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun draftDao(): DraftDao
    abstract fun announceDeviceDao(): AnnounceDeviceDao

    companion object {

        /** v1 → v2：教室喊话的已配对设备表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `announce_devices` " +
                        "(`code` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`selected` INTEGER NOT NULL, PRIMARY KEY(`code`))",
                )
            }
        }

        /** v2 → v3：软件表增加平台字段（软件库顶部平台大分类用） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `software` ADD COLUMN `platformsJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "classsoftwarehub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
