package com.passwall.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NodeEntity::class, SubscriptionEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "passwall.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
