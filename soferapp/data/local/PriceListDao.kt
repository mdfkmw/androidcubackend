package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PriceListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<PriceListEntity>)

    @Query("SELECT * FROM price_lists")
    suspend fun getAll(): List<PriceListEntity>

    @Query("SELECT id FROM price_lists WHERE routeId = :routeId LIMIT 1")
    suspend fun getPriceListIdForRoute(routeId: Int): Int?

}
