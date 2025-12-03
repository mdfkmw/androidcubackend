package ro.priscom.sofer.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.priscom.sofer.ui.components.StatusBar
import ro.priscom.sofer.ui.models.Route
import ro.priscom.sofer.ui.data.DriverLocalStore
import ro.priscom.sofer.ui.data.local.LocalRepository
import ro.priscom.sofer.ui.data.local.StationEntity
import ro.priscom.sofer.ui.data.remote.RemoteRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    driverName: String,
    vehicleInfo: String,
    routeInfo: String,
    loggedIn: Boolean,
    selectedVehicleId: Int?,
    onOpenSync: () -> Unit = {},
    onOpenLogin: () -> Unit = {}
) {


    val kasaStatus = "Casă: neconectată"
    var gpsStatus by remember { mutableStateOf("GPS: OFF") }
    val netStatus = "Internet: offline"
    val batteryStatus = "Baterie: 75%"

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Administrare", "Operații")

    // aici ținem textul traseu/ora selectate
    var currentRouteInfo by rememberSaveable { mutableStateOf(routeInfo) }
    var currentRouteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var currentDirection by rememberSaveable { mutableStateOf<String?>(null) }

    // stația curentă (calculată din GPS + geofence)
    var currentStopName by remember { mutableStateOf<String?>(null) }

    // stare persistentă între taburi: cursă pornită / îmbarcare pornită / selecție auto
    var tripStarted by rememberSaveable { mutableStateOf(false) }
    var boardingStarted by rememberSaveable { mutableStateOf(false) }
    var autoSelected by remember { mutableStateOf(false) }
    var autoSelectedTouched by remember { mutableStateOf(false) }
    var currentTripId by rememberSaveable { mutableStateOf<Int?>(null) }
    var currentRouteScheduleId by rememberSaveable { mutableStateOf<Int?>(null) }




    val context = LocalContext.current
    val activity = context as? Activity

    // ultima locație primită de la GPS
    var currentLocation by remember { mutableStateOf<Location?>(null) }

    // LocationManager pentru GPS
    val locationManager = remember {
        context.getSystemService(LocationManager::class.java)
    }

    val repo = remember { LocalRepository(context) }


    val remoteRepo = remember { RemoteRepository() }
    var stationsList by remember { mutableStateOf(listOf<String>()) }

    // cerem permisiunea de locație la prima pornire
    LaunchedEffect(Unit) {
        if (activity != null) {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    1001
                )
            }
        }
    }

    // ascultăm actualizările de locație
    DisposableEffect(locationManager, activity) {
        if (locationManager == null || activity == null) {
            onDispose { }
        } else {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                gpsStatus = "GPS: fără permisiune"
                onDispose { }
            } else {
                gpsStatus = "GPS: căutare sateliți…"

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        currentLocation = location
                        gpsStatus = "GPS: ON"
                    }
                }

                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000L,   // la 5 secunde
                        5f,      // la 5 metri
                        listener
                    )
                } catch (e: SecurityException) {
                    gpsStatus = "GPS: eroare permisiune"
                }

                onDispose {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }


    LaunchedEffect(currentRouteId, currentDirection) {
        val stations = currentRouteId?.let { routeId ->
            // preferăm să lăsăm backend-ul să returneze lista gata ordonată
            val remote = remoteRepo.getRouteStations(routeId, currentDirection)
            val remoteStations = remote?.map { dto ->
                StationEntity(
                    id = dto.station_id,
                    name = dto.stationName ?: "Stația ${dto.station_id}",
                    latitude = null,
                    longitude = null
                )
            }



            remoteStations ?: repo.getStationsForRoute(routeId, currentDirection)
        } ?: repo.getAllStations()

        stationsList = stations.map { it.name }
    }

    // când avem rută selectată și locație, calculăm stația curentă
    LaunchedEffect(currentRouteId, currentDirection, currentLocation) {
        val routeId = currentRouteId
        val loc = currentLocation
        if (routeId != null && loc != null) {
            val name = repo.findCurrentStationNameForRoute(
                routeId = routeId,
                direction = currentDirection,
                currentLat = loc.latitude,
                currentLng = loc.longitude
            )
            currentStopName = name
        } else {
            currentStopName = null
        }
    }
    // când primim pentru prima dată semnal GPS, bifăm automat "SELECȚIE AUTO"
    LaunchedEffect(currentLocation) {
        val hasGps = currentLocation != null
        if (hasGps && !autoSelectedTouched) {
            autoSelected = true
        }
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Aplicație Șofer")
                        Text(
                            text = "$driverName | $vehicleInfo",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = currentRouteInfo,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )
        },
        bottomBar = {
            StatusBar(
                kasaStatus = kasaStatus,
                gpsStatus = gpsStatus,
                netStatus = netStatus,
                batteryStatus = batteryStatus
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> AdminTabScreen(
                    loggedIn = loggedIn,
                    tripStarted = tripStarted,
                    selectedVehicleId = selectedVehicleId,
                    onRouteSelected = { routeId, routeName, hour, tripId, direction, routeScheduleId ->
                        currentRouteId = routeId
                        currentDirection = direction
                        currentRouteInfo = "Traseu: $routeName, Cursa: $hour"
                        currentTripId = tripId          // salvăm trip-ul curent
                        currentRouteScheduleId = routeScheduleId
                        tripStarted = true              // cursa PORNITĂ
                        boardingStarted = false         // resetăm îmbarcarea
                    },
                    onEndTrip = {
                        tripStarted = false
                        boardingStarted = false
                        currentTripId = null            // nu mai avem cursă activă
                        currentRouteScheduleId = null
                        currentRouteId = null
                        currentDirection = null
                        currentRouteInfo = "Nicio cursă selectată încă"
                    },
                    onOpenSync = onOpenSync,
                    onOpenLogin = onOpenLogin
                )


                1 -> OperatiiTabScreen(
                    currentStopName = currentStopName,
                    stations = stationsList,
                    loggedIn = loggedIn,
                    tripStarted = tripStarted,
                    boardingStarted = boardingStarted,
                    autoSelected = autoSelected,
                    gpsHasSignal = currentLocation != null,
                    routeId = currentRouteId,
                    tripId = currentTripId,               // 🆕 trimitem trip-ul curent
                    tripVehicleId = selectedVehicleId,    // 🆕 trimitem vehiculul selectat
                    repo = repo,
                    onAutoSelectedChange = { checked ->
                        autoSelectedTouched = true
                        autoSelected = checked
                    },
                    onStartBoarding = {
                        boardingStarted = true
                    }
                )





            }
        }
    }
    // Când se schimbă trip-ul curent, sincronizăm rezervările din backend în SQLite
    LaunchedEffect(currentTripId) {
        val tripId = currentTripId
        if (tripId != null) {
            try {
                repo.refreshReservationsForTrip(tripId)
            } catch (e: Exception) {
                // doar log, să nu pice UI-ul
                android.util.Log.e("MainTabsScreen", "Error refreshing reservations for trip=$tripId", e)
            }
        }
    }

}


