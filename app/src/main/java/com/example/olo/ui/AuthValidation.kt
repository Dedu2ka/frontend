package com.example.olo.ui

private val usernamePattern = Regex("^[A-Za-z0-9]+$")
private val passwordPattern = Regex("^[A-Za-z0-9!@#\$%^&*()_\\-+=\\[\\]{}|;:'\",.<>/?~`]+$")

fun validateAuthInput(username: String, password: String): String? {
    if (username.isBlank()) return "Введите логин"
    if (!usernamePattern.matches(username)) {
        return "Логин может содержать только латинские буквы и цифры"
    }
    if (username.length > 100) return "Логин должен содержать не более 100 символов"
    if (password.isBlank()) return "Введите пароль"
    if (password.length < 8) return "Пароль должен содержать минимум 8 символов"
    if (!passwordPattern.matches(password)) {
        return "Пароль может содержать только латинские буквы, цифры и спецсимволы"
    }
    return null
}
