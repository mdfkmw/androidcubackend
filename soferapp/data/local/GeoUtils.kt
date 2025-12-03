package ro.priscom.sofer.ui.data.local

import org.json.JSONArray
import kotlin.math.*

// Stație + info de geofence
data class StationWithGeofence(
    val station: StationEntity,
    val geofenceType: String?,
    val geofenceRadiusMeters: Double?,
    val geofencePolygon: List<Pair<Double, Double>>?
)

// Distanța dintre două coordonate, în metri (Haversine)
fun distanceMeters(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double
): Double {
    val R = 6371000.0 // raza Pământului în metri
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lng2 - lng1)

    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

// Parsează JSON-ul salvat în geofencePolygon ("[[lat,lng],[lat,lng],...]")
fun parseGeofencePolygon(json: String?): List<Pair<Double, Double>>? {
    if (json.isNullOrBlank()) return null
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until arr.length()) {
            val pairArr = arr.getJSONArray(i)
            val lat = pairArr.getDouble(0)
            val lng = pairArr.getDouble(1)
            list.add(lat to lng)
        }
        if (list.isEmpty()) null else list
    } catch (e: Exception) {
        null
    }
}

// Point-in-polygon (algoritm ray casting)
fun isPointInPolygon(
    pointLat: Double,
    pointLng: Double,
    polygon: List<Pair<Double, Double>>
): Boolean {
    var result = false
    var j = polygon.size - 1

    for (i in polygon.indices) {
        val (lat_i, lng_i) = polygon[i]
        val (lat_j, lng_j) = polygon[j]

        val intersect = ((lng_i > pointLng) != (lng_j > pointLng)) &&
                (pointLat < (lat_j - lat_i) * (pointLng - lng_i) /
                        (lng_j - lng_i + 1e-12) + lat_i)

        if (intersect) {
            result = !result
        }
        j = i
    }

    return result
}

// Verifică dacă o poziție (lat,lng) este în interiorul geofence-ului unei stații
fun isInsideGeofence(
    lat: Double,
    lng: Double,
    stationWithGeofence: StationWithGeofence
): Boolean {
    val type = stationWithGeofence.geofenceType?.lowercase()

    // 1) poligon
    if (type == "polygon" || type == "poligon") {
        val polygon = stationWithGeofence.geofencePolygon
        if (polygon != null && polygon.isNotEmpty()) {
            return isPointInPolygon(lat, lng, polygon)
        }
    }

    // 2) cerc ("circle" sau "cerc")
    if (type == "circle" || type == "cerc") {
        val radius = stationWithGeofence.geofenceRadiusMeters
        val st = stationWithGeofence.station
        val centerLat = st.latitude
        val centerLng = st.longitude

        if (radius != null && centerLat != null && centerLng != null) {
            val dist = distanceMeters(lat, lng, centerLat, centerLng)
            return dist <= radius
        }
    }

    // 3) fallback – dacă nu avem tip clar, folosim doar proximitatea față de stație
    val st = stationWithGeofence.station
    val centerLat = st.latitude ?: return false
    val centerLng = st.longitude ?: return false
    val dist = distanceMeters(lat, lng, centerLat, centerLng)

    // de exemplu: dacă ești mai aproape de 100m de stație, o considerăm "curentă"
    return dist <= 1.0
}
