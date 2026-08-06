package dev.avinya.admob.showcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Throwaway entity proving Room's KSP processor runs for every target.
 * The Phase 1b plan deletes this and introduces the real schema.
 */
@Entity(tableName = "spike")
internal data class SpikeEntity(
    @PrimaryKey val id: Long,
    val label: String,
)
