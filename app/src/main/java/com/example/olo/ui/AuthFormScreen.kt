package com.example.olo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AuthFormScreen(
    title: String,
    submitLabel: String,
    navigationLabel: String,
    initialUsername: String = "",
    errorMessage: String? = null,
    onSubmit: (String, String) -> Unit,
    onNavigate: () -> Unit
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    val darkBg = Color(0xFF131541)
    val inputBg = Color(0xFF191B4E)
    val strokeColor = Color(0xFF2C3285)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(24.dp)
    ) {
        Text("olo", color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.TopStart))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.height(24.dp))

            (validationError ?: errorMessage)?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.Red, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
            }

            Text("Введите логин", color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filterNot(Char::isWhitespace); validationError = null },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = authFieldColors(inputBg, strokeColor)
            )

            Spacer(Modifier.height(16.dp))
            Text("Введите пароль", color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.filterNot(Char::isWhitespace); validationError = null },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "🙈" else "👁", color = Color.White)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = authFieldColors(inputBg, strokeColor)
            )

            Spacer(Modifier.height(16.dp))
            Text(
                navigationLabel,
                color = Color(0xFF3B82F6),
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onNavigate)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val error = validateAuthInput(username, password)
                    validationError = error
                    if (error == null) onSubmit(username, password)
                },
                colors = ButtonDefaults.buttonColors(containerColor = strokeColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(submitLabel, color = Color.White)
            }
        }
    }
}

@Composable
private fun authFieldColors(inputBg: Color, strokeColor: Color) = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = inputBg,
    focusedContainerColor = inputBg,
    unfocusedBorderColor = strokeColor,
    focusedBorderColor = strokeColor,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)
