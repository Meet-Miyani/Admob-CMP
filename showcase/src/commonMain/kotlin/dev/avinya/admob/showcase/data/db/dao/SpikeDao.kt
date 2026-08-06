package dev.avinya.admob.showcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.avinya.admob.showcase.data.db.entity.SpikeEntity

@Dao
internal interface SpikeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpikeEntity)

    @Query("SELECT label FROM spike WHERE id = :id")
    suspend fun labelFor(id: Long): String?
}