@Composable
fun AdminTabScreen(
    loggedIn: Boolean,
    tripStarted: Boolean,
    selectedVehicleId: Int?,
    onRouteSelected: (Int, String, String, Int?, String?, Int?) -> Unit,
    onEndTrip: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenLogin: () -> Unit
)
{



    val activeGreen = Color(0xFF5BC21E)
    val inactiveGrey = Color(0xFFCCCCCC)
    val inactiveText = Color(0xFF444444)

    val context = LocalContext.current
    val localRepo = remember { LocalRepository(context) }
    val remoteRepo = remember { RemoteRepository() }

    var routes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var tripIdByRouteAndDisplayTime by remember {
        mutableStateOf<Map<Pair<Int, String>, Int>>(emptyMap())
    }
    var routeScheduleIdByRouteAndDisplayTime by remember {
        mutableStateOf<Map<Pair<Int, String>, Int?>>(emptyMap())
    }
    var directionByRouteAndDisplayTime by remember {
        mutableStateOf<Map<Pair<Int, String>, String>>(emptyMap())
    }
    val coroutineScope = rememberCoroutineScope()
    var startTripError by remember { mutableStateOf<String?>(null) }



    LaunchedEffect(Unit) {
         // 1. încercăm să luăm rute + trips din backend-ul mobil
         val mobileRoutes = remoteRepo.getRoutesWithTripsForToday()

         if (mobileRoutes != null && mobileRoutes.isNotEmpty()) {
             routes = mobileRoutes.map { mr ->
                 Route(
                     id = mr.route_id,
                     name = mr.route_name,
                     // folosim direct display_time: "TUR 07:00", "RETUR 13:00", etc.
                     hours = mr.trips.map { it.display_time }
                 )
             }

             // mapăm (route_id, display_time) -> trip_id
             val tripMap = mutableMapOf<Pair<Int, String>, Int>()
             val routeScheduleMap = mutableMapOf<Pair<Int, String>, Int?>()
             val directionMap = mutableMapOf<Pair<Int, String>, String>()
             mobileRoutes.forEach { mr ->
                 mr.trips.forEach { trip ->
                     val key = mr.route_id to trip.display_time
                     tripMap[key] = trip.trip_id
                     routeScheduleMap[key] = trip.route_schedule_id
                     directionMap[key] = trip.direction
                 }
             }
             tripIdByRouteAndDisplayTime = tripMap
             routeScheduleIdByRouteAndDisplayTime = routeScheduleMap
             directionByRouteAndDisplayTime = directionMap
         } else {
             // 2. fallback – ce aveai deja (DB sau dummy)
             val entities = localRepo.getAllRoutes()

             routes = if (entities.isNotEmpty()) {
                 entities.map { entity ->
                     Route(
                         id = entity.id,
                         name = entity.name,
                         hours = listOf("ORA CURSEI NEDEFINITĂ (TODO)")
                     )
                 }
             } else {
                 listOf(
                     Route(
                         id = 1,
                         name = "Botoșani -> București",
                         hours = listOf("TUR 06:00 - TUR", "TUR 12:00 - TUR", "RETUR 18:00 - RETUR")
                     ),
                     Route(
                         id = 2,
                         name = "Botoșani -> Iași",
                         hours = listOf("TUR 07:00 - TUR", "TUR 09:00 - TUR", "RETUR 15:30 - RETUR")
                     ),
                     Route(
                         id = 3,
                         name = "Hârlău -> Iași",
                         hours = listOf("TUR 06:00 - TUR", "RETUR 13:00 - RETUR")
                     )
                 )
             }

            // în fallback nu avem trips reale
            tripIdByRouteAndDisplayTime = emptyMap()
            routeScheduleIdByRouteAndDisplayTime = emptyMap()
            directionByRouteAndDisplayTime = emptyMap()
         }
     }



    var screen by remember { mutableStateOf("buttons") }
    var selectedRoute by remember { mutableStateOf<Route?>(null) }


    // poate începe cursa doar dacă e logat și nu are o cursă în desfășurare
    val canStartTrip = loggedIn && !tripStarted

    // poate încheia cursa doar dacă e logat și există o cursă în desfășurare
    val canCloseTrip = loggedIn && tripStarted

    // poate închide ziua doar dacă e logat și NU este cursă activă
    val canCloseDay = loggedIn && !tripStarted



    when (screen) {
        "buttons" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // rând 1: LOGIN | PORNIRE IN CURSA
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (!loggedIn) onOpenLogin()
                        },
                        enabled = !loggedIn, // după login se dezactivează
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!loggedIn) activeGreen else inactiveGrey,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("LOGIN")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (canStartTrip) screen = "selectRoute"
                        },
                        enabled = canStartTrip,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canStartTrip) activeGreen else inactiveGrey,
                            contentColor = if (canStartTrip) Color.Black else inactiveText
                        )
                    ) {
                        Text("PORNIRE IN CURSA")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // rând 2: REINITIALIZARE CASA... | INCHEIERE CURSA
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { /* REINITIALIZARE CASA */ },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeGreen
                        )
                    ) {
                        Text("REINITIALIZARE CASA DE MARCAT / CARD READER")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (canCloseTrip) {
                                onEndTrip()
                            }
                        },
                        enabled = canCloseTrip,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canCloseTrip) activeGreen else inactiveGrey,
                            contentColor = if (canCloseTrip) Color.Black else inactiveText
                        )
                    ) {
                        Text("INCHEIERE CURSA")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // rând 3: SINCRONIZARE | INCHIDERE ZI
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onOpenSync() },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeGreen
                        )
                    ) {
                        Text("SINCRONIZARE")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { /* INCHIDERE ZI */ },
                        enabled = canCloseDay,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canCloseDay) activeGreen else inactiveGrey,
                            contentColor = if (canCloseDay) Color.Black else inactiveText
                        )
                    ) {
                        Text("INCHIDERE ZI")
                    }
                }
            }
        }

        "selectRoute" -> {
            SelectRouteScreen(
                routes = routes,
                onRouteClick = { route ->
                    selectedRoute = route
                    screen = "selectHour"
                },
                onCancel = { screen = "buttons" }
            )
        }

        "selectHour" -> {
            val route = selectedRoute
            if (route == null) {
                screen = "buttons"
            } else {
                SelectHourScreen(
                    route = route,
                    onHourClick = { hour ->
                        val key = route.id to hour
                        val tripId = tripIdByRouteAndDisplayTime[key]
                        val routeScheduleId = routeScheduleIdByRouteAndDisplayTime[key]
                        val direction = directionByRouteAndDisplayTime[key]

                        if (!loggedIn) {
                            startTripError = "Trebuie să fii logat pentru a porni cursa."
                            return@SelectHourScreen
                        }

                        if (selectedVehicleId == null) {
                            startTripError = "Trebuie să alegi mai întâi o mașină înainte de a porni cursa."
                            return@SelectHourScreen
                        }

                        coroutineScope.launch {
                            val resp = remoteRepo.validateTripStart(
                                routeId = route.id,
                                tripId = tripId,
                                vehicleId = selectedVehicleId
                            )

                            if (resp == null) {
                                startTripError = "Nu s-a putut verifica dacă mașina este corectă. Verifică conexiunea la internet."
                                return@launch
                            }

                            if (!resp.ok) {
                                startTripError = resp.error
                                    ?: "Mașina aleasă nu este asociată acestei curse cu rezervări."
                                return@launch
                            }

                            // Aici resp.ok == true:
                            // - pentru curse cu rezervări: știm că vehiculul e corect
                            // - pentru curse scurte: backend-ul nu a blocat
                            //
                            // În ambele cazuri, dacă avem tripId, încercăm să atașăm mașina
                            // aleasă de șofer la acest trip în trip_vehicles.
                            if (tripId != null) {
                                remoteRepo.attachDriverVehicle(
                                    tripId = tripId,
                                    vehicleId = selectedVehicleId
                                )
                            }

                              // totul OK → putem porni cursa în MainTabsScreen
                              onRouteSelected(route.id, route.name, hour, tripId, direction, routeScheduleId)
                              screen = "buttons"
                          }
                      },
                    onCancel = { screen = "buttons" }
                )
            }
        }


    }
    if (startTripError != null) {
        AlertDialog(
            onDismissRequest = { startTripError = null },
            title = { Text("Nu poți porni cursa") },
            text = { Text(startTripError!!) },
            confirmButton = {
                TextButton(onClick = { startTripError = null }) {
                    Text("OK")
                }
            }
        )
    }

}

