package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PriceListItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<PriceListItemEntity>)

    @Query("SELECT * FROM price_list_items WHERE priceListId = :id")
    suspend fun getForList(id: Int): List<PriceListItemEntity>
}
