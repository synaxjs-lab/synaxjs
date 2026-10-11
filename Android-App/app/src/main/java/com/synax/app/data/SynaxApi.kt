package com.synax.app.data  import com.synax.app.BuildConfig import android.content.ContentResolver import android.content.Context import android.database.Cursor import android.net.Uri import android.provider.OpenableColumns

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SynaxApi(private val context: Context, private val baseUrl: String = BuildConfig.SYNAX_BASE_URL.trimEnd('/')) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class UploadResult(val fileUrl: String, val fileName: String, val fileSize: Long, val mimeType: String)

    private fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null,
        bytes: ByteArray? = null,
        contentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap(),
        allowError: Boolean = false
    ): JSONObject {
        val builder = Request.Builder().url(baseUrl + path).header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        val requestBody: RequestBody? = when {
            bytes != null -> bytes.toRequestBody(contentType.toMediaTypeOrNull())
            body != null -> body.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            method in setOf("POST", "PUT", "PATCH", "DELETE") -> ByteArray(0).toRequestBody(contentType.toMediaTypeOrNull())
            else -> null
        }
        builder.method(method, requestBody)
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val data = try { if (raw.isBlank()) JSONObject() else JSONObject(raw) } catch (_: Exception) { JSONObject().put("raw", raw.take(500)) }
            if (!response.isSuccessful && !allowError) {
                val message = data.optString("message").ifBlank { data.optString("error").ifBlank { data.optString("detail").ifBlank { "Request failed (HTTP ${response.code})" } } }
                throw IllegalStateException(message)
            }
            return data.put("__httpCode", response.code)
        }
    }

    private suspend fun api(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null, bytes: ByteArray? = null, contentType: String = "application/json", extraHeaders: Map<String, String> = emptyMap(), allowError: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) { request(path, method, token, body, bytes, contentType, extraHeaders, allowError) }

    suspend fun health(): JSONObject = api("/api/health")
    suspend fun publicIdentities(): JSONObject = api("/api/public/identities")

    suspend fun login(userId: String, password: String): JSONObject = api("/api/auth/login", "POST", body = JSONObject().put("userId", userId).put("password", password))
    suspend fun adminLogin(password: String): JSONObject = api("/api/auth/admin-login", "POST", body = JSONObject().put("password", password))
    suspend fun me(token: String): JSONObject = api("/api/auth/me", token = token)
    suspend fun logout(token: String) { api("/api/auth/logout", "POST", token = token, allowError = true) }
    suspend fun time(token: String): JSONObject = api("/api/time", token = token)

    suspend fun messages(token: String): List<SynaxMessage> {
        val response = api("/api/messages", token = token)
        return parseMessages(response.optJSONArray("messages"))
    }

    suspend fun sendMessage(token: String, payload: JSONObject): SynaxMessage {
        if (!payload.has("clientMessageId")) payload.put("clientMessageId", "android-${UUID.randomUUID()}")
        val data = api("/api/messages", "POST", token, payload)
        return (data.optJSONObject("message") ?: data).toSynaxMessage()
    }

    suspend fun markRead(token: String, ids: List<String> = emptyList()) {
        val body = JSONObject()
        if (ids.isNotEmpty()) body.put("messageIds", JSONArray(ids))
        api("/api/messages/mark-read", "POST", token, body, allowError = true)
    }

    suspend fun updateProfile(token: String, nickname: String, bio: String, pfpUrl: String): SynaxUser {
        val data = api("/api/profile/update", "POST", token, JSONObject().put("nickname", nickname).put("bio", bio).put("pfpUrl", pfpUrl))
        return (data.optJSONObject("user") ?: JSONObject()).toSynaxUser()
    }

    suspend fun editMessage(token: String, id: String, text: String) {
        api("/api/messages/${enc(id)}", "PUT", token, JSONObject().put("text", text))
    }

    suspend fun deleteOwnMessage(token: String, id: String) {
        api("/api/messages/${enc(id)}", "DELETE", token)
    }

    suspend fun react(token: String, id: String, emoji: String) {
        api("/api/messages/${enc(id)}/reaction", "POST", token, JSONObject().put("emoji", emoji))
    }

    suspend fun pin(token: String, id: String) { api("/api/messages/${enc(id)}/pin", "POST", token) }
    suspend fun presence(token: String, active: Boolean) { api("/api/presence/chat", "POST", token, JSONObject().put("active", active), allowError = true) }
    suspend fun presenceState(token: String): JSONObject = api("/api/presence/state", token = token)

    suspend fun getBackground(token: String): JSONObject = api("/api/chat/background", token = token)
    suspend fun setBackgroundPreset(token: String, id: String): JSONObject = api("/api/chat/background/preset", "POST", token, JSONObject().put("backgroundId", id))
    suspend fun resetBackground(token: String): JSONObject = api("/api/chat/background", "DELETE", token)

    suspend fun updateSharedBackgroundPhoto(token: String, uri: Uri): JSONObject = withContext(Dispatchers.IO) {
        val info = queryUri(uri)
        val bytes = readBounded(uri, 10L * 1024 * 1024)
        request("/api/chat/background/upload", "POST", token, bytes = bytes, contentType = info.mimeType, extraHeaders = mapOf("X-Synax-Content-Type" to info.mimeType, "X-Synax-File-Name" to enc(info.name)))
    }

    suspend fun uploadAttachment(token: String, uri: Uri, progress: (Int) -> Unit): UploadResult = withContext(Dispatchers.IO) {
        val info = queryUri(uri)
        require(info.size > 0) { "The selected file is empty or its size is unavailable." }
        val isImage = info.mimeType.startsWith("image/")
        val isVideo = info.mimeType.startsWith("video/")
        if (isImage || isVideo) uploadResumable(token, uri, info, if (isImage) "image" else "video", progress)
        else {
            require(info.size <= 25L * 1024 * 1024) { "Documents and audio files must be 25 MB or smaller." }
            val bytes = readBounded(uri, 25L * 1024 * 1024)
            val response = request("/api/upload", "POST", token, bytes = bytes, contentType = info.mimeType, extraHeaders = mapOf("X-Synax-File-Name" to enc(info.name)))
            progress(100)
            UploadResult(response.optString("fileUrl"), response.optString("fileName", info.name), response.optLong("fileSize", info.size), info.mimeType)
        }
    }

    private data class UriInfo(val name: String, val size: Long, val mimeType: String)
    private fun queryUri(uri: Uri): UriInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "attachment" } ?: "attachment"
        var size = -1L
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            if (cursor?.moveToFirst() == true) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        } finally { cursor?.close() }
        val mime = context.contentResolver.getType(uri)?.substringBefore(';') ?: guessMime(name)
        if (size <= 0) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                var count: Long = 0
                while (true) { val read = input.read(buffer); if (read < 0) break; count += read; require(count <= 500L * 1024 * 1024) { "File is too large." } }
                size = count
            }
        }
        return UriInfo(name.take(240), size, mime)
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"
        "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "webm" -> "video/webm"; "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"
        "txt" -> "text/plain"; "zip" -> "application/zip"; else -> "application/octet-stream"
    }

    private fun readBounded(uri: Uri, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= maxBytes) { "Selected file exceeds the supported size limit." }; out.write(buffer, 0, n) }
        } ?: throw IllegalStateException("Cannot open the selected file.")
        return out.toByteArray()
    }

    private fun uploadResumable(token: String, uri: Uri, info: UriInfo, kind: String, progress: (Int) -> Unit): UploadResult {
        require(info.size in 1..(500L * 1024 * 1024)) { "Media must be between 1 byte and 500 MB." }
        val prepared = request("/api/upload/resumable", "POST", token, JSONObject().put("fileName", info.name).put("mimeType", info.mimeType).put("kind", kind).put("fileSize", info.size))
        val uploadUrl = prepared.optString("uploadUrl"); val fileUrl = prepared.optString("fileUrl")
        require(uploadUrl.isNotBlank() && fileUrl.isNotBlank()) { "The server did not prepare this upload." }
        fun offset(): Long = request("/api/upload/resumable/offset", "POST", token, JSONObject().put("uploadUrl", uploadUrl)).optLong("offset", 0)
        var remoteOffset = offset().coerceIn(0, info.size)
        progress(((remoteOffset * 100) / info.size).toInt())
        val chunkSize = 1024 * 1024
        while (remoteOffset < info.size) {
            // Re-read the authoritative storage offset after every interruption or retry.
            remoteOffset = offset().coerceIn(0, info.size)
            if (remoteOffset >= info.size) break
            val desired = minOf(chunkSize.toLong(), info.size - remoteOffset).toInt()
            val chunk = readRange(uri, remoteOffset, desired)
            val result = request(
                "/api/upload/resumable/chunk", "PATCH", token, bytes = chunk,
                contentType = "application/octet-stream",
                extraHeaders = mapOf("X-Synax-Upload-URL" to uploadUrl, "Upload-Offset" to remoteOffset.toString()),
                allowError = true
            )
            val status = result.optInt("__httpCode", 0)
            if (status == 409) { remoteOffset = offset(); continue }
            if (status !in 200..299) throw IllegalStateException(result.optString("error").ifBlank { "Upload chunk failed (HTTP $status)." })
            val serverOffset = result.optLong("offset", remoteOffset + chunk.size)
            remoteOffset = if (serverOffset > remoteOffset && serverOffset <= info.size) serverOffset else offset()
            progress(((remoteOffset * 100) / info.size).toInt().coerceIn(0, 99))
        }
        val finalOffset = offset()
        require(finalOffset == info.size) { "Upload verification failed at ${(finalOffset * 100 / info.size)}%." }
        progress(100)
        return UploadResult(fileUrl, prepared.optString("fileName", info.name), info.size, info.mimeType)
    }

    private fun readRange(uri: Uri, start: Long, length: Int): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot reopen selected file.")
        input.use {
            var skipped = 0L
            while (skipped < start) {
                val count = it.skip(start - skipped)
                if (count > 0) skipped += count else { if (it.read() < 0) throw IllegalStateException("File ended before the upload offset."); skipped++ }
            }
            val bytes = ByteArray(length); var offset = 0
            while (offset < length) { val count = it.read(bytes, offset, length - offset); if (count < 0) break; offset += count }
            if (offset != length) throw IllegalStateException("Selected file changed during upload.")
            return bytes
        }
    }

    suspend fun callIceServers(token: String): JSONObject = api("/api/call/ice-servers", token = token)
    suspend fun sendCallSignal(token: String, signal: JSONObject): JSONObject = api("/api/call/signal", "POST", token, signal)
    suspend fun callSignals(token: String, afterId: Long, since: Long, callId: String? = null): JSONObject {
        val query = "?after=$afterId&since=$since" + if (callId.isNullOrBlank()) "" else "&callId=${enc(callId)}"
        return api("/api/call/signals$query", token = token)
    }

    suspend fun adminOverview(token: String): JSONObject = api("/api/admin/overview", token = token)
    suspend fun adminUsers(token: String): List<SynaxUser> {
        val users = adminOverview(token).optJSONObject("users") ?: JSONObject()
        return listOf(users.optJSONObject("person_1")?.toSynaxUser("person_1") ?: SynaxUser("person_1", "Person 1"), users.optJSONObject("person_2")?.toSynaxUser("person_2") ?: SynaxUser("person_2", "Person 2"))
    }
    suspend fun adminUpdateUser(token: String, userId: String, fields: JSONObject): JSONObject = api("/api/admin/users", "PUT", token, JSONObject().put(userId, fields))
    suspend fun adminLockUser(token: String, userId: String, locked: Boolean): JSONObject = api("/api/admin/user/${enc(userId)}/lock", "POST", token, JSONObject().put("isLocked", locked))
    suspend fun adminAdjustTime(token: String, userId: String, options: JSONObject): JSONObject = api("/api/admin/user/${enc(userId)}/reset-time", "POST", token, options)
    suspend fun adminUpdateSettings(token: String, settings: JSONObject): JSONObject = api("/api/admin/settings", "PUT", token, settings)
    suspend fun adminMessages(token: String): List<SynaxMessage> = parseMessages(api("/api/admin/messages", token = token).optJSONArray("messages"))
    suspend fun adminLogs(token: String): JSONArray = api("/api/admin/logs", token = token).optJSONArray("logs") ?: JSONArray()
    suspend fun adminDeleteMessage(token: String, messageId: String): JSONObject = api("/api/admin/messages/${enc(messageId)}", "DELETE", token)
    suspend fun adminClearMessages(token: String): JSONObject = api("/api/admin/messages/clear", "POST", token)
    suspend fun adminResetDefaults(token: String): JSONObject = api("/api/admin/reset-defaults", "POST", token)
    suspend fun adminChangePassword(token: String, current: String, new: String): JSONObject = api("/api/admin/change-password", "POST", token, JSONObject().put("currentPassword", current).put("newPassword", new))

    private fun parseMessages(array: JSONArray?): List<SynaxMessage> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toSynaxMessage() }.filter { it.id.isNotBlank() }.sortedBy { it.timestamp }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
