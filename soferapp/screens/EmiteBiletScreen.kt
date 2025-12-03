package ro.priscom.sofer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmiteBiletScreen(
    onBack: () -> Unit,
    onDestinationSelected: (String) -> Unit,
    stations: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        // TITLU SUS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFB0D4FF))
                .padding(12.dp)
        ) {
            Text(
                text = "Selectați stația destinație",
                fontSize = 18.sp,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(4.dp))

        // LISTA DE STAȚII (DOAR DIN PARAMETRUL 'stations')
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(stations) { statie ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDestinationSelected(statie)
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = statie,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }

                // Linie de separare
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFDDDDDD))
                )
            }
        }

        // BUTON RENUNȚĂ
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5BC21E)
            )
        ) {
            Text(
                text = "RENUNȚĂ",
                fontSize = 18.sp,
                color = Color.Black
            )
        }
    }
}
