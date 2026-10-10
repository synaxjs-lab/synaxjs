package com.synax.app.data

import org.json.JSONObject

data class SynaxUser(
    val id: String,
    val name: String,
    val username: String = "",
    val pfpUrl: String = "",
    val nickname: String = "",
    val bio: String = "",
    val isLocked: Boolean = false,
    val allowedMinutes: Int = 60,
    val logoName: String = "",
    val accentColor: String = "#6750F5",
    val raw: JSONObject = JSONObject()
) {
    val displayName: String get() = nickname.ifBlank { name }
}

data class SynaxMessage(
    val id: String,
    val clientMessageId: String = "",
    val senderId: String,
    val type: String,
    val text: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val timestamp: Long = 0,
    val status: String = "sent",
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val deleted: Boolean = false,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val reactions: JSONObject = JSONObject(),
    val raw: JSONObject = JSONObject()
)

data class SynaxTime(
    val remainingSeconds: Long = 0,
    val allowedSeconds: Long = 0,
    val isExpired: Boolean = false,
    val mode: String = "daily",
    val customMessage: String = ""
)

fun JSONObject.toSynaxUser(defaultId: String = ""): SynaxUser {
    val logo = optJSONObject("logo") ?: JSONObject()
    return SynaxUser(
        id = optString("id", defaultId),
        name = optString("name", if (defaultId == "person_1") "Sopie" else if (defaultId == "person_2") "Souie" else "SYNAX"),
        username = optString("username"),
        pfpUrl = optString("pfpUrl"),
        nickname = optString("nickname"),
        bio = optString("bio"),
        isLocked = optBoolean("isLocked", false),
        allowedMinutes = optInt("allowedMinutes", 60),
        logoName = logo.optString("name"),
        accentColor = logo.optString("accentColor", "#6750F5"),
        raw = this
    )
}

fun JSONObject.toSynaxMessage(): SynaxMessage {
    val reply = optJSONObject("replyTo") ?: JSONObject()
    return SynaxMessage(
        id = optString("id"),
        clientMessageId = optString("clientMessageId"),
        senderId = optString("senderId", "system"),
        type = optString("type", "text"),
        text = optString("text"),
        fileUrl = optString("fileUrl"),
        fileName = optString("fileName"),
        fileSize = optLong("fileSize", 0),
        timestamp = optLong("timestamp", 0),
        status = optString("status", "sent"),
        isPinned = optBoolean("isPinned", optBoolean("is_pinned", false)),
        isEdited = optBoolean("isEdited", optBoolean("is_edited", false)),
        deleted = optBoolean("deleted", false),
        replyToId = reply.optString("id"),
        replyToText = reply.optString("text"),
        replyToSender = reply.optString("senderName"),
        reactions = optJSONObject("reactions") ?: JSONObject(),
        raw = this
    )
}

fun JSONObject.toSynaxTime(): SynaxTime {
    return SynaxTime(
        remainingSeconds = optLong("remainingSeconds", 0),
        allowedSeconds = optLong("allowedSeconds", 0),
        isExpired = optBoolean("isExpired", false),
        mode = optString("mode", "daily"),
        customMessage = optString("customMessage")
    )
}

fun Long.asClock(): String {
    val total = this.coerceAtLeast(0)
    return "%02d:%02d".format(total / 3600, (total % 3600) / 60)
}
