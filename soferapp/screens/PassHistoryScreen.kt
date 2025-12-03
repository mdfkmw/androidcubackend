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

@Composable
fun PassHistoryScreen(
    onBack: () -> Unit
) {
    val activeGreen = Color(0xFF5BC21E)

    // fake: abonamente validate/invalidated pe cursa curentă
    val abonamente = listOf(
        "Abonament 1 – Popescu Ion – VALIDAT",
        "Abonament 2 – Ionescu Maria – INVALIDAT",
        "Abonament 3 – Georgescu Dan – VALIDAT"
    )

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
            Text("Istoric abonamente (cursa curentă)", fontSize = 18.sp)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(abonamente) { ab ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(ab, color = Color.Black)
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
