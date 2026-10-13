package com.example.olo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.olo.data.ConnectionStatus
import kotlinx.coroutines.delay

@Composable
fun ConnectionStatusBanner(status: ConnectionStatus, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(status) {
        visible = true
        if (status == ConnectionStatus.CONNECTED || status == ConnectionStatus.SERVER_AVAILABLE) {
            delay(2_200)
            visible = false
        }
    }
    if (!visible) return

    val (label, color) = when (status) {
        ConnectionStatus.CONNECTED -> "Подключено" to Color(0xFF3BAA72)
        ConnectionStatus.SERVER_AVAILABLE -> "Сервер доступен" to Color(0xFF3BAA72)
        ConnectionStatus.CONNECTING -> "Подключаемся к серверу…" to Color(0xFF6B8AFF)
        ConnectionStatus.RECONNECTING -> "Переподключаемся…" to Color(0xFFE6AD45)
        ConnectionStatus.ERROR -> "Ошибка соединения. Повторяем подключение…" to Color(0xFFE45C64)
        ConnectionStatus.DISCONNECTED -> "Нет соединения" to Color(0xFF8588A8)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("●", color = color, fontSize = 12.sp)
        Text("  $label", color = Color.White, fontSize = 12.sp)
    }
}
