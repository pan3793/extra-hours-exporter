import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Notification.Type.*
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.singleWindowApplication
import java.time.LocalDate

@OptIn(ExperimentalMaterialApi::class)
fun main() = singleWindowApplication(
    title = "Extra Hours Exporter"
) {
    // state
    var usernameOrPasswordError by remember { mutableStateOf(false) }
    val trayState = rememberTrayState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var month by remember { mutableStateOf(LocalDate.now().monthValue.toString()) }

    // handler
    fun notice(level: Notification.Type, title: String, message: String?) {
        trayState.sendNotification(Notification(title, message ?: "Nothing", level))
    }

    fun onClickExport() {
        if (!OAService.isLogin) {
            try {
                OAService.login(username, password)
            } catch (ex: Exception) {
                usernameOrPasswordError = true
                notice(Error, "Login Failed", ex.message)
            }
        }
        if (OAService.isLogin) {
            OAService.baseInfo()
            OAService.monthRecords(year.toInt(), month.toInt())
        }
    }

    // UI
    Tray(ColorPainter(Color.Blue), trayState)

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(8.dp)) {
            Column {
                Text("LDAP Username")
                TextField(
                    username,
                    isError = usernameOrPasswordError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { username = it },
                )
            }

            Column {
                Text("LDAP Password")
                TextField(
                    password,
                    isError = usernameOrPasswordError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    onValueChange = { password = it },
                )
            }

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column(modifier = Modifier.padding(end = 4.dp)) {
                    Text("Year")
                    TextField(
                        year,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        onValueChange = { year = it },
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                    Text("Month")
                    TextField(
                        month,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { month = it },
                    )
                }
            }

            Column {
                Text("Why do you need to work overtime?")
                TextField(
                    reason,
                    isError = reason.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { reason = it },
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::onClickExport

                ) {
                    Text("Export")
                }
            }
        }
    }
}