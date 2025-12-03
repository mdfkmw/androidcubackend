package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions_local")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    val subscriptionCode: String,
    val validatedAt: String,         // datetime ISO

    val tripId: Int?,
    val tripVehicleId: Int?,
    val employeeId: Int?,

    val syncStatus: Int              // 0=pending,1=synced,2=failed
)
