// Путь к файлу: app/src/main/java/com/example/olo/ui/OloBottomBar.kt

package com.example.olo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OloBottomBar(
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1033))
            .navigationBarsPadding()
            .height(60.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateToSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color(0xFF3B82F6))
        }
        IconButton(onClick = onNavigateToSearch) {
            Icon(Icons.Default.Edit, contentDescription = "Поиск / Чаты", tint = Color(0xFF3B82F6))
        }
    }
}
