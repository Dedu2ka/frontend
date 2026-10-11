package com.example.olo.ui

import androidx.compose.runtime.Composable

@Composable
fun AuthScreen(
    initialUsername: String,
    errorMessage: String?,
    onLoginClick: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    AuthFormScreen(
        title = "Вход",
        submitLabel = "Войти",
        navigationLabel = "Нет аккаунта? Регистрация.",
        initialUsername = initialUsername,
        errorMessage = errorMessage,
        onSubmit = onLoginClick,
        onNavigate = onNavigateToRegister
    )
}
