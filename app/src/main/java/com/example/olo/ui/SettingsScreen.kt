package com.example.olo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun SettingsScreen(
    currentUsername: String,
    currentDisplayName: String,
    currentAvatarUrl: String,
    onSaveProfile: (newDisplayName: String, newAvatarUrl: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onLogout: () -> Unit
) {
    var displayName by remember(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var avatarBase64 by remember(currentAvatarUrl) { mutableStateOf(currentAvatarUrl) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val darkBg = Color(0xFF131541)
    val inputBg = Color(0xFF191B4E)
    val strokeColor = Color(0xFF2C3285)

    val avatarBitmap = remember(avatarBase64) {
        if (avatarBase64.isNotBlank()) decodeBase64ToBitmap(avatarBase64) else null
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                avatarBase64 = encodeAvatarFromUri(context.contentResolver, it)
                avatarError = null
            } catch (e: Exception) {
                avatarError = e.message ?: "Не удалось открыть изображение"
            }
        }
    }

    Scaffold(
        bottomBar = {
            OloBottomBar(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch
            )
        },
        containerColor = darkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Настройки профиля",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(inputBg)
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    AsyncImage(
                        model = avatarBitmap,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Placeholder",
                        modifier = Modifier.size(60.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { launcher.launch("image/*") }) {
                Text("Выбрать фото из галереи", color = Color(0xFF6B8AFF))
            }

            avatarError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "@$currentUsername",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Отображаемое имя", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = inputBg,
                    focusedContainerColor = inputBg,
                    unfocusedBorderColor = strokeColor,
                    focusedBorderColor = strokeColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF6B8AFF),
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onSaveProfile(displayName, avatarBase64)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3285))
            ) {
                Text("Сохранить изменения", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showLogoutConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2C3B))
            ) {
                Text("Выйти из аккаунта", color = Color.White)
            }
        }
    }

    if (showLogoutConfirmation) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = darkBg,
                onSurface = Color.White,
                primary = Color(0xFF6B8AFF)
            )
        ) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmation = false },
                title = { Text("Выйти из аккаунта?") },
                text = { Text("Вы действительно хотите выйти из аккаунта?", color = Color(0xFFC5C8E8)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirmation = false
                            onLogout()
                        }
                    ) {
                        Text("Выйти", color = Color(0xFFFF7A8A))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmation = false }) {
                        Text("Отмена", color = Color(0xFF6B8AFF))
                    }
                }
            )
        }
    }
}
