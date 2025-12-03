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
 *  - type = "percent" sau "fixed", conform discount_types.type
 *  - valueOff = valoarea reducerii (procent sau sumă fixă)
 */
data class DiscountOption(
    val id: Int?,
    val label: String,
    val type: String = "percent", // "percent" sau "fixed"
    val valueOff: Double
)

@Composable
fun ReduceriScreen(
    onBack: () -> Unit,
    options: List<DiscountOption>,
    onSelect: (DiscountOption?) -> Unit
) {
    val activeGreen = androidx.compose.ui.graphics.Color(0xFF5BC21E)

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
                        if (opt.id == null && opt.valueOff == 0.0) {
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
                    val badge = when (opt.type) {
                        "fixed" -> "-${"%.0f".format(opt.valueOff)} lei"
                        else -> "-${opt.valueOff.toInt()}%"
                    }
                    if (opt.valueOff != 0.0) {
                        Text(text = badge, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
