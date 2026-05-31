package com.smartcockpit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_images")
data class GalleryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imageUri: String
)
