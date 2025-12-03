package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiscountTypeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DiscountTypeEntity>)

    @Query("DELETE FROM discount_types")
    suspend fun clearAll()

    @Query("SELECT * FROM discount_types ORDER BY label")
    suspend fun getAll(): List<DiscountTypeEntity>

    @Query("SELECT * FROM discount_types WHERE id IN (:ids) ORDER BY label")
    suspend fun getByIds(ids: List<Int>): List<DiscountTypeEntity>
}
