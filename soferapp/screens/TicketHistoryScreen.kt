package ro.priscom.sofer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.priscom.sofer.ui.data.DriverLocalStore

@Composable
fun TicketHistoryScreen(
    onBack: () -> Unit
) {
    val activeGreen = Color(0xFF5BC21E)

    // fake: câteva bilete pentru cursa curentă
    val bilete = DriverLocalStore.getTickets()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFB0D4FF))
                .padding(12.dp)
        ) {
            Text("Istoric bilete emise (cursa curentă)", fontSize = 18.sp)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(bilete) { bilet ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Destinație: ${bilet.destination}",
                        color = Color.Black
                    )
                    Text(
                        text = "Cantitate: ${bilet.quantity}, Final: %.2f".format(bilet.finalPrice),
                        color = Color.DarkGray,
                        fontSize = 13.sp
                    )
                }
            }

        }

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
