package ro.priscom.sofer.ui.data.remote

import android.util.Log
import java.time.LocalDate

class RemoteRepository {

    suspend fun login(identifier: String, password: String): AuthUserDto? {
        return try {
            val response = BackendApi.service.login(
                LoginRequest(identifier, password)
            )

            if (response.ok && response.user != null) {
                response.user
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e("RemoteRepository", "Login error", e)
            null
        }
    }

    suspend fun getRoutesWithTripsForToday(): List<MobileRouteWithTripsDto>? {
        return try {
            val today = LocalDate.now().toString() // "YYYY-MM-DD"
            BackendApi.service.getRoutesWithTrips(today)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getRouteStations(
        routeId: Int,
        direction: String?
    ): List<RouteStationDto>? {
        return try {
            BackendApi.service.getRouteStationsApp(routeId, direction)
        } catch (e: Exception) {
            Log.e("RemoteRepository", "getRouteStations error", e)
            null
        }
    }

    suspend fun validateTripStart(
        routeId: Int,
        tripId: Int?,
        vehicleId: Int
    ): ValidateTripStartResponse? {
        return try {
            BackendApi.service.validateTripStart(
                ValidateTripStartRequest(
                    routeId = routeId,
                    tripId = tripId,
                    vehicleId = vehicleId
                )
            )
        } catch (e: Exception) {
            Log.e("RemoteRepository", "validateTripStart error", e)
            null
        }
    }
    suspend fun attachDriverVehicle(
        tripId: Int,
        vehicleId: Int
    ) {
        try {
            BackendApi.service.attachDriverVehicle(
                tripId = tripId,
                body = AttachDriverVehicleRequest(
                    vehicleId = vehicleId
                )
            )
            // Nu ne interesează răspunsul, scopul e doar să se trimită către backend.
            // Dacă backend-ul refuză (ex: cursă cu rezervări), e ok, nu blocăm șoferul.
        } catch (e: Exception) {
            Log.e("RemoteRepository", "attachDriverVehicle error", e)
            // Nu afișăm eroare la șofer, doar log în aplicație.
        }
    }
}


