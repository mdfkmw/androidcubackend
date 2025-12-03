package ro.priscom.sofer.ui.data.local

import androidx.room.*

@Dao
interface RouteStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RouteStationEntity>)

    @Query("SELECT * FROM route_stations WHERE routeId = :routeId ORDER BY orderIndex")
    suspend fun getForRoute(routeId: Int): List<RouteStationEntity>
}

