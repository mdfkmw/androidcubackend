package ro.priscom.sofer.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.priscom.sofer.ui.data.DriverLocalStore
import ro.priscom.sofer.ui.data.local.LocalRepository
import ro.priscom.sofer.ui.data.local.ReservationEntity

private enum class ReservationsTab {
    URCARI_AICI,
    TOATE,
    ISTORIC
}

@Composable
fun DriverReservationsScreen(
    tripId: Int,
    currentStopName: String?,
    repo: LocalRepository,
    onBack: () -> Unit
) {
    val activeGreen = Color(0xFF5BC21E)
    val headerBlue = Color(0xFFB0D4FF)

    val coroutineScope = rememberCoroutineScope()

    var allReservations by remember { mutableStateOf<List<ReservationEntity>>(emptyList()) }
    var stationNameById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var currentStationId by remember { mutableStateOf<Int?>(null) }

    var selectedTab by remember { mutableStateOf(ReservationsTab.URCARI_AICI) }

    // când intrăm în ecran: citim rezervările din SQLite
    LaunchedEffect(tripId) {
        allReservations = repo.getReservationsForTrip(tripId)
    }

    // încărcăm toate stațiile (ca să afișăm numele, nu ID-ul)
    LaunchedEffect(Unit) {
        val stations = repo.getAllStations()
        stationNameById = stations.associate { it.id to it.name }
    }

    // determinăm stationId pentru stația curentă (din nume)
    LaunchedEffect(currentStopName) {
        currentStationId = currentStopName?.let { repo.getStationIdByName(it) }
    }

    // funcție helper: nume stație din id
    fun stationName(id: Int?): String {
        if (id == null) return "-"
        return stationNameById[id] ?: "#$id"
    }

    // filtrări
    val reservationsSorted = remember(allReservations) {
        allReservations.sortedWith(
            compareBy<ReservationEntity>(
                { it.seatId == null },   // cele fără loc la final
                { it.seatId },
                { it.boardStationId }
            )
        )
    }

    val reservationsHere = remember(reservationsSorted, currentStationId) {
        if (currentStationId == null) emptyList()
        else reservationsSorted.filter { it.boardStationId == currentStationId }
    }

    // pentru ecranul de detalii
    var selectedReservation by remember { mutableStateOf<ReservationEntity?>(null) }

    // pentru ecranul de încasare
    var openPaymentFor by remember { mutableStateOf<ReservationEntity?>(null) }

    // dacă avem o rezervare pentru încasare, afișăm direct ecranul de încasare
    val paymentReservation = openPaymentFor
    if (paymentReservation != null) {
        PaymentScreen(
            reservation = paymentReservation,
            tripRouteScheduleId = paymentReservation.tripId, // deocamdată trimitem tripId
            repo = repo,
            onBack = { openPaymentFor = null },
            onConfirmPayment = { newExitId, discount, description ->
                // aici, mai târziu, vom salva plata (payments + update rezervare)
                openPaymentFor = null
            }
        )
        return
    }

    // dacă avem selecție, afișăm direct ecranul de detalii
    val sel = selectedReservation
    if (sel != null) {
        ReservationDetailsScreen(
            reservation = sel,
            fromStationName = stationName(sel.boardStationId),
            toStationName = stationName(sel.exitStationId),
            onBack = { selectedReservation = null },
            onMarkBoarded = {
                val current = sel   // rezervarea selectată acum

                coroutineScope.launch {
                    try {
                        // 1️⃣ apelăm repo – update pe server + SQLite
                        repo.markReservationBoarded(current.id)

                        // 2️⃣ recitim rezervările din DB local
                        val newList = repo.getReservationsForTrip(tripId)
                        allReservations = newList

                        // 3️⃣ găsim rezervarea actualizată și o punem în selectedReservation
                        val updated = newList.firstOrNull { it.id == current.id }
                        selectedReservation = updated

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onMarkNoShow = {
                val current = sel

                coroutineScope.launch {
                    try {
                        repo.markReservationNoShow(current.id)

                        val updated = current.copy(status = "no_show")

                        allReservations = allReservations.map { r ->
                            if (r.id == updated.id) updated else r
                        }

                        selectedReservation = updated

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onCancel = {
                val current = sel

                coroutineScope.launch {
                    try {
                        repo.cancelReservation(current.id)

                        val updated = current.copy(status = "cancelled")

                        allReservations = allReservations.map { r ->
                            if (r.id == updated.id) updated else r
                        }

                        selectedReservation = updated

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onOpenIncasare = {
                openPaymentFor = sel
            }
        )
        return
    }





    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // header sus
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBlue)
                .padding(12.dp)
        ) {
            Column {
                Text("Rezervări – cursa $tripId", fontSize = 18.sp)
                Text(
                    text = "Stație curentă: ${currentStopName ?: "-"}",
                    fontSize = 14.sp
                )
            }
        }

        // tab-uri
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color(0xFFEFEFEF)
        ) {
            Tab(
                selected = selectedTab == ReservationsTab.URCARI_AICI,
                onClick = { selectedTab = ReservationsTab.URCARI_AICI },
                text = { Text("URCĂRI AICI") }
            )
            Tab(
                selected = selectedTab == ReservationsTab.TOATE,
                onClick = { selectedTab = ReservationsTab.TOATE },
                text = { Text("TOATE") }
            )
            Tab(
                selected = selectedTab == ReservationsTab.ISTORIC,
                onClick = { selectedTab = ReservationsTab.ISTORIC },
                text = { Text("ISTORIC") }
            )
        }

        // conținut tab-uri – ocupă tot spațiul rămas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                ReservationsTab.URCARI_AICI -> {
                    ReservationsList(
                        reservations = reservationsHere,
                        stationName = ::stationName,
                        emptyMessage = if (currentStationId == null)
                            "Nu avem ID pentru stația curentă (GPS)."
                        else
                            "Nu există rezervări care urcă din această stație.",
                        onReservationClick = { selectedReservation = it }
                    )
                }

                ReservationsTab.TOATE -> {
                    ReservationsList(
                        reservations = reservationsSorted,
                        stationName = ::stationName,
                        emptyMessage = "Nu există rezervări pentru această cursă.",
                        onReservationClick = { selectedReservation = it }
                    )
                }

                ReservationsTab.ISTORIC -> {
                    ReservationsHistoryTab(
                        tripId = tripId,
                        onBackClick = { /* deocamdată nu face nimic */ }
                    )
                }
            }
        }

        // buton jos
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = activeGreen
            )
        ) {
            Text("ÎNAPOI")
        }

    }
}

