package ro.priscom.sofer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Opțiune de reducere:
 *  - id  = discount_types.id din backend (sau null pentru "fără reducere")
 *  - label = cum o vede șoferul
 *  - percent = procent reducere (ex: 50.0 înseamnă -50%)
 */
data class DiscountOption(
    val id: Int?,
    val label: String,
    val percent: Double
)

@Composable
fun ReduceriScreen(
    onBack: () -> Unit,
    onSelect: (DiscountOption?) -> Unit
) {
    val activeGreen = androidx.compose.ui.graphics.Color(0xFF5BC21E)

    // lista de reduceri – o legăm mai târziu 1:1 de discount_types din backend
    val options = listOf(
        DiscountOption(
            id = null,
            label = "FĂRĂ REDUCERE",
            percent = 0.0
        ),
        DiscountOption(
            id = 1,
            label = "Pensionar 50%",
            percent = 50.0
        ),
        DiscountOption(
            id = 2,
            label = "DAS 50%",
            percent = 50.0
        ),
        DiscountOption(
            id = 3,
            label = "Copil <10 ani 50%",
            percent = 50.0
        ),
        DiscountOption(
            id = 4,
            label = "Copil <12 ani 50%",
            percent = 50.0
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // header simplu: titlu + buton ÎNAPOI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Selectează reducere",
                fontSize = 18.sp,
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeGreen
                )
            ) {
                Text("ÎNAPOI")
            }
        }

        Spacer(Modifier.height(8.dp))

        // lista de reduceri
        options.forEach { opt ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        // dacă alegem "Fără reducere" trimitem null,
                        // altfel trimitem opțiunea selectată
                        if (opt.id == null && opt.percent == 0.0) {
                            onSelect(null)
                        } else {
                            onSelect(opt)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(text = opt.label, fontSize = 16.sp)
                    if (opt.percent != 0.0) {
                        Text(
                            text = "-${opt.percent.toInt()}%",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
