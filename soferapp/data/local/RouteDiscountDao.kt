package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteDiscountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RouteDiscountEntity>)

    @Query("DELETE FROM route_discounts")
    suspend fun clearAll()

    @Query("""
        SELECT * FROM route_discounts
        WHERE routeScheduleId = :routeScheduleId
    """)
    suspend fun getForSchedule(routeScheduleId: Int): List<RouteDiscountEntity>
}
