package com.synax.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.synax.app.data.SynaxApi
import com.synax.app.data.SynaxMessage
import com.synax.app.data.SynaxRealtime
import com.synax.app.data.SynaxTime
import com.synax.app.data.SynaxUser
import com.synax.app.data.asClock
import com.synax.app.data.toSynaxMessage
import com.synax.app.data.toSynaxTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserChatScreen(api: SynaxApi, token: String, me: SynaxUser, otherInitial: SynaxUser, settingsInitial: JSONObject, timeInitial: JSONObject, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<SynaxMessage>() }
    var other by remember { mutableStateOf(otherInitial) }
    var time by remember { mutableStateOf(timeInitial.toSynaxTime()) }
    var connected by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<SynaxMessage?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedDialog by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<SynaxMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var background by remember { mutableStateOf(JSONObject()) }
    var profilePhotoUrl by remember { mutableStateOf(me.pfpUrl) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingUri by remember { mutableStateOf<Uri?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun replaceMessages(list: List<SynaxMessage>) {
        messages.clear(); messages.addAll(list.sortedBy { it.timestamp })
    }

    fun startVoiceRecording() {
        val file = File(context.cacheDir, "synax-voice-${System.currentTimeMillis()}.m4a")
        try {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44100)
            mediaRecorder.setAudioEncodingBitRate(96000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            recordingFile = file
            recordingUri = Uri.fromFile(file)
            isRecording = true
            uploadStatus = "Recording voice message…"
        } catch (e: Exception) {
            runCatching { file.delete() }
            runCatching { recorder?.release() }
            recorder = null
            recordingFile = null
            recordingUri = null
            isRecording = false
            uploadStatus = e.message ?: "Unable to start voice recording."
        }
    }

    fun stopAndSendVoiceRecording(send: Boolean) {
        val currentRecorder = recorder
        val file = recordingFile
        val uri = recordingUri
        recorder = null
        isRecording = false
        try {
            currentRecorder?.stop()
            currentRecorder?.release()
        } catch (e: Exception) {
            runCatching { currentRecorder?.release() }
            runCatching { file?.delete() }
            recordingFile = null
            recordingUri = null
            uploadStatus = "Recording was too short or could not be finalized."
            return
        }
        recordingFile = null
        recordingUri = null
        if (!send || uri == null || file == null || !file.exists() || file.length() <= 0L) {
            runCatching { file?.delete() }
            uploadStatus = "Voice recording discarded."
            return
        }
        scope.launch {
            busy = true
            uploadProgress = 0
            try {
                uploadStatus = "Uploading voice message…"
                val uploaded = api.uploadAttachment(token, uri) { value -> scope.launch { uploadProgress = value } }
                require(uploaded.fileUrl.isNotBlank()) { "The server did not return a voice-message URL." }
                api.sendMessage(token, org.json.JSONObject().put("type", "voice").put("fileUrl", uploaded.fileUrl).put("fileName", uploaded.fileName).put("fileSize", uploaded.fileSize).put("clientMessageId", "android-${java.util.UUID.randomUUID()}"))
                replaceMessages(api.messages(token))
                uploadStatus = "Voice message sent."
            } catch (e: Exception) {
                uploadStatus = e.message ?: "Voice message upload failed."
            } finally {
                busy = false
                runCatching { file.delete() }
                delay(2500)
                uploadStatus = ""
            }
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else uploadStatus = "Microphone permission is required to record a voice message."
    }

    val realtime = remember(token) {
        SynaxRealtime { event ->
            scope.launch {
                when (event.optString("type")) {
                    "auth:success" -> { connected = true; statusText = "Connected" }
                    "connection:open" -> statusText = "Connecting…"
                    "connection:closed" -> { connected = false; statusText = "Reconnecting…" }
                    "typing:update" -> if (event.optString("userId") == other.id) typing = event.optBoolean("isTyping")
                    "chat:message_deleted" -> {
                        val id = event.optString("messageId")
                        val index = messages.indexOfFirst { it.id == id }
                        if (index >= 0) messages[index] = messages[index].copy(deleted = true, text = "This message was deleted", fileUrl = "")
                        runCatching { replaceMessages(api.messages(token)) }
                    }
                    "chat:history_cleared" -> runCatching { replaceMessages(api.messages(token)) }
                    "profile:updated", "identities:updated", "time:tick", "settings:updated", "chat:message_ack", "chat:new_message", "chat:messages_delivered", "chat:messages_read", "chat:reaction_update", "chat:message_edited", "chat:message_pinned", "chat:background_updated" -> {
                        if (event.optString("type") == "time:tick" && event.optString("userId") == me.id) event.optJSONObject("timeStatus")?.let { time = it.toSynaxTime() }
                        if (event.optString("type") == "typing:update") typing = event.optBoolean("isTyping")
                        if (event.optString("type") == "profile:updated" && event.optString("userId") == other.id) event.optJSONObject("profile")?.let { other = other.copy(name = it.optString("name", other.name), pfpUrl = it.optString("pfpUrl", other.pfpUrl), nickname = it.optString("nickname", other.nickname), bio = it.optString("bio", other.bio)) }
                        if (event.optString("type") == "chat:background_updated") background = event
                        if (event.optString("type").startsWith("chat:")) runCatching { replaceMessages(api.messages(token)) }
                    }
                }
            }
        }
    }

    LaunchedEffect(token) {
        runCatching { replaceMessages(api.messages(token)) }.onFailure { statusText = it.message ?: "Unable to load chat" }
        runCatching { time = api.time(token).optJSONObject("timeStatus")?.toSynaxTime() ?: time }
        runCatching { background = api.getBackground(token) }
        runCatching { api.markRead(token) }
        runCatching { api.presence(token, true) }
        realtime.connect(token)
        while (true) {
            delay(12_000)
            runCatching { replaceMessages(api.messages(token)) }
            runCatching { time = api.time(token).optJSONObject("timeStatus")?.toSynaxTime() ?: time }
            runCatching { api.presenceState(token) }
        }
    }

    DisposableEffect(realtime) {
        onDispose {
            realtime.disconnect()
            runCatching { recorder?.stop() }; runCatching { recorder?.release() }
            runCatching { recordingFile?.delete() }
            scope.launch { api.presence(token, false) }
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            try {
                uris.forEachIndexed { index, uri ->
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    uploadStatus = "Uploading ${index + 1} of ${uris.size}…"; uploadProgress = 0
                    val uploaded = api.uploadAttachment(token, uri) { progress -> scope.launch { uploadProgress = progress } }
                    val type = when { mime.startsWith("image/") -> "image"; mime.startsWith("video/") -> "video"; mime.startsWith("audio/") -> "voice"; else -> "file" }
                    api.sendMessage(token, JSONObject().put("type", type).put("fileUrl", uploaded.fileUrl).put("fileName", uploaded.fileName).put("fileSize", uploaded.fileSize).put("clientMessageId", "android-${java.util.UUID.randomUUID()}"))
                    replaceMessages(api.messages(token))
                }
                uploadStatus = "Uploads sent"
            } catch (e: Exception) { uploadStatus = e.message ?: "Upload failed" }
            finally { busy = false; delay(2500); uploadStatus = "" }
        }
    }
    val profileImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                busy = true
                val result = api.uploadAttachment(token, uri) { uploadProgress = it }
                profilePhotoUrl = result.fileUrl
                uploadStatus = "Profile photo uploaded. Save profile to apply it."
            } catch (e: Exception) { uploadStatus = e.message ?: "Profile upload failed" } finally { busy = false }
        }
    }
    val backgroundPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { busy = true; background = api.updateSharedBackgroundPhoto(token, uri); uploadStatus = "Shared background saved for both users." }
            catch (e: Exception) { uploadStatus = e.message ?: "Background upload failed" }
            finally { busy = false; delay(2500); uploadStatus = "" }
        }
    }

    Scaffold(
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (other.pfpUrl.isNotBlank()) AsyncImage(model = other.pfpUrl, contentDescription = "Profile photo", modifier = Modifier.size(38.dp).clip(CircleShape))
                            else Box(Modifier.size(38.dp).clip(CircleShape).background(Violet.copy(alpha = .23f)), contentAlignment = Alignment.Center) { Text(other.name.take(1).uppercase(), color = Cyan, fontWeight = FontWeight.Bold) }
                            Column {
                                Text(other.displayName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (typing) "typing…" else if (connected) "Online connection" else statusText.ifBlank { "Private conversation" }, color = if (connected) Color(0xFF5DE1AE) else Cyan, fontSize = 11.sp)
                            }
                        }
                    },
                    actions = {
                        Text(time.remainingSeconds.asClock(), color = if (time.isExpired) Pink else Cyan, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { selectedDialog = "call_voice" }) { Icon(Icons.Default.Call, contentDescription = "Voice call", tint = Cyan) }
                        IconButton(onClick = { selectedDialog = "call_video" }) { Icon(Icons.Default.VideoCall, contentDescription = "Video call", tint = Cyan) }
                        IconButton(onClick = { selectedDialog = "settings" }) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Cyan) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Panel, titleContentColor = Color.White)
                )
                if (searchOpen) OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search messages") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), singleLine = true)
                if (uploadStatus.isNotBlank()) Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(uploadStatus, color = if (uploadStatus.contains("failed", true) || uploadStatus.contains("unable", true)) Pink else Cyan, fontSize = 12.sp)
                    if (busy && uploadProgress in 1..99) LinearProgressIndicator(progress = { uploadProgress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), color = Violet)
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 10.dp, vertical = 8.dp)) {
                replyTo?.let { reply ->
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to ${reply.senderId}: ${reply.text.take(80)}", modifier = Modifier.weight(1f), color = Cyan, fontSize = 11.sp)
                        TextButton(onClick = { replyTo = null }) { Text("Cancel") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { filesLauncher.launch(arrayOf("image/*", "video/*", "application/*", "text/*", "audio/*")) }, enabled = !busy && !isRecording) { Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Cyan) }
                    if (isRecording) {
                        Text("● REC", color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { stopAndSendVoiceRecording(false) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Discard", fontSize = 11.sp) }
                        Button(onClick = { stopAndSendVoiceRecording(true) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Stop & Send", fontSize = 11.sp) }
                    } else {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it; realtime.send(JSONObject().put("type", "typing").put("isTyping", it.isNotBlank())) },
                            modifier = Modifier.weight(1f), placeholder = { Text("Whisper into the private universe…", fontSize = 13.sp) },
                            shape = RoundedCornerShape(18.dp), maxLines = 4, enabled = !busy
                        )
                        if (text.isBlank()) {
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceRecording()
                                else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }, enabled = !busy) { Text("♫", color = Cyan, fontSize = 24.sp) }
                        } else {
                            IconButton(onClick = {
                                val outgoing = text.trim()
                                if (outgoing.isNotBlank() && !sending) scope.launch {
                                    sending = true
                                    try {
                                        val payload = JSONObject().put("type", "text").put("text", outgoing).put("clientMessageId", "android-${java.util.UUID.randomUUID()}")
                                        replyTo?.let { payload.put("replyTo", JSONObject().put("id", it.id).put("text", it.text).put("senderName", if (it.senderId == me.id) me.displayName else other.displayName)) }
                                        val sent = api.sendMessage(token, payload)
                                        if (messages.none { it.id == sent.id || (sent.clientMessageId.isNotBlank() && it.clientMessageId == sent.clientMessageId) }) messages.add(sent)
                                        text = ""; replyTo = null; realtime.send(JSONObject().put("type", "typing").put("isTyping", false))
                                    } catch (e: Exception) { uploadStatus = e.message ?: "Message failed" }
                                    finally { sending = false }
                                }
                            }, enabled = text.isNotBlank() && !sending) { if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, contentDescription = "Send", tint = Violet) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).background(VoidBlack).padding(horizontal = 10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item { WelcomeMessage(settingsInitial.optString("welcomeMessage", "Welcome to your private sanctuary. Every conversation belongs solely to you both.")) }
            items(messages.filter { search.isBlank() || it.text.contains(search, true) || it.fileName.contains(search, true) }, key = { it.id }) { message ->
                MessageRow(message, isMine = message.senderId == me.id, onOpen = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }, onAction = { action ->
                    selectedMessage = message
                    when (action) {
                        "reply" -> replyTo = message
                        "edit" -> { editDraft = message.text; selectedDialog = "edit" }
                        "delete" -> selectedDialog = "delete_own"
                        "reaction" -> scope.launch { runCatching { api.react(token, message.id, "❤️"); replaceMessages(api.messages(token)) }.onFailure { uploadStatus = it.message ?: "Reaction failed" } }
                        "pin" -> scope.launch { runCatching { api.pin(token, message.id); replaceMessages(api.messages(token)) }.onFailure { uploadStatus = it.message ?: "Pin failed" } }
                    }
                })
            }
        }
    }

    when (selectedDialog) {
        "settings" -> AlertDialog(
            onDismissRequest = { selectedDialog = "" }, title = { Text("SYNAX Settings") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profile Settings", Modifier.fillMaxWidth().clickable { selectedDialog = "profile" }.padding(10.dp), color = Cyan)
                Text("Theme & Chat Background", Modifier.fillMaxWidth().clickable { selectedDialog = "background" }.padding(10.dp), color = Cyan)
                Text("Search Messages", Modifier.fillMaxWidth().clickable { searchOpen = !searchOpen; selectedDialog = "" }.padding(10.dp), color = Cyan)
                Text("Chat Options", color = Cyan, modifier = Modifier.fillMaxWidth().clickable { selectedDialog = "options" }.padding(10.dp))
                Text("Logout", color = Pink, modifier = Modifier.fillMaxWidth().clickable { selectedDialog = "logout" }.padding(10.dp))
            } }, confirmButton = { TextButton(onClick = { selectedDialog = "" }) { Text("Close") } }
        )
        "profile" -> ProfileDialog(me, profilePhotoUrl, onClose = { selectedDialog = "" }, onPickPhoto = { profileImageLauncher.launch(arrayOf("image/*")) }, onSave = { nickname, bio, pfp ->
            scope.launch { try { busy = true; api.updateProfile(token, nickname, bio, pfp); uploadStatus = "Profile saved."; selectedDialog = "" } catch (e: Exception) { uploadStatus = e.message ?: "Profile update failed" } finally { busy = false } }
        })
        "background" -> BackgroundDialog(background, onClose = { selectedDialog = "" }, onPreset = { id -> scope.launch { try { background = api.setBackgroundPreset(token, id); selectedDialog = ""; uploadStatus = "Shared background updated." } catch (e: Exception) { uploadStatus = e.message ?: "Unable to save background" } } }, onPhoto = { backgroundPhotoLauncher.launch(arrayOf("image/*")) }, onReset = { scope.launch { runCatching { background = api.resetBackground(token); selectedDialog = "" } } })
        "options" -> AlertDialog(onDismissRequest = { selectedDialog = "" }, title = { Text("Chat Options") }, text = { Text("Messages and shared settings are synchronized with synax.online. The server enforces the administrator's feature and time rules.") }, confirmButton = { TextButton(onClick = { selectedDialog = "" }) { Text("OK") } })
        "logout" -> AlertDialog(onDismissRequest = { selectedDialog = "" }, title = { Text("Log out of SYNAX?") }, text = { Text("Your server session will be ended.") }, confirmButton = { TextButton(onClick = { selectedDialog = ""; onLogout() }) { Text("Log out", color = Pink) } }, dismissButton = { TextButton(onClick = { selectedDialog = "" }) { Text("Cancel") } })
        "edit" -> AlertDialog(onDismissRequest = { selectedDialog = "" }, title = { Text("Edit message") }, text = { OutlinedTextField(editDraft, { editDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Message") }) }, confirmButton = { TextButton(onClick = { val m = selectedMessage; if (m != null) scope.launch { try { api.editMessage(token, m.id, editDraft); replaceMessages(api.messages(token)); selectedDialog = "" } catch (e: Exception) { uploadStatus = e.message ?: "Edit failed" } } }) { Text("Save") } }, dismissButton = { TextButton(onClick = { selectedDialog = "" }) { Text("Cancel") } })
        "delete_own" -> AlertDialog(onDismissRequest = { selectedDialog = "" }, title = { Text("Delete this message?") }, text = { Text("This will remove the message from the shared conversation if the server allows it.") }, confirmButton = { TextButton(onClick = { val m = selectedMessage; if (m != null) scope.launch { try { api.deleteOwnMessage(token, m.id); replaceMessages(api.messages(token)); selectedDialog = "" } catch (e: Exception) { uploadStatus = e.message ?: "Delete failed"; selectedDialog = "" } } }) { Text("Delete", color = Pink) } }, dismissButton = { TextButton(onClick = { selectedDialog = "" }) { Text("Cancel") } })
        "call_voice", "call_video" -> AlertDialog(onDismissRequest = { selectedDialog = "" }, title = { Text(if (selectedDialog == "call_video") "Video call" else "Voice call") }, text = { Text("Native calling setup requires microphone${if (selectedDialog == "call_video") " and camera" else ""} permission. Use the existing SYNAX website call until native WebRTC is enabled in this build.") }, confirmButton = { TextButton(onClick = { selectedDialog = ""; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://synax.online"))) } }) { Text("Open SYNAX") } }, dismissButton = { TextButton(onClick = { selectedDialog = "" }) { Text("Cancel") } })
    }
}

@Composable
private fun WelcomeMessage(message: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 26.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✦  S Y N A X  ✦", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(message, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MessageRow(message: SynaxMessage, isMine: Boolean, onOpen: (String) -> Unit, onAction: (String) -> Unit) {
    if (message.type == "system") {
        Text(message.text, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(12.dp), color = Cyan, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        return
    }
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 330.dp).fillMaxWidth(.92f).clip(RoundedCornerShape(18.dp)).background(if (isMine) Color(0xFF211A4A) else Panel).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isMine) "You" else message.senderId.replace("person_", "Person "), color = if (isMine) Color(0xFFC5B8FF) else Cyan, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "Message actions", tint = Cyan, modifier = Modifier.size(18.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Reply") }, onClick = { menu = false; onAction("reply") })
                        DropdownMenuItem(text = { Text("React ❤️") }, onClick = { menu = false; onAction("reaction") })
                        DropdownMenuItem(text = { Text(if (message.isPinned) "Toggle pin" else "Pin message") }, onClick = { menu = false; onAction("pin") })
                        if (isMine && message.type == "text" && !message.deleted) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onAction("edit") })
                        if (isMine && !message.deleted) DropdownMenuItem(text = { Text("Delete", color = Pink) }, onClick = { menu = false; onAction("delete") })
                    }
                }
            }
            if (message.replyToText.isNotBlank()) Text("↪ ${message.replyToSender}: ${message.replyToText}", color = Cyan, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(VoidBlack.copy(alpha = .35f), RoundedCornerShape(8.dp)).padding(7.dp))
            if (message.deleted) Text("This message was deleted", color = Color.Gray, fontSize = 13.sp)
            else when (message.type) {
                "image" -> {
                    if (message.fileUrl.isNotBlank()) AsyncImage(model = message.fileUrl, contentDescription = message.fileName.ifBlank { "Shared image" }, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(message.fileUrl) })
                    if (message.text.isNotBlank()) Text(message.text, color = Color.White)
                }
                "video" -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("▶  Video attachment", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { if (message.fileUrl.isNotBlank()) onOpen(message.fileUrl) }.padding(8.dp))
                    Text(message.fileName, color = Cyan, fontSize = 11.sp)
                }
                "file", "voice" -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (message.type == "voice") "♫  Voice message" else "▤  ${message.fileName.ifBlank { "File attachment" }}", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { if (message.fileUrl.isNotBlank()) onOpen(message.fileUrl) }.padding(7.dp))
                    Text("Tap to open", color = Cyan, fontSize = 10.sp)
                }
                else -> Text(message.text, color = Color(0xFFF2F5FF), fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (message.isEdited) Text("edited · ", color = Color.Gray, fontSize = 9.sp)
                if (message.isPinned) Text("📌 ", color = Cyan, fontSize = 10.sp)
                Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)), color = Color.Gray, fontSize = 10.sp)
                if (isMine) Text(when (message.status) { "read" -> "  ✓✓"; "delivered" -> "  ✓✓"; else -> "  ✓" }, color = if (message.status == "read") Color(0xFF52A7FF) else Color.LightGray, fontSize = 11.sp)
            }
            val reactions = message.reactions
            if (reactions.length() > 0) Text(reactions.keys().asSequence().mapNotNull { key -> reactions.optString(key).takeIf { it.isNotBlank() } }.joinToString("  "), color = Pink, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileDialog(user: SynaxUser, currentPhoto: String, onClose: () -> Unit, onPickPhoto: () -> Unit, onSave: (String, String, String) -> Unit) {
    var nickname by remember { mutableStateOf(user.nickname.ifBlank { user.name }) }
    var bio by remember { mutableStateOf(user.bio) }
    var pfp by remember { mutableStateOf(currentPhoto) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Personal Profile") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pfp.isNotBlank()) AsyncImage(model = pfp, contentDescription = "Profile preview", modifier = Modifier.size(78.dp).clip(CircleShape).align(Alignment.CenterHorizontally))
            OutlinedTextField(value = nickname, onValueChange = { nickname = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Private nickname") }, singleLine = true)
            OutlinedTextField(value = bio, onValueChange = { bio = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Sanctuary status / bio") }, minLines = 2)
            OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) { Text("Choose profile photo") }
            Text("Personal portrait is separate from your professional logo mark.", color = Color.Gray, fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = { onSave(nickname.trim(), bio.trim(), pfp) }) { Text("Save changes") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun BackgroundDialog(current: JSONObject, onClose: () -> Unit, onPreset: (String) -> Unit, onPhoto: () -> Unit, onReset: () -> Unit) {
    val options = listOf("Starfield" to "starfield", "Nebula" to "nebula", "Aurora" to "aurora", "Twilight" to "twilight", "Midnight" to "midnight", "Crimson" to "crimson")
    AlertDialog(onDismissRequest = onClose, title = { Text("Chat Background") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shared for both people in this chat", color = Cyan, fontSize = 12.sp)
            options.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { (label, id) -> OutlinedButton(onClick = { onPreset(id) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text(label, fontSize = 11.sp) } } } }
            OutlinedButton(onClick = onPhoto, modifier = Modifier.fillMaxWidth()) { Text("Choose custom photo") }
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) { Text("Reset background") }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
