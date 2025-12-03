package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles WHERE operatorId = :operatorId")
    suspend fun getVehiclesForOperator(operatorId: Int): List<VehicleEntity>

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehicles(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehicles: List<VehicleEntity>)
}
