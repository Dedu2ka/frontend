package com.example.olo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.olo.data.ConnectionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MessageUiItem(
    val id: String,
    val senderId: String,
    val content: String,
    val timestamp: String? = null,
    val status: String? = null
)

private data class ChatTimelineItem(
    val key: String,
    val message: MessageUiItem? = null,
    val dateLabel: String? = null
)

@Composable
fun ChatScreen(
    currentUserId: String,
    chatPartnerName: String,
    chatPartnerAvatarUrl: String? = null,
    messages: List<MessageUiItem>,
    onSendMessage: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    hasOlderMessages: Boolean = false,
    loadingOlderMessages: Boolean = false,
    onLoadOlderMessages: () -> Unit = {}
) {
    var textInput by remember { mutableStateOf("") }

    val darkBg = Color(0xFF131541)
    val topBarBg = Color(0xFF191B4E)
    val inputBg = Color(0xFF191B4E)
    val strokeColor = Color(0xFF2C3285)
    val myMessageBg = Color(0xFF3B44B3)
    val partnerMessageBg = Color(0xFF1E215C)

    val partnerAvatarBitmap = remember(chatPartnerAvatarUrl) {
        if (!chatPartnerAvatarUrl.isNullOrBlank()) decodeBase64ToBitmap(chatPartnerAvatarUrl) else null
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val messageListState = rememberLazyListState()
    val timelineItems = remember(messages) {
        val newestFirst = messages.asReversed()
        buildList {
            newestFirst.forEachIndexed { index, message ->
                add(ChatTimelineItem(key = "message-${message.id}", message = message))
                val nextOlder = newestFirst.getOrNull(index + 1)
                if (nextOlder == null || messageLocalDate(message.timestamp) != messageLocalDate(nextOlder.timestamp)) {
                    add(ChatTimelineItem(key = "date-${messageLocalDate(message.timestamp) ?: message.id}", dateLabel = formatMessageDate(message.timestamp)))
                }
            }
        }
    }

    LaunchedEffect(messageListState.firstVisibleItemIndex, messageListState.layoutInfo.totalItemsCount, hasOlderMessages, loadingOlderMessages) {
        if (hasOlderMessages && !loadingOlderMessages && messages.isNotEmpty() &&
            messageListState.firstVisibleItemIndex >= messageListState.layoutInfo.totalItemsCount - 4) onLoadOlderMessages()
    }

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            messageListState.animateScrollToItem(0)
        }
    }

    Scaffold(
        bottomBar = {
            if (!imeVisible) {
                OloBottomBar(
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToSearch = onNavigateToSearch
                )
            }
        },
        containerColor = darkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topBarBg)
                    .padding(16.dp)
            ) {
                Text("olo", color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (partnerAvatarBitmap != null) {
                        AsyncImage(
                            model = partnerAvatarBitmap,
                            contentDescription = "Аватар собеседника",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.LightGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.DarkGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(chatPartnerName, color = Color.White, fontSize = 16.sp)
                }
            }
            ConnectionStatusBanner(connectionStatus, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

            // Message list
            LazyColumn(
                state = messageListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                reverseLayout = true
            ) {
                items(timelineItems, key = { it.key }) { entry ->
                    val msg = entry.message
                    if (msg == null) {
                        Text(
                            text = entry.dateLabel.orEmpty(),
                            color = Color(0xFFBFC3FF),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                    val isMyMessage = msg.senderId == currentUserId

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMyMessage) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(
                                    color = if (isMyMessage) myMessageBg else partnerMessageBg,
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isMyMessage) 12.dp else 2.dp,
                                        bottomEnd = if (isMyMessage) 2.dp else 12.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(msg.content, color = Color.White)
                                val time = remember(msg.timestamp) { formatMessageTime(msg.timestamp) }
                                if (time != null || (isMyMessage && msg.status != null)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (time != null) {
                                            Text(time, color = Color(0xFFBFC3FF), fontSize = 10.sp)
                                        }
                                        if (isMyMessage && msg.status != null) {
                                            if (time != null) Spacer(modifier = Modifier.width(4.dp))
                                            MessageStatusIndicator(msg.status)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                if (loadingOlderMessages) item { Text("Загружаем…", color = Color.LightGray, modifier = Modifier.fillMaxWidth()) }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Введите текст", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = inputBg,
                        focusedContainerColor = inputBg,
                        unfocusedBorderColor = strokeColor,
                        focusedBorderColor = strokeColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendMessage(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(strokeColor, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Отправить", tint = Color.White)
                }
            }
        }
    }
}


private fun formatMessageTime(timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(timestamp) }
        .recoverCatching { OffsetDateTime.parse(timestamp).toInstant() }
        .getOrNull() ?: return null
    return DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()))
}

private fun messageLocalDate(timestamp: String?): LocalDate? {
    if (timestamp.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(timestamp) }
        .recoverCatching { OffsetDateTime.parse(timestamp).toInstant() }
        .getOrNull() ?: return null
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun formatMessageDate(timestamp: String?): String {
    val date = messageLocalDate(timestamp) ?: return "Без даты"
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()).format(date)
    }
}

@Composable
private fun MessageStatusIndicator(status: String) {
    when (status.uppercase()) {
        "PENDING" -> Text("…", color = Color(0xFFBFC3FF), fontSize = 12.sp)
        "SENT" -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Отправлено",
            modifier = Modifier.size(13.dp),
            tint = Color(0xFFBFC3FF)
        )
        "DELIVERED", "READ" -> {
            val tint = if (status.equals("READ", ignoreCase = true)) {
                Color(0xFF72B7FF)
            } else {
                Color(0xFFBFC3FF)
            }
            Box(modifier = Modifier.width(20.dp).height(14.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (status.equals("READ", ignoreCase = true)) "Прочитано" else "Доставлено",
                    modifier = Modifier.size(13.dp).align(Alignment.CenterStart),
                    tint = tint
                )
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp).align(Alignment.CenterEnd),
                    tint = tint
                )
            }
        }
    }
}
