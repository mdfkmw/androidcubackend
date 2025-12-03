package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val orderIndex: Int,
    val visibleForDrivers: Boolean
)
