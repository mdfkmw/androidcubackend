package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reprezintă un bilet emis de șofer, salvat local în SQLite (Room).
 *
 * IMPORTANT:
 *  - syncStatus:
 *      0 = pending (nesincronizat)
 *      1 = synced  (trimis cu succes la backend)
 *      2 = failed  (eroare la sync)
 *
 *  - remoteReservationId:
 *      id-ul rezervării create în backend (din tabela `reservations`),
 *      completat după sincronizare.
 */
@Entity(tableName = "tickets_local")
data class TicketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,                 // ID local, generat de Room

    val remoteReservationId: Long?,    // ID rezervare din backend (după sync)
    val syncStatus: Int,               // 0=pending, 1=synced, 2=failed

    val operatorId: Int?,              // operatorul la care lucrează șoferul
    val employeeId: Int?,              // id-ul șoferului (employee_id)
    val tripId: Int?,                  // trip-ul pe care s-a emis biletul
    val tripVehicleId: Int?,           // vehiculul pentru cursa respectivă (dacă e relevant)

    val fromStationId: Int?,           // stație urcare (board_station_id)
    val toStationId: Int?,             // stație coborâre (exit_station_id)
    val seatId: Int?,                  // seat_id (doar la cursele lungi cu loc; altfel null)
    val priceListId: Int?,             // lista de preț folosită
    val discountTypeId: Int?,          // tipul de reducere (dacă există)

    val basePrice: Double?,            // prețul de bază (fără reducere)
    val finalPrice: Double?,           // prețul după reducere (plătit efectiv)
    val currency: String?,             // ex: "RON"
    val paymentMethod: String?,        // "cash", "card"

    val createdAt: String              // "yyyy-MM-dd HH:mm:ss" - data/ora emiterii biletului
)