/**
 * Lista simplă: doar loc, nume, telefon, segment.
 * FĂRĂ butoane, FĂRĂ "status: active".
 */
@Composable
private fun ReservationsList(
    reservations: List<ReservationEntity>,
    stationName: (Int?) -> String,
    emptyMessage: String,
    onReservationClick: (ReservationEntity) -> Unit
) {
    if (reservations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emptyMessage, color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items(reservations) { res ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReservationClick(res) }
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = "Loc ${res.seatId ?: "-"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = res.personName ?: "Fără nume",
                    fontSize = 14.sp,
                    color = Color.Black
                )
                if (!res.personPhone.isNullOrBlank()) {
                    Text(
                        text = res.personPhone!!,
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }
                Text(
                    text = "${stationName(res.boardStationId)} → ${stationName(res.exitStationId)}",
                    fontSize = 13.sp,
                    color = Color(0xFF333333)
                )
            }
            Divider(color = Color(0xFFDDDDDD))
        }
    }
}


/**
 * Tab-ul ISTORIC – deocamdată simplu: arată biletele emise local
 * pe cursa curentă. Mai târziu aici combinăm:
 *   - plăți pentru rezervări
 *   - imbarcări / no-show / anulări
 */
@Composable
private fun ReservationsHistoryTab(
    tripId: Int,
    onBackClick: () -> Unit
) {
    val tickets = DriverLocalStore.getTickets()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        if (tickets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Încă nu există istoric pentru această cursă.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(tickets) { t ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text("Bilet: ${t.destination}", fontSize = 14.sp)
                        Text(
                            "Cantitate: ${t.quantity}, Final: %.2f".format(t.finalPrice),
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }
                    Divider(color = Color(0xFFDDDDDD))
                }
            }
        }
    }
}


