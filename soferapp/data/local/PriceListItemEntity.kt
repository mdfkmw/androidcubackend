package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_list_items")
data class PriceListItemEntity(
    @PrimaryKey val id: Int,
    val price: Double,
    val currency: String,
    val priceListId: Int,
    val fromStationId: Int,
    val toStationId: Int
)
