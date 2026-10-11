package com.synax.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synax.app.data.SynaxApi
import com.synax.app.data.SynaxMessage
import com.synax.app.data.SynaxUser
import com.synax.app.data.asClock
import com.synax.app.data.toSynaxMessage
import com.synax.app.data.toSynaxUser
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val AdminSections = listOf("Overview", "People", "Logos & Identity", "Time Controls", "Features & Calling", "Atmosphere & Theme", "Message Audit", "Lockdown & Keys", "Audit Trail", "Database & Reset")
private val LogoPresets = listOf(
    JSONObject().put("id", "astral_crest").put("name", "Astral Crest").put("accentColor", "#6366f1").put("glowColor", "rgba(99, 102, 241, 0.5)").put("svgPath", "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26Z"),
    JSONObject().put("id", "lunar_chrono").put("name", "Lunar Chrono").put("accentColor", "#ec4899").put("glowColor", "rgba(236, 72, 153, 0.5)").put("svgPath", "M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 0 1-4.4 2.26 5.403 5.403 0 0 1-3.14-9.8c-.44-.06-.9-.1-1.36-.1z"),
    JSONObject().put("id", "solaris_prism").put("name", "Solaris Prism").put("accentColor", "#38bdf8").put("glowColor", "rgba(56, 189, 248, 0.4)").put("svgPath", "M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"),
    JSONObject().put("id", "cyber_nexus").put("name", "Cyber Nexus").put("accentColor", "#10b981").put("glowColor", "rgba(16, 185, 129, 0.4)").put("svgPath", "M12 2l8 4.5v9L12 20l-8-4.5v-9L12 2z"),
    JSONObject().put("id", "valkyrie_wings").put("name", "Valkyrie Wings").put("accentColor", "#f59e0b").put("glowColor", "rgba(245, 158, 11, 0.4)").put("svgPath", "M12 2L3 9l9 13 9-13-9-7z"),
    JSONObject().put("id", "ethereal_lotus").put("name", "Ethereal Sigil").put("accentColor", "#a855f7").put("glowColor", "rgba(168, 85, 247, 0.4)").put("svgPath", "M12 2c2.5 4 6 7.5 6 10a6 6 0 0 1-12 0c0-2.5 3.5-6 6-10z")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(api: SynaxApi, token: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf("Overview") }
    var overview by remember { mutableStateOf(JSONObject()) }
    var settings by remember { mutableStateOf(JSONObject()) }
    var users by remember { mutableStateOf(listOf<SynaxUser>()) }
    var messages by remember { mutableStateOf(listOf<SynaxMessage>()) }
    var logs by remember { mutableStateOf(JSONArray()) }
    var messageQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf("") }
    var targetMessage by remember { mutableStateOf<SynaxMessage?>(null) }
    var adminCurrentPassword by remember { mutableStateOf("") }
    var adminNewPassword by remember { mutableStateOf("") }
    var lockdownMessage by remember { mutableStateOf("SYNAX is temporarily locked by the Administrator.") }
    var exportJson by remember { mutableStateOf("") }

    suspend fun refreshAll() {
        loading = true; error = ""
        try {
            overview = api.adminOverview(token)
            settings = overview.optJSONObject("settings") ?: JSONObject()
            users = listOf("person_1", "person_2").mapNotNull { overview.optJSONObject("users")?.optJSONObject(it)?.toSynaxUser(it) }
            messages = api.adminMessages(token)
            logs = api.adminLogs(token)
            lockdownMessage = settings.optString("lockdownMessage", lockdownMessage)
            notice = "SYNAX data synchronized."
        } catch (e: Exception) { error = e.message ?: "Admin data could not be loaded." }
        finally { loading = false }
    }

    LaunchedEffect(token) { refreshAll() }

    fun doAction() { scope.launch { refreshAll() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && exportJson.isNotBlank()) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { notice = "JSON snapshot exported." }
                .onFailure { error = it.message ?: "Export failed." }
        }
    }

    Scaffold(
        containerColor = VoidBlack,
        topBar = {
            TopAppBar(
                title = { Column { Text("SYNAX CONTROL", fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp); Text("ADMIN MASTER SANCTUARY", color = Cyan, fontSize = 10.sp, letterSpacing = 1.5.sp) } },
                actions = {
                    IconButton(onClick = { scope.launch { refreshAll() } }) { Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Cyan) }
                    TextButton(onClick = { confirmAction = "logout" }) { Text("Exit", color = Pink) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Panel, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Panel).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdminSections.forEach { item ->
                    FilterChip(selected = section == item, onClick = { section = item; error = "" }, label = { Text(item, fontSize = 11.sp, maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet, selectedLabelColor = Color.White))
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Violet)
            if (notice.isNotBlank()) Text(notice, Modifier.fillMaxWidth().background(Color(0xFF071D1A)).padding(8.dp), color = Color(0xFF78E5BF), fontSize = 11.sp)
            if (error.isNotBlank()) Text(error, Modifier.fillMaxWidth().background(Color(0xFF2A0B18)).padding(8.dp), color = Color(0xFFFF9CB7), fontSize = 12.sp)
            when (section) {
                "Overview" -> AdminOverview(overview, settings, users, onRefresh = ::doAction, onQuickAction = { action, minutes -> scope.launch { try { if (action == "reset") { api.adminAdjustTime(token, "person_1", JSONObject().put("resetSession", true)); api.adminAdjustTime(token, "person_2", JSONObject().put("resetSession", true)) } else { api.adminAdjustTime(token, "person_1", JSONObject().put("addMinutes", minutes)); api.adminAdjustTime(token, "person_2", JSONObject().put("addMinutes", minutes)) }; refreshAll(); notice = "Quick time intervention confirmed." } catch (e: Exception) { error = e.message ?: "Time intervention failed." } } })
                "People" -> PeopleSection(users, onSave = { userId, name, username, password ->
                    scope.launch {
                        try {
                            val payload = JSONObject().put("name", name.trim()).put("username", username.trim())
                            if (password.isNotBlank()) payload.put("password", password)
                            api.adminUpdateUser(token, userId, payload)
                            refreshAll(); notice = "${userId.replace('_', ' ')} account updated."
                        } catch (e: Exception) { error = e.message ?: "User update failed." }
                    }
                }, onLock = { userId, locked -> scope.launch { try { api.adminLockUser(token, userId, locked); refreshAll() } catch (e: Exception) { error = e.message ?: "Lock update failed." } } })
                "Logos & Identity" -> LogosSection(users, onSave = { userId, logo -> scope.launch { try { api.adminUpdateUser(token, userId, JSONObject().put("logo", logo)); refreshAll(); notice = "Logo mark saved." } catch (e: Exception) { error = e.message ?: "Logo update failed." } } })
                "Time Controls" -> TimeControlsSection(settings, users, onSaveSettings = { candidate -> scope.launch { try { settings = api.adminUpdateSettings(token, candidate); refreshAll(); notice = "Time rules saved." } catch (e: Exception) { error = e.message ?: "Time rules failed." } } }, onTime = { id, options -> scope.launch { try { api.adminAdjustTime(token, id, options); refreshAll(); notice = "Time update confirmed by server." } catch (e: Exception) { error = e.message ?: "Time update failed." } } })
                "Features & Calling" -> FeatureSection(settings, onSave = { candidate -> scope.launch { try { settings = api.adminUpdateSettings(token, candidate); refreshAll(); notice = "Feature settings saved." } catch (e: Exception) { error = e.message ?: "Settings update failed." } } })
                "Atmosphere & Theme" -> AtmosphereSection(settings, onSave = { candidate -> scope.launch { try { settings = api.adminUpdateSettings(token, candidate); refreshAll(); notice = "Atmosphere updated." } catch (e: Exception) { error = e.message ?: "Atmosphere update failed." } } })
                "Message Audit" -> Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(messageQuery, { messageQuery = it }, modifier = Modifier.fillMaxWidth().padding(12.dp), label = { Text("Search messages, sender, attachment or ID") }, singleLine = true)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${messages.size} stored messages", color = Cyan, fontSize = 12.sp)
                        TextButton(onClick = { confirmAction = "clear_messages" }) { Text("Clear All Chat History", color = Pink, fontSize = 11.sp) }
                    }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(messages.filter { messageQuery.isBlank() || listOf(it.id, it.text, it.senderId, it.fileName, it.type).any { v -> v.contains(messageQuery, true) } }, key = { it.id }) { message -> AdminMessageCard(message, onDelete = { targetMessage = message; confirmAction = "delete_message" }) }
                    }
                }
                "Lockdown & Keys" -> LockdownSection(settings, lockdownMessage, { lockdownMessage = it }, adminCurrentPassword, { adminCurrentPassword = it }, adminNewPassword, { adminNewPassword = it }, onSaveLockdown = { locked ->
                    val candidate = JSONObject(settings.toString()).put("lockdownMessage", lockdownMessage).put("emergencyLockdown", locked)
                    scope.launch { try { settings = api.adminUpdateSettings(token, candidate); refreshAll(); notice = if (settings.optBoolean("emergencyLockdown")) "Lockdown enabled. User requests are blocked server-side." else "Lockdown is disabled." } catch (e: Exception) { error = e.message ?: "Lockdown setting failed." } }
                }, onChangePassword = {
                    scope.launch { try { api.adminChangePassword(token, adminCurrentPassword, adminNewPassword); adminCurrentPassword = ""; adminNewPassword = ""; notice = "Administrator password changed." } catch (e: Exception) { error = e.message ?: "Password change failed." } }
                })
                "Audit Trail" -> AuditSection(logs)
                "Database & Reset" -> DatabaseSection(onExport = {
                    scope.launch { try {
                        val snap = JSONObject().put("generatedAt", System.currentTimeMillis()).put("users", overview.optJSONObject("users") ?: JSONObject()).put("settings", settings)
                            .put("messages", JSONArray().apply { messages.forEach { put(it.raw) } }).put("logs", logs)
                        exportJson = snap.toString(2); exportLauncher.launch("synax-snapshot-${System.currentTimeMillis()}.json")
                    } catch (e: Exception) { error = e.message ?: "Snapshot export failed." } }
                }, onReset = { confirmAction = "reset_defaults" })
                else -> AdminOverview(overview, settings, users, onRefresh = ::doAction, onQuickAction = { action, minutes -> scope.launch { try { if (action == "reset") { api.adminAdjustTime(token, "person_1", JSONObject().put("resetSession", true)); api.adminAdjustTime(token, "person_2", JSONObject().put("resetSession", true)) } else { api.adminAdjustTime(token, "person_1", JSONObject().put("addMinutes", minutes)); api.adminAdjustTime(token, "person_2", JSONObject().put("addMinutes", minutes)) }; refreshAll() } catch (e: Exception) { error = e.message ?: "Time intervention failed." } } })
            }
        }
    }

    when (confirmAction) {
        "delete_message" -> AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text("Delete this message?") }, text = { Text("${targetMessage?.senderId ?: "Unknown sender"}: ${targetMessage?.text?.take(180).orEmpty().ifBlank { targetMessage?.fileName ?: "Attachment / system message" }}\n\nThis removes only the selected message from active history and records an audit event.") }, confirmButton = {
            TextButton(onClick = {
                val id = targetMessage?.id ?: ""
                confirmAction = ""
                scope.launch { try { api.adminDeleteMessage(token, id); messages = api.adminMessages(token); logs = api.adminLogs(token); notice = "Selected message deleted." } catch (e: Exception) { error = e.message ?: "Message deletion failed." } }
            }) { Text("Delete message", color = Pink) }
        }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Cancel") } })
        "clear_messages" -> AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text("Clear entire chat history?") }, text = { Text("This affects both participants. It is separate from deleting a single message and cannot be undone from this screen.") }, confirmButton = { TextButton(onClick = { confirmAction = ""; scope.launch { try { api.adminClearMessages(token); refreshAll(); notice = "Chat history cleared." } catch (e: Exception) { error = e.message ?: "Clear history failed." } } }) { Text("Clear all", color = Pink) } }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Cancel") } })
        "reset_defaults" -> AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text("Reset factory defaults?") }, text = { Text("This destructive action can reset both identities, settings, time rules and conversation history. It runs only after confirmation.") }, confirmButton = { TextButton(onClick = { confirmAction = ""; scope.launch { try { api.adminResetDefaults(token); refreshAll(); notice = "Factory defaults restored." } catch (e: Exception) { error = e.message ?: "Reset failed." } } }) { Text("Reset defaults", color = Pink) } }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Cancel") } })
        "logout" -> AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text("Exit Admin Portal?") }, text = { Text("End the administrator session and return to login.") }, confirmButton = { TextButton(onClick = { confirmAction = ""; onLogout() }) { Text("Exit", color = Pink) } }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Cancel") } })
    }

}

