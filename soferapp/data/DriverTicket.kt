package ro.priscom.sofer.ui.models

data class DriverTicket(
    val destination: String,
    val grossPrice: Double,
    val finalPrice: Double,
    val quantity: Int,
    val discountPercent: Double
)
