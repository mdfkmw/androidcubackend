package ro.priscom.sofer.ui.data.local

import android.content.Context
import java.time.LocalDate
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import ro.priscom.sofer.ui.data.remote.BackendApi
import ro.priscom.sofer.ui.data.remote.MobileReservationDto



class LocalRepository(context: Context) {

    private val db = DatabaseProvider.getDatabase(context)

    suspend fun getDriver(id: Int) =
        db.employeeDao().getDriverById(id)

    suspend fun getOperators() =
        db.operatorDao().getAll()

    // Seed de test: băgăm operatori + un șofer în DB local dacă e gol
    suspend fun seedDemoDataIfEmpty() {
        // operatori
        if (db.operatorDao().getAll().isEmpty()) {
            db.operatorDao().insertAll(
                listOf(
                    OperatorEntity(id = 1, name = "Pris-Com"),
                    OperatorEntity(id = 2, name = "Auto-Dimas")
                )
            )
        }

        // șofer de test, ID = 25
        val demoId = 25
        val existing = db.employeeDao().getDriverById(demoId)
        if (existing == null) {
            db.employeeDao().insertAll(
                listOf(
                    EmployeeEntity(
                        id = demoId,
                        name = "Hotaran Cristi",
                        role = "driver",
                        operatorId = 2,
                        password = "1234"
                    )
                )
            )
        }
    }
    suspend fun seedVehiclesIfEmpty() {
        val vehicles = db.vehicleDao().getVehiclesForOperator(2)

        if (vehicles.isEmpty()) {
            db.vehicleDao().insertAll(
                listOf(
                    VehicleEntity(id = 101, plateNumber = "BT22DMS", operatorId = 2),
                    VehicleEntity(id = 102, plateNumber = "BT10PRS", operatorId = 2),
                    VehicleEntity(id = 103, plateNumber = "B200XYZ", operatorId = 1)
                )
            )
        }
    }
    suspend fun getVehiclesForOperator(operatorId: Int): List<VehicleEntity> =
        db.vehicleDao().getVehiclesForOperator(operatorId)

    suspend fun getAllVehicles(): List<VehicleEntity> =
        db.vehicleDao().getAllVehicles()

    // === RUTE / STAȚII / PREȚURI pentru UI ===

    suspend fun getAllRoutes() =
        db.routeDao().getAll()

    suspend fun getAllStations() =
        db.stationDao().getAll()

    suspend fun getStationsForRoute(routeId: Int, direction: String? = null): List<StationEntity> {
        val stations = db.stationDao().getForRoute(routeId)

        return if (direction?.lowercase() == "retur") stations.reversed() else stations
    }

    suspend fun getRouteStationsForRoute(routeId: Int) =
        db.routeStationDao().getForRoute(routeId)

    suspend fun getAllPriceLists() =
        db.priceListDao().getAll()

    suspend fun getPriceListItemsForList(listId: Int) =
        db.priceListItemDao().getForList(listId)

    /**
     * Găsește prețul de bază pentru un segment (stație -> stație),
     * din lista de prețuri a unei rute, pentru categoria NORMAL (id = 1),
     * ținând cont de data de azi (effective_from <= azi, se ia ultima).
     */
    suspend fun getPriceForSegment(
        routeId: Int,
        fromStationId: Int,
        toStationId: Int,
        categoryId: Int = 1,
        date: LocalDate = LocalDate.now()
    ): Double? {
        // 1. toate listele de preț
        val allLists = db.priceListDao().getAll()

        // 2. păstrăm doar pentru ruta + categoria cerute
        //    și doar cele care au intrat în vigoare până la data cerută
        val applicable = allLists.mapNotNull { list ->
            if (list.routeId != routeId || list.categoryId != categoryId) return@mapNotNull null
            val effective = try {
                LocalDate.parse(list.effectiveFrom)
            } catch (e: Exception) {
                null
            }
            if (effective != null && !effective.isAfter(date)) {
                list to effective
            } else {
                null
            }
        }

        // 3. luăm lista de preț "cea mai nouă" (cea mai mare effective_from)
        val chosenList = applicable.maxByOrNull { it.second }?.first ?: return null

        // 4. din lista aleasă, căutăm item-ul de preț pentru segmentul dorit
        val items = db.priceListItemDao().getForList(chosenList.id)
        val item = items.firstOrNull { it.fromStationId == fromStationId && it.toStationId == toStationId }

        return item?.price
    }

