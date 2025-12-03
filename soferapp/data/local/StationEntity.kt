package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val latitude: Double?,
    val longitude: Double?
)
