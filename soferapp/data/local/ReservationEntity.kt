package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reservations_local")
data class ReservationEntity(
    @PrimaryKey val id: Long,        // ID-ul rezervării de pe server

    val tripId: Int?,
    val seatId: Int?,

    val personId: Long?,
    val personName: String?,
    val personPhone: String?,

    val status: String,              // "active", "cancelled", "no_show"
    val boardStationId: Int?,
    val exitStationId: Int?,

    val boarded: Boolean,
    val boardedAt: String?,          // datetime sau null

    // 🔹 NOU – info rezervare
    val reservationTime: String?,    // din reservations.reservation_time
    val agentId: Int?,               // rp.employee_id
    val agentName: String?,          // employees.name

    // 🔹 NOU – prețuri + reduceri + plăți
    val basePrice: Double?,          // reservation_pricing.price_value
    val discountAmount: Double?,     // SUM(reservation_discounts.discount_amount)
    val finalPrice: Double?,         // basePrice - discountAmount
    val paidAmount: Double?,         // SUM(payments.amount status='paid')
    val dueAmount: Double?,          // finalPrice - paidAmount (>=0)
    val isPaid: Boolean,             // true dacă finalPrice e acoperit
    val discountLabel: String?,      // discount_types.label
    val promoCode: String?,          // promo_codes.code

    val syncStatus: Int              // 0=pending,1=synced,2=failed
)