    /**
     * Variantă helper: căutăm prețul după numele stațiilor,
     * folosind stațiile din ruta curentă (ca să nu ne încurcăm cu nume duplicate).
     */
    suspend fun getPriceForSegmentByStationNames(
        routeId: Int,
        fromStationName: String,
        toStationName: String,
        categoryId: Int = 1,
        date: LocalDate = LocalDate.now()
    ): Double? {
        // luăm stațiile pentru rută (fără să inversăm pentru retur,
        // ne interesează doar id-urile pentru mapare nume -> id)
        val stations = getStationsForRoute(routeId, direction = null)

        val from = stations.firstOrNull { it.name == fromStationName } ?: return null
        val to = stations.firstOrNull { it.name == toStationName } ?: return null

        return getPriceForSegment(
            routeId = routeId,
            fromStationId = from.id,
            toStationId = to.id,
            categoryId = categoryId,
            date = date
        )
    }


    // === REZERVĂRI – sync din backend în SQLite ===

    /**
     * Ia din backend toate rezervările pentru un trip
     * și le salvează în tabela locală `reservations_local`.
     *
     * - Șterge întâi rezervările vechi pentru tripId.
     * - Apoi inserează lista nouă.
     */
    suspend fun refreshReservationsForTrip(tripId: Int) {
        try {
            val api = BackendApi.service
            val resp = api.getTripReservations(tripId)

            if (!resp.ok) {
                Log.e("LocalRepository", "refreshReservationsForTrip: backend not ok (trip=$tripId)")
                return
            }

            val list = resp.reservations.map { dto ->
                mapMobileReservationDtoToEntity(dto)
            }

            val dao = db.reservationDao()

            withContext(Dispatchers.IO) {
                // ștergem rezervările vechi pentru trip
                dao.deleteByTrip(tripId)

                // inserăm lista nouă, dacă nu e goală
                if (list.isNotEmpty()) {
                    dao.insertAll(list)
                }
            }


            Log.d(
                "LocalRepository",
                "refreshReservationsForTrip: saved ${list.size} reservations for trip=$tripId"
            )
        } catch (e: Exception) {
            Log.e("LocalRepository", "refreshReservationsForTrip error (trip=$tripId)", e)
        }
    }


    /**
     * Returnează rezervările pentru un trip din DB local.
     * (deocamdată doar pentru debug / DB Inspector)
     */
    suspend fun getReservationsForTrip(tripId: Int): List<ReservationEntity> {
        return db.reservationDao().getByTrip(tripId)
    }

    /**
     * Mapare simplă DTO -> Entity (pentru Room).
     */
    private fun mapMobileReservationDtoToEntity(dto: MobileReservationDto): ReservationEntity {
        return ReservationEntity(
            id = dto.id,
            tripId = dto.tripId,
            seatId = dto.seatId,
            personId = dto.personId,
            personName = dto.personName,
            personPhone = dto.personPhone,
            status = dto.status,
            boardStationId = dto.boardStationId,
            exitStationId = dto.exitStationId,
            boarded = dto.boarded != 0,

            boardedAt = dto.boardedAt,

            // NOU – info rezervare
            reservationTime = dto.reservationTime,
            agentId = dto.agentId,
            agentName = dto.agentName,

            // NOU – prețuri + reduceri + plăți
            basePrice = dto.basePrice,
            discountAmount = dto.discountAmount,
            finalPrice = dto.finalPrice,
            paidAmount = dto.paidAmount,
            dueAmount = dto.dueAmount,
            isPaid = dto.isPaid,
            discountLabel = dto.discountLabel,
            promoCode = dto.promoCode,

            syncStatus = 1 // 1 = synced (vine din backend, nu e pending)
        )
    }



    /**
     * Găsește numele stației curente pentru o rută,
     * pe baza coordonatelor GPS și a geofence-urilor.
     *
     * @param routeId      ruta curentă
     * @param direction    "tur" / "retur" sau null
     * @param currentLat   latitudinea telefonului
     * @param currentLng   longitudinea telefonului
     */
    suspend fun findCurrentStationNameForRoute(
        routeId: Int,
        direction: String?,
        currentLat: Double,
        currentLng: Double
    ): String? {

        // 1. toate stațiile pentru rută (cu lat/lng)
        val stations = getStationsForRoute(routeId, direction)

        // 2. route_stations pentru rută (geofence info)
        val routeStations = getRouteStationsForRoute(routeId)

        // map stationId -> StationEntity
        val stationById = stations.associateBy { it.id }

        // 3. construim lista de StationWithGeofence
        val withGeofence = routeStations.mapNotNull { rs ->
            val st = stationById[rs.stationId] ?: return@mapNotNull null
            StationWithGeofence(
                station = st,
                geofenceType = rs.geofenceType,
                geofenceRadiusMeters = rs.geofenceRadius,
                geofencePolygon = parseGeofencePolygon(rs.geofencePolygon)
            )
        }

        // 4. întâi căutăm stațiile la care suntem "în geofence"
        val inside = withGeofence.filter {
            isInsideGeofence(currentLat, currentLng, it)
        }

        // Dacă suntem în raza/poligonul uneia sau mai multor stații,
        // luăm prima stație găsită (ordinea e cea din route_stations)
        if (inside.isNotEmpty()) {
            return inside.first().station.name
        }

        // 5. dacă NU suntem în niciun geofence, nu returnăm nicio stație
        // -> funcția întoarce null, iar UI-ul va afișa "necunoscută"
        return null


    }



