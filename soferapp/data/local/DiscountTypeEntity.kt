package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discount_types")
data class DiscountTypeEntity(
    @PrimaryKey val id: Int,
    val code: String,
    val label: String,
    val valueOff: Double,
    val type: String, // "percent" / "fixed"
    val descriptionRequired: Boolean,
    val descriptionLabel: String?,
    val dateLimited: Boolean,
    val validFrom: String?, // "YYYY-MM-DD"
    val validTo: String?     // "YYYY-MM-DD"
)
