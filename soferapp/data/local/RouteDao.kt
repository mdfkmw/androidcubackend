package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RouteEntity>)

    @Query("SELECT * FROM routes")
    suspend fun getAll(): List<RouteEntity>
}
