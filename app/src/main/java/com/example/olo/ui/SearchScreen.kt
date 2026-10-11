package com.example.olo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.olo.data.ChatDto

data class UserItem(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String? = null
)

@Composable
fun SearchScreen(
    userList: List<UserItem>,
    chatList: List<ChatDto>,
    onSearchQueryChange: (String) -> Unit,
    onUserClick: (UserItem) -> Unit,
    onChatClick: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val darkBg = Color(0xFF131541)
    val inputBg = Color(0xFF191B4E)
    val cardBg = Color(0xFF191B4E)
    val strokeColor = Color(0xFF2C3285)

    Scaffold(
        bottomBar = {
            OloBottomBar(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch
            )
        },
        containerColor = darkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("olo", color = Color.White, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                placeholder = { Text("Поиск пользователей", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isBlank()) {
                // Список существующих чатов
                Text("Ваши чаты", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chatList) { chat ->
                        val chatAvatarBitmap = remember(chat.partnerAvatarUrl) {
                            if (!chat.partnerAvatarUrl.isNullOrBlank()) decodeBase64ToBitmap(chat.partnerAvatarUrl) else null
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .background(cardBg, RoundedCornerShape(28.dp))
                                .clickable { onChatClick(chat.id) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (chatAvatarBitmap != null) {
                                AsyncImage(
                                    model = chatAvatarBitmap,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.LightGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.DarkGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chat.partnerName?.takeIf { it.isNotBlank() } ?: "Чат",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = chat.lastMessage?.takeIf { it.isNotBlank() } ?: "Нет сообщений",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (chat.unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(Color(0xFF5B67F1), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Результаты поиска пользователей
                Text("Результаты поиска", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(userList) { user ->
                        val userAvatarBitmap = remember(user.avatarUrl) {
                            if (!user.avatarUrl.isNullOrBlank()) decodeBase64ToBitmap(user.avatarUrl) else null
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(cardBg, RoundedCornerShape(28.dp))
                                .clickable { onUserClick(user) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (userAvatarBitmap != null) {
                                AsyncImage(
                                    model = userAvatarBitmap,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.LightGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.DarkGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val displayName = user.displayName?.takeIf { it.isNotBlank() }
                                Text(
                                    text = displayName ?: user.username,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1
                                )
                                if (displayName != null && displayName != user.username) {
                                    Text(
                                        text = "@${user.username}",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
