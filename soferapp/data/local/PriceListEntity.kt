package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_lists")
data class PriceListEntity(
    @PrimaryKey val id: Int,
    val routeId: Int,
    val categoryId: Int,
    val effectiveFrom: String
)
