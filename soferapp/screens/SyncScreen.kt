package ro.priscom.sofer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ro.priscom.sofer.ui.data.SyncStatusStore
import ro.priscom.sofer.ui.data.local.AppDatabase
import ro.priscom.sofer.ui.data.remote.RemoteSyncRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Composable
fun SyncScreen(
    db: AppDatabase,
    loggedIn: Boolean,
    onBack: () -> Unit = {}
) {




    val scope = rememberCoroutineScope()
    val syncRepo = remember { RemoteSyncRepository() }

    var isRunning by remember { mutableStateOf(false) }
    var lastMessage by remember {
        mutableStateOf(SyncStatusStore.lastResultMessage ?: "Nu a fost rulat încă niciun sync.")
    }
    var lastSyncAt by remember { mutableStateOf(SyncStatusStore.lastSyncTimestamp) }

    val formatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    }

    fun runSync() {
        scope.launch {
            isRunning = true
            val result = syncRepo.syncMasterData(db, loggedIn)
            val message = buildString {
                appendLine("Sincronizare completă:")
                appendLine("- operators: ${result.operators}")
                appendLine("- employees: ${result.employees}")
                appendLine("- vehicles: ${result.vehicles}")
                appendLine("- routes: ${result.routes}")
                appendLine("- stations: ${result.stations}")
                appendLine("- route_stations: ${result.routeStations}")
                appendLine("- price_lists: ${result.priceLists}")
                appendLine("- price_list_items: ${result.priceListItems}")

                val ticketsResult = syncRepo.syncTickets(db)
                appendLine("- tickets total: ${ticketsResult.total}")
                appendLine("- tickets synced: ${ticketsResult.synced}")
                appendLine("- tickets failed: ${ticketsResult.failed}")
                if (ticketsResult.error != null) {
                    appendLine("Eroare sync tickets: ${ticketsResult.error}")
                }

                result.error?.let { errorMessage ->
                    appendLine()
                    appendLine()
                    append("Eroare sincronizare: $errorMessage")



                }
            }
            val finishedAt = LocalDateTime.now()
            lastMessage = message
            lastSyncAt = finishedAt
            SyncStatusStore.update(message, finishedAt)
            isRunning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {

        Text(
            "Sincronizare date",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(16.dp))

        Text(
            lastMessage,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = lastSyncAt?.let { "Ultima sincronizare: ${it.format(formatter)}" }
                ?: "Ultima sincronizare: —",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { runSync() },
            enabled = !isRunning
        ) {
            Text(if (isRunning) "Se sincronizează..." else "Sincronizează acum")
        }
        Button(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Înapoi")
        }
    }
}
