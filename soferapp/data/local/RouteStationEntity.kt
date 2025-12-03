package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_stations")
data class RouteStationEntity(
    @PrimaryKey val id: Int,
    val routeId: Int,
    val stationId: Int,
    val orderIndex: Int,
    val geofenceType: String?,
    val geofenceRadius: Double?,
    val geofencePolygon: String? // salvăm JSON aici
)
