package ro.priscom.sofer.ui.data.local

import androidx.room.*

@Dao
interface ReservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(res: ReservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ReservationEntity>)

    @Query("DELETE FROM reservations_local WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: Int)

    @Query("SELECT * FROM reservations_local WHERE tripId = :tripId")
    suspend fun getByTrip(tripId: Int): List<ReservationEntity>

    @Query("SELECT COUNT(*) FROM reservations_local")
    suspend fun countAll(): Int

    @Query("UPDATE reservations_local SET boarded = :boarded, boardedAt = :boardedAt WHERE id = :reservationId")
    suspend fun updateBoardStatus(
        reservationId: Long,
        boarded: Boolean,
        boardedAt: String?
    )

    @Query("UPDATE reservations_local SET status = :status WHERE id = :reservationId")
    suspend fun updateStatus(
        reservationId: Long,
        status: String
    )

}
