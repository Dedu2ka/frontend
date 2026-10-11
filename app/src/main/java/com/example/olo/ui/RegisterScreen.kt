package com.example.olo.ui

import androidx.compose.runtime.Composable

@Composable
fun RegisterScreen(
    onRegisterClick: (String, String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    AuthFormScreen(
        title = "Регистрация",
        submitLabel = "Зарегистрироваться",
        navigationLabel = "Есть аккаунт? Вход",
        onSubmit = onRegisterClick,
        onNavigate = onNavigateToLogin
    )
}
