package com.smartcockpit.data.local.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WeatherConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromFloatList(value: List<Float>): String = gson.toJson(value)

    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        val listType = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromIntList(value: List<Int>): String = gson.toJson(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val listType = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, listType)
    }
}
