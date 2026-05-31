package com.smartcockpit.data.local.dao

import androidx.room.*
import com.smartcockpit.data.local.GalleryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {
    @Query("SELECT * FROM gallery_images")
    fun getAllImages(): Flow<List<GalleryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImage(image: GalleryEntity)

    @Delete
    suspend fun removeImage(image: GalleryEntity)
}
