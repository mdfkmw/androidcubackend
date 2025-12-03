package ro.priscom.sofer.ui.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val role: String,
    val operatorId: Int,
    val password: String        // <- NOU: parola locală
)