@Composable
fun ReservationDetailsScreen(
    reservation: ReservationEntity,
    fromStationName: String,
    toStationName: String,
    onBack: () -> Unit,
    onMarkBoarded: () -> Unit,
    onMarkNoShow: () -> Unit,
    onCancel: () -> Unit,
    onOpenIncasare: () -> Unit
) {
    val activeGreen = Color(0xFF5BC21E)
    val headerBlue = Color(0xFFB0D4FF)

    // 🔹 Poți marca îmbarcat DOAR dacă rezervarea este plătită și încă nu e îmbarcată
    val canMarkBoarded = reservation.isPaid && !reservation.boarded

    // 🔹 Poți anula doar dacă NU e îmbarcată și nu e deja anulată
    val canCancel = !reservation.boarded && reservation.status != "cancelled"


    var showConfirmBoarded by remember { mutableStateOf(false) }
    var showConfirmNoShow by remember { mutableStateOf(false) }
    var showConfirmCancel by remember { mutableStateOf(false) }

    val statusLabel = when (reservation.status) {
        "cancelled" -> "Anulată"
        "no_show" -> "No-show"
        else -> "Activă"
    }

    val boardedLabel = if (reservation.boarded) "Da" else "Nu"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBlue)
                .padding(12.dp)
        ) {
            Column {
                Text("Detalii rezervare", fontSize = 18.sp)
                Text(
                    "Loc ${reservation.seatId ?: "-"} – ${reservation.personName ?: ""}",
                    fontSize = 14.sp
                )
            }
        }

        // CONȚINUT
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Informații generale", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            DetailRow("Nume", reservation.personName ?: "—")
            DetailRow("Telefon", reservation.personPhone ?: "—")
            DetailRow("Loc", reservation.seatId?.toString() ?: "—")
            DetailRow("Segment", "$fromStationName → $toStationName")

            Spacer(Modifier.height(8.dp))

            DetailRow("Status rezervare", statusLabel)
            DetailRow("Îmbarcat", boardedLabel)
            DetailRow("Boarded at", reservation.boardedAt ?: "—")

            Spacer(Modifier.height(8.dp))

            DetailRow("Creată la", reservation.reservationTime ?: "—")
            DetailRow("Creată de", reservation.agentName ?: "—")

            val achitataText = when {
                reservation.finalPrice == null -> "—"
                reservation.isPaid -> "DA (${reservation.paidAmount ?: 0.0} lei)"
                else -> "NU (${reservation.paidAmount ?: 0.0} / ${reservation.finalPrice ?: 0.0} lei)"
            }
            DetailRow("Achitată", achitataText)

            val reducereText = when {
                reservation.discountLabel != null && reservation.discountAmount != null ->
                    "${reservation.discountLabel} (-${"%.2f".format(reservation.discountAmount)} lei)"
                reservation.discountLabel != null ->
                    reservation.discountLabel!!
                reservation.discountAmount != null ->
                    "-${"%.2f".format(reservation.discountAmount)} lei"
                else -> "—"
            }
            DetailRow("Reducere", reducereText)

            Spacer(Modifier.height(24.dp))

            Text("Acțiuni șofer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            // 🔵 ÎNCASEAZĂ / DIFERENȚĂ – FĂRĂ POPUP
            Button(
                onClick = onOpenIncasare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeGreen
                )
            ) {
                Text("ÎNCASEAZĂ / DIFERENȚĂ")
            }

            Spacer(Modifier.height(8.dp))

            // RÂND: ÎMBARCAT + NO-SHOW (ambele cu popup)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 🟢 ÎMBARCAT – cu confirmare + activ doar dacă e plătită
                Button(
                    onClick = { showConfirmBoarded = true },
                    enabled = canMarkBoarded,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("ÎMBARCAT")
                }

                // 🟡 NO-SHOW – cu confirmare
                val isNoShowEnabled =
                    reservation.status != "no_show" &&
                            reservation.status != "cancelled" &&
                            !reservation.boarded

                Button(
                    onClick = { showConfirmNoShow = true },
                    enabled = isNoShowEnabled,   // 🔴 AICI lipsise
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC107) // galben
                    )
                ) {
                    Text("NO-SHOW")
                }

            }

            Spacer(Modifier.height(8.dp))

            // 🔴 ANULEAZĂ REZERVAREA – cu confirmare
            Button(
                onClick = { showConfirmCancel = true },
                enabled = canCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDD2C00) // roșu
                )
            ) {
                Text("ANULEAZĂ REZERVAREA")
            }

        }

        // BUTON ÎNAPOI
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = activeGreen
            )
        ) {
            Text("ÎNAPOI")
        }
    }

    // 🔹 Popup confirmare ÎMBARCAT
    if (showConfirmBoarded) {
        AlertDialog(
            onDismissRequest = { showConfirmBoarded = false },
            title = { Text("Confirmare") },
            text = { Text("Ești sigur că vrei să marchezi rezervarea ca ÎMBARCATĂ?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmBoarded = false
                    onMarkBoarded()
                }) {
                    Text("DA")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBoarded = false }) {
                    Text("NU")
                }
            }
        )
    }

    // 🔹 Popup confirmare NO-SHOW
    if (showConfirmNoShow) {
        AlertDialog(
            onDismissRequest = { showConfirmNoShow = false },
            title = { Text("Confirmare") },
            text = { Text("Ești sigur că vrei să marchezi rezervarea ca NO-SHOW?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmNoShow = false
                    onMarkNoShow()
                }) {
                    Text("DA")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmNoShow = false }) {
                    Text("NU")
                }
            }
        )
    }

    // 🔹 Popup confirmare ANULARE
    if (showConfirmCancel) {
        AlertDialog(
            onDismissRequest = { showConfirmCancel = false },
            title = { Text("Confirmare") },
            text = { Text("Ești sigur că vrei să ANULEZI această rezervare?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmCancel = false
                    onCancel()
                }) {
                    Text("DA")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCancel = false }) {
                    Text("NU")
                }
            }
        )
    }
}


@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.DarkGray)
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 13.sp, color = Color.Black)
    }
}