@Composable
fun SelectRouteScreen(
    routes: List<Route>,
    onRouteClick: (Route) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // titlu sus
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFB0D4FF))
                .padding(12.dp)
        ) {
            Text(
                text = "Selectați traseul",
                fontSize = 18.sp,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(routes) { route ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRouteClick(route) }
                        .padding(vertical = 14.dp, horizontal = 12.dp)
                ) {
                    Text(route.name, fontSize = 16.sp, color = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFDDDDDD))
                )
            }
        }

        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5BC21E)
            )
        ) {
            Text("RENUNTA", fontSize = 18.sp, color = Color.Black)
        }
    }
}

@Composable
fun SelectHourScreen(
    route: Route,
    onHourClick: (String) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // titlu sus
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFB0D4FF))
                .padding(12.dp)
        ) {
            Text(
                text = "Selectați cursa",
                fontSize = 18.sp,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(route.hours) { hour ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHourClick(hour) }
                        .padding(vertical = 14.dp, horizontal = 12.dp)
                ) {
                    Text(hour, fontSize = 16.sp, color = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFDDDDDD))
                )
            }
        }

        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5BC21E)
            )
        ) {
            Text("RENUNTA", fontSize = 18.sp, color = Color.Black)
        }
    }

}








@Composable
fun OperatiiTabScreen(
    currentStopName: String?,
    stations: List<String>,
    loggedIn: Boolean,
    tripStarted: Boolean,
    boardingStarted: Boolean,
    autoSelected: Boolean,
    gpsHasSignal: Boolean,
    routeId: Int?,
    tripId: Int?,                    // 🆕 id-ul cursei curente
    tripVehicleId: Int?,             // 🆕 vehiculul folosit pe cursă
    repo: LocalRepository,
    onAutoSelectedChange: (Boolean) -> Unit,
    onStartBoarding: () -> Unit
) {



    val activeGreen = Color(0xFF5BC21E)
    val inactiveGrey = Color(0xFFCCCCCC)
    val inactiveText = Color(0xFF444444)

    var showDestinations by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<String?>(null) }
    var showStartBoardingConfirm by remember { mutableStateOf(false) }
    var showTicketHistory by remember { mutableStateOf(false) }
    var showPassHistory by remember { mutableStateOf(false) }

    var showReservations by remember { mutableStateOf(false) }


    val coroutineScope = rememberCoroutineScope()
    var selectedDestinationPrice by remember { mutableStateOf<Double?>(null) }


    var fromStationId by remember { mutableStateOf<Int?>(null) }
    var toStationId by remember { mutableStateOf<Int?>(null) }
    var priceListId by remember { mutableStateOf<Int?>(null) }





    // Stațiile disponibile ca destinație:
    // - dacă avem stație curentă și e în listă,
    //   arătăm doar stațiile DE DUPĂ ea (în ordinea rutei).
    // - dacă nu avem stație curentă sau nu e găsită, arătăm toate stațiile.
    val nextStations = remember(currentStopName, stations) {
        if (currentStopName == null) {
            stations
        } else {
            val idx = stations.indexOf(currentStopName)
            if (idx >= 0 && idx < stations.lastIndex) {
                stations.subList(idx + 1, stations.size)
            } else {
                // nu am găsit stația curentă în listă, sau e ultima
                emptyList()
            }
        }
    }

    LaunchedEffect(currentStopName, routeId) {
        fromStationId = currentStopName?.let { repo.getStationIdByName(it) }
        priceListId = routeId?.let { repo.getPriceListIdForRoute(it) }
    }

    LaunchedEffect(selectedDestination) {
        toStationId = selectedDestination?.let { repo.getStationIdByName(it) }
    }



    // --- reguli de activare ---

    // „Plecare din stație”:
    //  - activ dacă: NU există semnal GPS SAU bifa de selecție auto NU e bifată
    //  - dezactivat numai când: există GPS și bifa e bifată
    val canPlecare =
        loggedIn && (!gpsHasSignal || !autoSelected)

    val canEmiteBilet =
        loggedIn && tripStarted && boardingStarted

    val canRezervari =
        loggedIn && tripStarted

    val canStartBoarding =
        loggedIn && tripStarted && !boardingStarted

    val canRapoarte =
        loggedIn && tripStarted && boardingStarted

    // „Selecție auto”:
    //  - bifa este activă doar dacă există GPS și șoferul e logat
    val canSelectAuto =
        loggedIn && gpsHasSignal


    when {
        showTicketHistory -> {
            TicketHistoryScreen(onBack = { showTicketHistory = false })
        }

        showPassHistory -> {
            PassHistoryScreen(onBack = { showPassHistory = false })
        }

        showReservations && tripId != null -> {
            DriverReservationsScreen(
                tripId = tripId,
                currentStopName = currentStopName,
                repo = repo,
                onBack = { showReservations = false }
            )
        }

        selectedDestination != null -> {
                    BiletDetaliiScreen(
                        destination = selectedDestination!!,
                        onBack = { selectedDestination = null },
                        onIncasare = {
                            selectedDestination = null
                            showDestinations = false
                        },
                        currentStopName = currentStopName,
                        ticketPrice = selectedDestinationPrice,
                        repo = repo,
                        routeScheduleId = currentRouteScheduleId,

                        // date reale pentru bilet – folosim parametrii funcției, nu variabile din altă parte
                        tripId = tripId,
                        fromStationId = fromStationId,
                        toStationId = toStationId,
                priceListId = priceListId,
                tripVehicleId = tripVehicleId,
                operatorId = DriverLocalStore.getOperatorId(),
                employeeId = DriverLocalStore.getEmployeeId()
            )
        }





        showDestinations -> {
            EmiteBiletScreen(
                onBack = { showDestinations = false },
                onDestinationSelected = { dest ->
                    selectedDestination = dest
                    showDestinations = false

                    // calculăm prețul real pentru segment (currentStopName → dest)
                    if (routeId != null && currentStopName != null) {
                        coroutineScope.launch {
                            val price = repo.getPriceForSegmentByStationNames(
                                routeId = routeId,
                                fromStationName = currentStopName,
                                toStationName = dest,
                                categoryId = 1  // NORMAL
                            )
                            selectedDestinationPrice = price
                        }
                    } else {
                        selectedDestinationPrice = null
                    }
                },
                stations = nextStations
            )
        }


        else -> {
            // biletele emise local
            val tickets = DriverLocalStore.getTickets()

            val persoaneCoboara = if (currentStopName != null) {
                tickets
                    .filter { it.destination.contains(currentStopName, ignoreCase = true) }
                    .sumOf { it.quantity }
            } else 0

            // deocamdată 0 – în viitor: rezervări + abonamente din stația curentă
            val persoaneUrca = 0

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // rând 1: PLECARE DIN STATIE | EMITE BILET
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { /* TODO: Plecare din statie (GPS) */ },
                        enabled = canPlecare,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canPlecare) activeGreen else inactiveGrey,
                            contentColor = if (canPlecare) Color.Black else inactiveText
                        )
                    ) {
                        Text("PLECARE DIN STATIE")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { showDestinations = true },
                        enabled = canEmiteBilet,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canEmiteBilet) activeGreen else inactiveGrey,
                            contentColor = if (canEmiteBilet) Color.Black else inactiveText
                        )
                    ) {
                        Text("EMITE BILET")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // rând 2: REZERVARI | PORNESTE IMBARCAREA
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showReservations = true },
                        enabled = canRezervari,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canRezervari) activeGreen else inactiveGrey,
                            contentColor = if (canRezervari) Color.Black else inactiveText
                        )
                    ) {
                        Text("REZERVARI")
                    }


                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { showStartBoardingConfirm = true },
                        enabled = canStartBoarding,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canStartBoarding) activeGreen else inactiveGrey,
                            contentColor = if (canStartBoarding) Color.Black else inactiveText
                        )
                    ) {
                        Text(
                            if (boardingStarted)
                                "IMBARCAREA A INCEPUT"
                            else
                                "PORNESTE IMBARCAREA"
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // rând 3: RAPOARTE | SELECTIE AUTO (bifă)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { /* TODO: Rapoarte */ },
                        enabled = canRapoarte,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canRapoarte) activeGreen else inactiveGrey,
                            contentColor = if (canRapoarte) Color.Black else inactiveText
                        )
                    ) {
                        Text("RAPOARTE")
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoSelected,
                            onCheckedChange = { checked ->
                                if (canSelectAuto) {
                                    onAutoSelectedChange(checked)
                                }
                            },
                            enabled = canSelectAuto
                        )

                        Text(
                            text = "SELECTIE AUTO",
                            color = if (canSelectAuto) Color.Black else inactiveText
                        )
                    }
                }

                // carduri sub butoane
                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clickable { if (loggedIn) showTicketHistory = true }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Istoric bilete emise (cursa curentă)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ex: ultimele 5 bilete.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clickable { if (loggedIn) showPassHistory = true }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Istoric abonamente (cursa curentă)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ex: ultimele validări/invalidări.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // sumar stație curentă
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = if (currentStopName != null)
                                "Stația curentă: $currentStopName"
                            else
                                "Stația curentă: necunoscută",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )


                        Text(
                            text = "Persoane care COBORĂ: $persoaneCoboara",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Persoane care URCĂ: $persoaneUrca",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // popup confirmare pornire îmbarcare
            if (showStartBoardingConfirm) {
                AlertDialog(
                    onDismissRequest = { showStartBoardingConfirm = false },
                    title = { Text("Pornește îmbarcarea") },
                    text = { Text("Ești sigur că vrei să pornești îmbarcarea?") },
                    confirmButton = {
                        TextButton(onClick = {
                            onStartBoarding()
                            showStartBoardingConfirm = false
                        }) {
                            Text("DA")
                        }
                    }
,
                    dismissButton = {
                        TextButton(onClick = { showStartBoardingConfirm = false }) {
                            Text("NU")
                        }
                    }
                )
            }
        }
    }
}







