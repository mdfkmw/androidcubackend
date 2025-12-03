package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OperatorDao {

    @Query("SELECT * FROM operators")
    suspend fun getAll(): List<OperatorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operators: List<OperatorEntity>)
}
