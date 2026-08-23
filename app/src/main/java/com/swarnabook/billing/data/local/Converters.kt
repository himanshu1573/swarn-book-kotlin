package com.swarnabook.billing.data.local

import androidx.room.TypeConverter
import com.swarnabook.billing.data.model.MakingMode

/** SQLite has no enum type, so making-charge mode is stored as its name. */
class Converters {

    @TypeConverter
    fun fromMakingMode(mode: MakingMode): String = mode.name

    @TypeConverter
    fun toMakingMode(value: String): MakingMode =
        runCatching { MakingMode.valueOf(value) }.getOrDefault(MakingMode.PERCENT)
}
