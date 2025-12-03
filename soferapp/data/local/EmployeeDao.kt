package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmployeeDao {

    // luăm șoferul după ID, dar doar dacă are rolul "driver"
    @Query("SELECT * FROM employees WHERE role = 'driver' AND id = :id LIMIT 1")
    suspend fun getDriverById(id: Int): EmployeeEntity?

    // vom popula local DB cu lista de angajați primită de la server
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(employees: List<EmployeeEntity>)
}
