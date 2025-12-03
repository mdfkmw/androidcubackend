package ro.priscom.sofer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ro.priscom.sofer.ui.data.local.DiscountTypeEntity
import ro.priscom.sofer.ui.data.local.LocalRepository
import ro.priscom.sofer.ui.data.local.ReservationEntity
import androidx.compose.foundation.clickable


@Composable
fun PaymentScreen(
    reservation: ReservationEntity,
    tripRouteScheduleId: Int?,
    repo: LocalRepository,
    onBack: () -> Unit,
    onConfirmPayment: (
        newExitStationId: Int?,
        discount: DiscountTypeEntity?,
        description: String?
    ) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // === Reduceri disponibile (din DB local) ===
    var discountList by remember { mutableStateOf<List<DiscountTypeEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        discountList = repo.getAllDiscountTypes()
    }


    // === Stare UI ===
    var selectedDiscount by remember { mutableStateOf<DiscountTypeEntity?>(null) }
    var descriptionText by remember { mutableStateOf("") }

    val needsDescription = selectedDiscount?.descriptionRequired == true
    val isDescriptionOK = if (needsDescription) descriptionText.isNotBlank() else true
    val canConfirm = isDescriptionOK

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Încasare pentru rezervarea:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("${reservation.personName} – loc ${reservation.seatId ?: "-"}")

        Spacer(Modifier.height(20.dp))

        // =============== SELECTOR REDUCERI ===============
        PaymentDiscountSection(
            discounts = discountList,
            selectedDiscount = selectedDiscount,
            descriptionText = descriptionText,
            onSelectDiscount = { discount ->
                selectedDiscount = discount
                if (discount?.descriptionRequired != true) {
                    descriptionText = ""
                }
            },
            onDescriptionChange = { newText ->
                descriptionText = newText
            }
        )

        Spacer(Modifier.height(24.dp))

        // =============== BUTON CONFIRMARE ===============
        Button(
            onClick = {
                onConfirmPayment(
                    reservation.exitStationId,
                    selectedDiscount,
                    if (descriptionText.isBlank()) null else descriptionText
                )
            },
            enabled = canConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CONFIRMĂ ÎNCASAREA")
        }


        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ÎNAPOI")
        }
    }
}

/**
 * Secțiunea de alegere reducere + câmp text (dacă descriptionRequired = true).
 */
@Composable
fun PaymentDiscountSection(
    discounts: List<DiscountTypeEntity>,
    selectedDiscount: DiscountTypeEntity?,
    descriptionText: String,
    onSelectDiscount: (DiscountTypeEntity?) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // === SELECTOR REDUCERE – variantă simplă, stabilă ===
        Box {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                value = selectedDiscount?.label ?: "Fără reducere",
                onValueChange = { /* nimic, doar selectăm din meniu */ },
                readOnly = true,
                label = { Text("Reducere") },
                trailingIcon = { Text("▼") }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                // Opțiune: fără reducere
                DropdownMenuItem(
                    text = { Text("Fără reducere") },
                    onClick = {
                        onSelectDiscount(null)
                        expanded = false
                    }
                )

                // Reducerile disponibile pentru cursă
                discounts.forEach { discount ->
                    DropdownMenuItem(
                        text = { Text(discount.label) },
                        onClick = {
                            onSelectDiscount(discount)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // === CÂMP TEXT – doar dacă descriptionRequired == true ===
        val needsDescription = selectedDiscount?.descriptionRequired == true

        if (needsDescription) {
            val labelText = selectedDiscount.descriptionLabel ?: "Motiv reducere"

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = descriptionText,
                onValueChange = { onDescriptionChange(it) },
                label = { Text(labelText) },
                singleLine = true
            )
        }
    }
}


