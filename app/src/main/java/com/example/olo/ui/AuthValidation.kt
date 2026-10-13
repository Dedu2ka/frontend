package com.example.olo.ui

private val usernamePattern = Regex("^[A-Za-z0-9]+$")
private val passwordPattern = Regex("^[A-Za-z0-9!@#\$%^&*()_\\-+=\\[\\]{}|;:'\",.<>/?~`]+$")

fun validateAuthInput(username: String, password: String): String? {
    val normalizedUsername = username.trim()
    val normalizedPassword = password.trim()
    if (normalizedUsername.isBlank()) return "Введите логин"
    if (!usernamePattern.matches(normalizedUsername)) {
        return "Логин может содержать только латинские буквы и цифры"
    }
    if (normalizedUsername.length > 100) return "Логин должен содержать не более 100 символов"
    if (normalizedPassword.isBlank()) return "Введите пароль"
    if (normalizedPassword.length < 8) return "Пароль должен содержать минимум 8 символов"
    if (!passwordPattern.matches(normalizedPassword)) {
        return "Пароль может содержать только латинские буквы, цифры и спецсимволы"
    }
    return null
}