@Composable
private fun AdminOverview(overview: JSONObject, settings: JSONObject, users: List<SynaxUser>, onRefresh: () -> Unit, onQuickAction: (String, Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("SYSTEM DASHBOARD", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Real-time status of the private sanctuary for both participants.", color = Cyan, fontSize = 12.sp) }
        item {
            val p1 = overview.optJSONObject("users")?.optJSONObject("person_1")?.optJSONObject("timeStatus")
            val total = overview.optInt("totalMessages", 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("SANCTUARY HEALTH", if (overview.length() > 0) "ONLINE & SECURE" else "CHECK CONNECTION", if (overview.length() > 0) "Server response confirmed" else "Refresh to retry", Modifier.weight(1f))
                SummaryCard("TIME REMAINING", p1?.optLong("remainingSeconds", 0)?.asClock() ?: "—", "Server-controlled timer", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("MESSAGES STORED", total.toString(), "Persistent chat database", Modifier.weight(1f))
                SummaryCard("LOCKDOWN STATUS", if (settings.optBoolean("emergencyLockdown")) "LOCKDOWN" else "UNLOCKED", if (settings.optBoolean("emergencyLockdown")) "Users blocked by server" else "Emergency switch off", Modifier.weight(1f))
            }
        }
        item { SectionCard("QUICK SERVER TIME INTERVENTIONS") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onQuickAction("grant", 15) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("+15 Minutes", fontSize = 11.sp) }
                Button(onClick = { onQuickAction("grant", 30) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("+30 Minutes", fontSize = 11.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onQuickAction("reset", 0) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("Reset Session Usage", fontSize = 11.sp) }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text("Sync Live Status", fontSize = 11.sp) }
            }
        } }
        item { users.forEach { user -> SectionCard(user.name.uppercase()) {
            Text("@${user.username}", color = Cyan)
            Text("Mark: ${user.logoName.ifBlank { "Professional logo" }}", fontSize = 12.sp)
            Text("Configured limit: ${user.allowedMinutes} minutes", color = Color.LightGray, fontSize = 12.sp)
            Text(if (user.isLocked) "Account locked" else "Account active", color = if (user.isLocked) Pink else Color(0xFF5DE1AE), fontSize = 12.sp)
        }; Spacer(Modifier.height(8.dp)) } }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Color(0xFF8DA0BE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = if (label == "LOCKDOWN STATUS" && value == "LOCKDOWN") Pink else Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Color(0xFF8DA0BE), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun PeopleSection(users: List<SynaxUser>, onSave: (String, String, String, String) -> Unit, onLock: (String, Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Text("PERSON 1 & PERSON 2 MANAGEMENT", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Configure identities and account lock states.", color = Cyan, fontSize = 12.sp) }
        items(users, key = { it.id }) { user ->
            var name by remember(user.id, user.name) { mutableStateOf(user.name) }
            var username by remember(user.id, user.username) { mutableStateOf(user.username) }
            var password by remember(user.id) { mutableStateOf("") }
            var locked by remember(user.id, user.isLocked) { mutableStateOf(user.isLocked) }
            SectionCard("${user.id.uppercase()} ACCOUNT") {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New password (leave blank to keep current)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(locked, { locked = it; onLock(user.id, it) }); Text("Account locked", color = if (locked) Pink else Color.LightGray) }
                Button(onClick = { onSave(user.id, name, username, password); password = "" }, modifier = Modifier.fillMaxWidth()) { Text("Save ${user.id.replace('_', ' ').uppercase()}") }
            }
        }
    }
}

@Composable
private fun LogosSection(users: List<SynaxUser>, onSave: (String, JSONObject) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Text("PROFESSIONAL LOGOS & IDENTITY", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Personal portraits remain separate from professional marks.", color = Cyan, fontSize = 12.sp) }
        items(users, key = { it.id }) { user ->
            SectionCard("${user.name.uppercase()} — PROFESSIONAL MARK") {
                Text("Current mark: ${user.logoName.ifBlank { "Not set" }}", color = Cyan)
                LogoPresets.forEach { preset ->
                    OutlinedButton(onClick = { onSave(user.id, JSONObject(preset.toString())) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (user.raw.optJSONObject("logo")?.optString("id") == preset.optString("id")) Violet else Border), contentPadding = PaddingValues(8.dp)) {
                        Text("✦  ${preset.optString("name")}  •  ${preset.optString("accentColor")}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeControlsSection(settings: JSONObject, users: List<SynaxUser>, onSaveSettings: (JSONObject) -> Unit, onTime: (String, JSONObject) -> Unit) {
    var mode by remember(settings) { mutableStateOf(settings.optString("timeStrategy", "daily")) }
    var showTimer by remember(settings) { mutableStateOf(settings.optBoolean("showTimerToUsers", true)) }
    var dailyReset by remember(settings) { mutableStateOf(settings.optBoolean("dailyResetAtMidnight", true)) }
    var expiration by remember(settings) { mutableStateOf(settings.optString("timeOverMessage", "Your time in SYNAX has ended.")) }
    var disableMessages by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("disableMessaging", true) ?: true) }
    var disableVoice by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("disableVoiceCalls", true) ?: true) }
    var disableVideo by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("disableVideoCalls", true) ?: true) }
    var disableImages by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("disableImages", true) ?: true) }
    var disableFiles by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("disableFiles", true) ?: true) }
    var disableVoiceMessages by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("disableVoiceMessages", true) ?: true) }
    var lockSession by remember(settings) { mutableStateOf(settings.optJSONObject("restrictionsOnExpire")?.optBoolean("lockSession", false) ?: false) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { Text("SERVER-SIDE AUTHORITATIVE TIME CONTROLS", fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("The server remains the source of truth for both users.", color = Cyan, fontSize = 12.sp) }
        item { SectionCard("GLOBAL TIME RULES") {
            Text("Tracking mode", color = Cyan, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ModeButton("Daily Allowed Time", mode == "daily", Modifier.weight(1f)) { mode = "daily" }; ModeButton("Continuous", mode == "continuous", Modifier.weight(1f)) { mode = "continuous" } }
            SettingSwitch("Reset daily at midnight India time", dailyReset) { dailyReset = it }
            SettingSwitch("Show countdown to users", showTimer) { showTimer = it }
            OutlinedTextField(expiration, { expiration = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Expiration message") }, minLines = 2)
        } }
        items(users, key = { it.id }) { user ->
            var exact by remember(user.id, user.allowedMinutes) { mutableStateOf(user.allowedMinutes.toString()) }
            SectionCard("${user.name.uppercase()} — ${user.id}") {
                Text("Remaining time: ${user.raw.optJSONObject("timeStatus")?.optLong("remainingSeconds", 0)?.asClock() ?: "Refresh to check"}", color = Cyan, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(exact, { exact = it.filter(Char::isDigit) }, modifier = Modifier.weight(1f), label = { Text("Exact limit (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Button(onClick = { val m = exact.toIntOrNull(); if (m != null && m >= 1) onTime(user.id, JSONObject().put("setMinutes", m)) }) { Text("Set limit") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onTime(user.id, JSONObject().put("addMinutes", 5)) }, modifier = Modifier.weight(1f)) { Text("+5m") }
                    OutlinedButton(onClick = { onTime(user.id, JSONObject().put("addMinutes", 15)) }, modifier = Modifier.weight(1f)) { Text("+15m") }
                    OutlinedButton(onClick = { onTime(user.id, JSONObject().put("addMinutes", -5)) }, modifier = Modifier.weight(1f)) { Text("−5m") }
                }
                Button(onClick = { onTime(user.id, JSONObject().put("resetSession", true)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C2A16))) { Text("Reset Usage (keep configured limit)") }
            }
        }
        item { SectionCard("RESTRICTIONS WHEN TIME EXPIRES") {
            SettingSwitch("Disable sending messages", disableMessages) { disableMessages = it }
            SettingSwitch("Disable voice calls", disableVoice) { disableVoice = it }
            SettingSwitch("Disable video calls", disableVideo) { disableVideo = it }
            SettingSwitch("Disable image uploads", disableImages) { disableImages = it }
            SettingSwitch("Disable file uploads", disableFiles) { disableFiles = it }
            SettingSwitch("Disable voice messages", disableVoiceMessages) { disableVoiceMessages = it }
            SettingSwitch("Lock session when time reaches zero", lockSession) { lockSession = it }
            Button(onClick = {
                val candidate = JSONObject().put("timeStrategy", mode).put("dailyResetAtMidnight", dailyReset).put("showTimerToUsers", showTimer).put("timeOverMessage", expiration)
                candidate.put("restrictionsOnExpire", JSONObject().put("disableMessaging", disableMessages).put("disableVoiceCalls", disableVoice).put("disableVideoCalls", disableVideo).put("disableImages", disableImages).put("disableFiles", disableFiles).put("disableVoiceMessages", disableVoiceMessages).put("lockSession", lockSession))
                onSaveSettings(candidate)
            }, modifier = Modifier.fillMaxWidth()) { Text("UPDATE TIME RULES") }
        } }
    }
}

@Composable
private fun FeatureSection(settings: JSONObject, onSave: (JSONObject) -> Unit) {
    val initial = settings.optJSONObject("featuresEnabled") ?: JSONObject()
    val keys = listOf("editing" to "Editing", "deleting" to "Deleting", "messages" to "Messages", "reactions" to "Reactions", "videoCalls" to "Video Calls", "voiceCalls" to "Voice Calls", "fileSharing" to "File Sharing", "imageSharing" to "Image Sharing", "voiceMessages" to "Voice Messages")
    var enabled by remember(settings) { mutableStateOf(keys.associate { it.first to initial.optBoolean(it.first, true) }) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FEATURE & COMMUNICATION CONTROLS", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Changes are enforced by the existing server API.", color = Cyan, fontSize = 12.sp)
        SectionCard("FEATURE FLAGS") {
            keys.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { (key, label) -> Column(Modifier.weight(1f)) { SettingSwitch(label, enabled[key] ?: true) { value -> enabled = enabled.toMutableMap().apply { put(key, value) } } } }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
            Button(onClick = { onSave(JSONObject().put("featuresEnabled", JSONObject().apply { enabled.forEach { (key, value) -> put(key, value) } })) }, modifier = Modifier.fillMaxWidth()) { Text("SAVE FEATURE SETTINGS") }
        }
    }
}

@Composable
private fun AtmosphereSection(settings: JSONObject, onSave: (JSONObject) -> Unit) {
    var title by remember(settings) { mutableStateOf(settings.optString("worldTitle", "SYNAX")) }
    var subtitle by remember(settings) { mutableStateOf(settings.optString("worldSubtitle", "When two opposite angles get attracted to each other")) }
    var theme by remember(settings) { mutableStateOf(settings.optString("theme", "celestial")) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ATMOSPHERE & APPEARANCE", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        SectionCard("COSMIC ATMOSPHERE") {
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("World title") }, singleLine = true)
            OutlinedTextField(subtitle, { subtitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Subtitle") }, minLines = 2)
            Text("Cosmic theme", color = Cyan, fontSize = 12.sp)
            listOf("celestial", "cyberpunk", "twilight", "aurora").forEach { name -> ModeButton(name.replaceFirstChar(Char::uppercase), theme == name, Modifier.fillMaxWidth()) { theme = name } }
            Button(onClick = { onSave(JSONObject().put("worldTitle", title).put("worldSubtitle", subtitle).put("theme", theme)) }, modifier = Modifier.fillMaxWidth()) { Text("UPDATE ATMOSPHERE") }
        }
    }
}

@Composable
private fun AdminMessageCard(message: SynaxMessage, onDelete: () -> Unit) {
    SectionCard("${message.senderId} • ${message.type.uppercase()}") {
        Text(message.text.ifBlank { if (message.deleted) "Message deleted" else message.fileName.ifBlank { "No text content" } }, color = Color.White, fontSize = 13.sp)
        Text("ID: ${message.id}", color = Color.Gray, fontSize = 9.sp)
        Text(if (message.timestamp > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)) else "Timestamp unavailable", color = Cyan, fontSize = 10.sp)
        if (message.fileUrl.isNotBlank() && !message.deleted) Text("Attachment: ${message.fileName.ifBlank { message.fileUrl }}", color = Cyan, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete, enabled = !message.deleted) { Text(if (message.deleted) "Already deleted" else "Delete message", color = Pink) }
        }
    }
}

@Composable
private fun LockdownSection(settings: JSONObject, message: String, onMessage: (String) -> Unit, currentPassword: String, onCurrent: (String) -> Unit, newPassword: String, onNew: (String) -> Unit, onSaveLockdown: (Boolean) -> Unit, onChangePassword: () -> Unit) {
    var locked by remember(settings) { mutableStateOf(settings.optBoolean("emergencyLockdown", false)) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SECURITY & MASTER PASSWORD", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        SectionCard("EMERGENCY SANCTUARY LOCKDOWN") {
            Text("Blocks authenticated user API requests while enabled.", color = Cyan, fontSize = 12.sp)
            SettingSwitch("Enable emergency lockdown", locked) { locked = it }
            OutlinedTextField(message, onMessage, modifier = Modifier.fillMaxWidth(), label = { Text("Lockdown message") }, minLines = 2)
            Button(onClick = { onSaveLockdown(locked) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (locked) Color(0xFFAE0030) else Violet)) { Text("SAVE LOCKDOWN STATE") }
            Text("Use with care. Users may need to reopen the app after unlocking.", color = Color.Gray, fontSize = 10.sp)
        }
        SectionCard("CHANGE MASTER ADMIN PASSWORD") {
            OutlinedTextField(currentPassword, onCurrent, modifier = Modifier.fillMaxWidth(), label = { Text("Current admin password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(newPassword, onNew, modifier = Modifier.fillMaxWidth(), label = { Text("New admin password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(onClick = onChangePassword, modifier = Modifier.fillMaxWidth(), enabled = currentPassword.isNotBlank() && newPassword.length >= 4) { Text("UPDATE ADMIN PASSWORD") }
        }
    }
}

@Composable
private fun AuditSection(logs: JSONArray) {
    val list = remember(logs) { (0 until logs.length()).mapNotNull { logs.optJSONObject(it) } }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Text("SYSTEM AUDIT TRAIL", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Chronological administrator and system events.", color = Cyan, fontSize = 12.sp) }
        if (list.isEmpty()) item { Text("No audit events returned by the server.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
        items(list, key = { it.optString("id") }) { item ->
            SectionCard(item.optString("actor", "System")) {
                Text(item.optString("action", "Event"), color = Color.White, fontWeight = FontWeight.SemiBold)
                if (item.optString("details").isNotBlank()) Text(item.optString("details"), color = Color.LightGray, fontSize = 12.sp)
                val timestamp = item.optLong("timestamp", 0)
                Text(if (timestamp > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp)) else "Timestamp unavailable", color = Cyan, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DatabaseSection(onExport: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DATABASE SNAPSHOT & BACKUP", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        SectionCard("EXPORT DATABASE SNAPSHOT") {
            Text("Export the administrator-authorized overview, current messages, settings, and audit events as JSON. Passwords and tokens are excluded.", color = Color.LightGray, fontSize = 12.sp)
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Storage, null); Spacer(Modifier.width(8.dp)); Text("DOWNLOAD JSON SNAPSHOT") }
        }
        SectionCard("RESET FACTORY DEFAULTS") {
            Text("Restores default participant identities, settings, logos, and time rules. This can clear the active conversation.", color = Color(0xFFFF9CB7), fontSize = 12.sp)
            Button(onClick = onReset, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F1730))) { Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text("RESET DEFAULTS") }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFFD9E3F5))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
