package ro.priscom.sofer.ui.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EmployeeEntity::class,
        OperatorEntity::class,
        RouteEntity::class,
        VehicleEntity::class,
        TicketEntity::class,
        ReservationEntity::class,
        SubscriptionEntity::class,
        StationEntity::class,
        RouteStationEntity::class,
        PriceListEntity::class,
        PriceListItemEntity::class,
        DiscountTypeEntity::class,
        RouteDiscountEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun employeeDao(): EmployeeDao
    abstract fun operatorDao(): OperatorDao
    abstract fun routeDao(): RouteDao
    abstract fun vehicleDao(): VehicleDao

    abstract fun ticketDao(): TicketDao
    abstract fun reservationDao(): ReservationDao
    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun stationDao(): StationDao
    abstract fun routeStationDao(): RouteStationDao

    abstract fun priceListDao(): PriceListDao
    abstract fun priceListItemDao(): PriceListItemDao

    abstract fun discountTypeDao(): DiscountTypeDao
    abstract fun routeDiscountDao(): RouteDiscountDao
}
