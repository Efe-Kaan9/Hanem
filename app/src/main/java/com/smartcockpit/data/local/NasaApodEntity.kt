package com.smartcockpit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nasa_apod")
data class NasaApodEntity(
    @PrimaryKey
    val date: String,
    val title: String,
    val explanation: String,
    val url: String,
    val hdUrl: String?,
    val mediaType: String
)
