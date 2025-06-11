package com.thingsapart.langtutor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.languageapp.data.dao.ChatDao
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import com.thingsapart.langtutor.data.dao.ChatDao
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.data.model.ChatMessageEntity

@Database(entities = [ChatConversationEntity::class, ChatMessageEntity::class], version = 1, exportSchema = false) // Set exportSchema to true for production apps
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "language_app_database"
                )
                // Add migrations here if needed in the future
                .fallbackToDestructiveMigration() // Not recommended for production, for simplicity now
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
