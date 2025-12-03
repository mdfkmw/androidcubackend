package ro.priscom.sofer.ui.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TicketDao {

    /**
     * Inserează un bilet local în tabela `tickets_local`.
     *
     * Returnează:
     *  - id-ul local generat (coloana `id` din TicketEntity).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: TicketEntity): Long

    @Query("SELECT COUNT(*) FROM tickets_local")
    suspend fun countAll(): Int

    // 👇 toate biletele care trebuie sincronizate cu backend-ul
    @Query("SELECT * FROM tickets_local WHERE syncStatus = 0")
    suspend fun getPendingTickets(): List<TicketEntity>

    // 👇 actualizăm statusul de sincronizare + id-ul rezervării din backend
    @Query(
        "UPDATE tickets_local " +
                "SET syncStatus = :syncStatus, remoteReservationId = :remoteReservationId " +
                "WHERE id = :localId"
    )
    suspend fun updateSyncStatus(
        localId: Long,
        syncStatus: Int,
        remoteReservationId: Long?
    )
}
