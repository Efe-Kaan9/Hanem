package com.smartcockpit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartcockpit.data.local.PhraseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhraseDao {
    @Query("SELECT * FROM daily_phrase WHERE id = 0")
    fun getDailyPhrase(): Flow<PhraseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePhrase(phrase: PhraseEntity)
}
