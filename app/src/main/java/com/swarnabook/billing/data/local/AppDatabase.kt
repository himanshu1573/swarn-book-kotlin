package com.swarnabook.billing.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.InvoiceItem

@Database(
    entities = [Invoice::class, InvoiceItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun invoiceDao(): InvoiceDao

    companion object {
        private const val DB_NAME = "swarnabook.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, DB_NAME
                ).build().also { instance = it }
            }
    }
}
