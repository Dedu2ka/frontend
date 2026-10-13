package com.example.olo.data

import retrofit2.Response

sealed interface AccountResult {
    data class Authenticated(val session: AuthSession) : AccountResult
    data class Rejected(val message: String) : AccountResult
}

/** Owns the login/registration HTTP contract and persists a successful session. */
class AccountRepository(
    private val api: ApiService,
    private val sessionStore: AuthSessionStore
) {
    suspend fun login(username: String, password: String): AccountResult =
        authenticate(register = false, username = username, password = password)

    suspend fun register(username: String, password: String): AccountResult =
        authenticate(register = true, username = username, password = password)

    private suspend fun authenticate(register: Boolean, username: String, password: String): AccountResult {
        val request = AuthRequest(username.trim(), password.trim())
        val response = if (register) api.register(request) else api.login(request)
        if (!response.isSuccessful) {
            return AccountResult.Rejected(authenticationError(register, response))
        }

        val body = response.body()
            ?: return AccountResult.Rejected("Сервер не вернул данные аккаунта")
        val session = AuthSession(body.username, body.userId, body.token, body.refreshToken)
        sessionStore.saveSession(session)
        return AccountResult.Authenticated(session)
    }

    private fun authenticationError(register: Boolean, response: Response<AuthResponse>): String {
        if (!register) return "Неверный логин или пароль"
        return when (response.code()) {
            409 -> "Этот логин уже занят"
            400 -> ApiErrorParser.message(response.errorBody()?.string()) ?: "Проверьте логин и пароль"
            else -> "Ошибка регистрации (HTTP ${response.code()})"
        }
    }

}
