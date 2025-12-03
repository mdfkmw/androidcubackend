package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operators")
data class OperatorEntity(
    @PrimaryKey val id: Int,
    val name: String
)
