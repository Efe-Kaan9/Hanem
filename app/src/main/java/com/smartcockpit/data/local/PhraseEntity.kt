package com.smartcockpit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_phrase")
data class PhraseEntity(
    @PrimaryKey
    val id: Int = 0,
    val en: String,
    val tr: String,
    val index: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)