    // Test simplu spre backend: apel GET la /api/ping (sau ce endpoint de test ai)
    suspend fun testBackendPing(): String? = withContext(Dispatchers.IO) {
        try {
            // ATENȚIE:
            // - pe emulator: 10.0.2.2 e calculatorul tău (unde merge backend-ul pe port 5000)
            // - dacă folosești un telefon real: vei schimba asta cu IP-ul PC-ului tău în rețea
            val url = URL("http://10.0.2.2:5000/api/ping")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }

            conn.disconnect()

            "HTTP $code: $text"
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR: ${e.message}"
        }
    }


    suspend fun getStationIdByName(name: String): Int? {
        return db.stationDao().getStationIdByName(name)
    }

    suspend fun getPriceListIdForRoute(routeId: Int): Int? {
        return db.priceListDao().getPriceListIdForRoute(routeId)
    }


    suspend fun markReservationBoarded(reservationId: Long) {
        val api = BackendApi.service
        val dao = db.reservationDao()

        // 1️⃣ Trimitem spre backend
        val response = api.markReservationBoarded(reservationId)

        if (!response.ok) {
            throw Exception(response.error ?: "Eroare necunoscută la îmbarcare")
        }

        // 2️⃣ Facem update local în SQLite — DOAR UN RAND!
        val now = java.time.LocalDateTime.now().toString()

        dao.updateBoardStatus(
            reservationId = reservationId,
            boarded = true,
            boardedAt = now
        )

        // 3️⃣ Atât! Nu ștergem, nu refacem tot trip-ul.
    }

    suspend fun markReservationNoShow(reservationId: Long) {
        val api = BackendApi.service
        val dao = db.reservationDao()

        // 1️⃣ Trimitem spre backend
        val response = api.markReservationNoShow(reservationId)

        if (!response.ok) {
            throw Exception(response.error ?: "Eroare necunoscută la NO-SHOW")
        }

        // 2️⃣ Facem update local în SQLite — DOAR UN RAND!
        dao.updateStatus(
            reservationId = reservationId,
            status = "no_show"
        )
    }

    suspend fun cancelReservation(reservationId: Long) {
        val api = BackendApi.service
        val dao = db.reservationDao()

        // 1️⃣ Trimitem spre backend
        val response = api.cancelReservation(reservationId)

        if (!response.ok) {
            throw Exception(response.error ?: "Eroare necunoscută la anulare")
        }

        // 2️⃣ Facem update local în SQLite — DOAR UN RAND
        dao.updateStatus(
            reservationId = reservationId,
            status = "cancelled"
        )
    }

    // ================================================================
    //  REDUCERI (DISCOUNTS) – FILTRARE PE ROUTE_SCHEDULE
    // ================================================================

    /**
     * Returnează reducerile permise pentru un route_schedule_id,
     * filtrate după vizibilitatea pentru șofer (visibleDriver = true).
     *
     * Funcționează 100% offline deoarece folosește doar SQLite.
     */
    suspend fun getDiscountsForRouteSchedule(routeScheduleId: Int?): List<DiscountTypeEntity> {
        if (routeScheduleId == null) return emptyList()

        val routeDiscountDao = db.routeDiscountDao()
        val discountTypeDao = db.discountTypeDao()

        // 1️⃣ luăm reducerile definite pentru programarea rutei
        val rows = routeDiscountDao.getForSchedule(routeScheduleId)

        // 2️⃣ filtrăm doar ce e vizibil pentru aplicația șofer
        val driverVisible = rows.filter { it.visibleDriver }

        if (driverVisible.isEmpty()) return emptyList()

        // 3️⃣ extragem lista de discount_type_id
        val ids = driverVisible.map { it.discountTypeId }.distinct()

        // 4️⃣ returnăm reducerile complete
        return discountTypeDao.getByIds(ids)
    }

    // ================================================================
    //  REDUCERI – listă completă pentru aplicația șofer
    // ================================================================
    suspend fun getAllDiscountTypes(): List<DiscountTypeEntity> {
        return db.discountTypeDao().getAll()
    }
}



