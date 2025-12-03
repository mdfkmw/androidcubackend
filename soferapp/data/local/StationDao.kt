package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<StationEntity>)

    @Query("SELECT * FROM stations ORDER BY name")
    suspend fun getAll(): List<StationEntity>

    @Query(
        """
        SELECT s.*
        FROM stations s
        INNER JOIN route_stations rs ON rs.stationId = s.id
        WHERE rs.routeId = :routeId
        ORDER BY rs.orderIndex
        """
    )
    suspend fun getForRoute(routeId: Int): List<StationEntity>

    @Query("SELECT id FROM stations WHERE name = :name LIMIT 1")
    suspend fun getStationIdByName(name: String): Int?
}
