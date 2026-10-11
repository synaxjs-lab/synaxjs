package com.synax.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synax.app.data.SynaxApi
import com.synax.app.data.SynaxUser
import com.synax.app.data.toSynaxUser
import com.synax.app.security.SecureTokens
import kotlinx.coroutines.launch
import org.json.JSONObject

val VoidBlack = Color(0xFF050711)
val Panel = Color(0xFF0B1222)
val Border = Color(0xFF22314B)
val Violet = Color(0xFF6040F5)
val Cyan = Color(0xFF8BCBFF)
val Pink = Color(0xFFEC4899)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 7, 17)
        window.navigationBarColor = android.graphics.Color.rgb(5, 7, 17)
        setContent {
            val dark = darkColorScheme(
                primary = Violet, secondary = Pink, background = VoidBlack, surface = Panel,
                onBackground = Color(0xFFF2F5FF), onSurface = Color(0xFFF2F5FF), outline = Border
            )
            MaterialTheme(colorScheme = dark, typography = Typography()) {
                Surface(Modifier.fillMaxSize(), color = VoidBlack) {
                    SynaxApp(applicationContext)
                }
            }
        }
    }
}

@Composable
private fun SynaxApp(context: android.content.Context) {
    val api = remember { SynaxApi(context) }
    val tokens = remember { SecureTokens(context) }
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf("loading") }
    var authToken by remember { mutableStateOf("") }
    var currentUser by remember { mutableStateOf<SynaxUser?>(null) }
    var otherUser by remember { mutableStateOf<SynaxUser?>(null) }
    var timeStatus by remember { mutableStateOf(JSONObject()) }
    var settings by remember { mutableStateOf(JSONObject()) }
    var identities by remember { mutableStateOf(JSONObject()) }
    var globalError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try { identities = api.publicIdentities() } catch (e: Exception) { globalError = e.message ?: "Cannot connect to SYNAX." }
        val savedAdmin = tokens.read("admin")
        val savedUser = tokens.read("user")
        when {
            !savedAdmin.isNullOrBlank() -> try {
                api.adminOverview(savedAdmin)
                authToken = savedAdmin; route = "admin"
            } catch (_: Exception) { tokens.clear("admin"); route = "login" }
            !savedUser.isNullOrBlank() -> try {
                val me = api.me(savedUser)
                if (me.optString("role") == "admin") throw IllegalStateException("Admin token cannot be used as a user token.")
                authToken = savedUser
                currentUser = me.optJSONObject("user")?.toSynaxUser() ?: throw IllegalStateException("Invalid profile response.")
                otherUser = me.optJSONObject("otherUser")?.toSynaxUser() ?: SynaxUser(if (currentUser?.id == "person_1") "person_2" else "person_1", "SYNAX")
                timeStatus = me.optJSONObject("timeStatus") ?: JSONObject()
                settings = me.optJSONObject("settings") ?: JSONObject()
                route = "chat"
            } catch (_: Exception) { tokens.clear("user"); route = "login" }
            else -> route = "login"
        }
    }

    when (route) {
        "loading" -> LoadingScreen()
        "login" -> LoginScreen(
            identities = identities,
            initialError = globalError,
            onLoginUser = { id, password ->
                scope.launch {
                    globalError = ""
                    try {
                        val result = api.login(id, password)
                        val token = result.getString("token")
                        tokens.clear("admin"); tokens.save("user", token)
                        authToken = token
                        currentUser = result.optJSONObject("user")?.toSynaxUser(id) ?: throw IllegalStateException("Invalid user information returned by server.")
                        otherUser = result.optJSONObject("otherUser")?.toSynaxUser() ?: SynaxUser(if (id == "person_1") "person_2" else "person_1", "SYNAX")
                        timeStatus = result.optJSONObject("timeStatus") ?: JSONObject()
                        settings = result.optJSONObject("settings") ?: JSONObject()
                        route = "chat"
                    } catch (e: Exception) { globalError = e.message ?: "Login failed." }
                }
            },
            onLoginAdmin = { password ->
                scope.launch {
                    globalError = ""
                    try {
                        val result = api.adminLogin(password)
                        val token = result.getString("token")
                        tokens.clear("user"); tokens.save("admin", token)
                        authToken = token
                        settings = result.optJSONObject("settings") ?: JSONObject()
                        route = "admin"
                    } catch (e: Exception) { globalError = e.message ?: "Admin login failed." }
                }
            }
        )
        "chat" -> {
            val user = currentUser
            if (user == null) LoadingScreen() else UserChatScreen(
                api = api, token = authToken, me = user, otherInitial = otherUser ?: SynaxUser("person_2", "SYNAX"),
                settingsInitial = settings, timeInitial = timeStatus,
                onLogout = {
                    scope.launch { api.logout(authToken); tokens.clear("user"); authToken = ""; currentUser = null; otherUser = null; route = "login" }
                }
            )
        }
        "admin" -> AdminScreen(
            api = api, token = authToken,
            onLogout = { scope.launch { api.logout(authToken); tokens.clear("admin"); authToken = ""; settings = JSONObject(); route = "login" } }
        )
        else -> LoadingScreen()
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(VoidBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("S Y N A X", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            CircularProgressIndicator(color = Violet)
            Text("Opening your private sanctuary…", color = Cyan, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LoginScreen(
    identities: JSONObject,
    initialError: String,
    onLoginUser: (String, String) -> Unit,
    onLoginAdmin: (String) -> Unit
) {
    var mode by remember { mutableStateOf("user") }
    var selectedUser by remember { mutableStateOf("person_1") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(initialError) { if (initialError.isNotBlank()) error = initialError }
    val personOne = identities.optJSONObject("person_1")?.optString("name", "Sopie") ?: "Sopie"
    val personTwo = identities.optJSONObject("person_2")?.optString("name", "Souie") ?: "Souie"
    Box(Modifier.fillMaxSize().background(VoidBlack).padding(22.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text("◈", color = Violet, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("S Y N A X", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("PRIVATE SANCTUARY", fontSize = 10.sp, color = Cyan, letterSpacing = 2.sp)
                    }
                }
                Text(identities.optString("worldSubtitle", "When two opposite angles get attracted to each other"), color = Color.LightGray, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("User Chat", mode == "user", Modifier.weight(1f)) { mode = "user"; error = "" }
                    ModeButton("Admin Portal", mode == "admin", Modifier.weight(1f)) { mode = "admin"; error = "" }
                }
                if (mode == "user") {
                    Text("CHOOSE IDENTITY", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeButton(personOne, selectedUser == "person_1", Modifier.weight(1f)) { selectedUser = "person_1" }
                        ModeButton(personTwo, selectedUser == "person_2", Modifier.weight(1f)) { selectedUser = "person_2" }
                    }
                } else {
                    Text("ADMIN MASTER SANCTUARY", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = "" }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (mode == "admin") "Administrator password" else "Account password") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF8B98), fontSize = 12.sp)
                Button(
                    onClick = { if (password.isBlank()) error = "Enter your password." else if (mode == "admin") onLoginAdmin(password) else onLoginUser(selectedUser, password) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (mode == "admin") Color(0xFF8F174F) else Violet)
                ) { Text(if (mode == "admin") "ENTER ADMIN SANCTUARY" else "ENTER SYNAX", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                Text("Connected to synax.online", modifier = Modifier.fillMaxWidth(), color = Color(0xFF8194B3), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Violet.copy(alpha = .22f) else VoidBlack),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Violet else Border),
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
    ) { Text(label, color = if (selected) Color.White else Cyan, fontSize = 12.sp, maxLines = 2) }
}
