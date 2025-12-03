package ro.priscom.sofer.ui.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Repository simplu pentru salvarea biletelor în baza de date locală (Room).
 *
 * - Creează un rând în tabela `tickets_local` cu syncStatus = 0 (pending).
 * - Va fi preluat ulterior de RemoteSyncRepository.syncTickets(...) și trimis la backend.
 */
class TicketLocalRepository(context: Context) {

    private val db: AppDatabase = DatabaseProvider.getDatabase(context)
    private val ticketDao: TicketDao = db.ticketDao()

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Salvează UN bilet local.
     *
     * ATENȚIE:
     *  - Dacă quantity > 1 în UI, vom apela această funcție de mai multe ori.
     *  - Aici salvăm UN singur bilet = 1 rezervare în backend.
     */
    suspend fun saveTicketLocally(
        operatorId: Int? = null,
        employeeId: Int? = null,
        tripId: Int?,
        tripVehicleId: Int? = null,
        fromStationId: Int?,
        toStationId: Int?,
        seatId: Int? = null,
        priceListId: Int?,
        discountTypeId: Int? = null,
        basePrice: Double?,
        finalPrice: Double?,
        currency: String? = "RON",
        paymentMethod: String? = "cash",
        createdAt: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = createdAt ?: LocalDateTime.now().format(dateFormatter)

        val ticket = TicketEntity(
            id = 0L,                     // autoGenerate
            remoteReservationId = null,  // id rezervare din backend (după sync)
            syncStatus = 0,              // 0 = pending (nesincronizat)

            operatorId = operatorId,
            employeeId = employeeId,
            tripId = tripId,
            tripVehicleId = tripVehicleId,

            // câmpuri intermediare (dacă ai adăugat altele în TicketEntity) rămân cu valori default

            fromStationId = fromStationId,
            toStationId = toStationId,
            seatId = seatId,
            priceListId = priceListId,
            discountTypeId = discountTypeId,

            basePrice = basePrice,
            finalPrice = finalPrice,
            currency = currency,
            paymentMethod = paymentMethod,

            createdAt = now
        )

        try {
            ticketDao.insert(ticket)
            Log.d("TicketLocalRepository", "Saved ticket locally: $ticket")
        } catch (e: Exception) {
            Log.e("TicketLocalRepository", "Error saving ticket locally", e)
        }
    }
}
