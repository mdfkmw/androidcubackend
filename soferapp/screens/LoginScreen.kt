package ro.priscom.sofer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ro.priscom.sofer.ui.data.local.LocalRepository
import ro.priscom.sofer.ui.data.remote.RemoteRepository
import androidx.compose.ui.graphics.Color
import ro.priscom.sofer.ui.data.DriverLocalStore

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { LocalRepository(context) }
    val scope = rememberCoroutineScope()

    var driverIdText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)   // 👈 asta acoperă complet ce e dedesubt
            .padding(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Autentificare șofer")
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = driverIdText,
            onValueChange = { driverIdText = it },
            label = { Text("ID șofer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = passwordText,
            onValueChange = { passwordText = it },
            label = { Text("Parolă") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    error = null

                    val id = driverIdText.toIntOrNull()
                    if (id == null) {
                        error = "ID invalid"
                        return@launch
                    }

                    // 1. Încearcă backend login
                    val remote = RemoteRepository()
                    val backendUser = remote.login(driverIdText, passwordText)

                    if (backendUser != null) {
                        DriverLocalStore.setEmployeeId(backendUser.id)
                        DriverLocalStore.setOperatorId(backendUser.operator_id)
                        onLoginSuccess(driverIdText)
                        return@launch
                    }


                    // 2. Dacă eșuează, fallback la login local
                    val driver = repo.getDriver(id)

                    if (driver == null) {
                        error = "Nu există șofer cu acest ID"
                        return@launch
                    }

                    if (driver.password != passwordText) {
                        error = "Parolă greșită"
                        return@launch
                    }

                    DriverLocalStore.setEmployeeId(driver.id)
                    DriverLocalStore.setOperatorId(driver.operatorId)

                    onLoginSuccess(driverIdText)

                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Intră")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Renunță")
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(text = error ?: "")
        }
    }
}
