package com.example.olo.data

import retrofit2.Response

sealed interface UserResult<out T> {
    data class Success<T>(val value: T) : UserResult<T>
    data class Failure(val statusCode: Int, val message: String?) : UserResult<Nothing>
}

/** Converts profile and search HTTP responses into a small UI-facing result type. */
class UserRepository(private val api: ApiService) {
    suspend fun getProfile(token: String, userId: String): UserResult<UserDto> =
        api.getCurrentUser(bearer(token), userId).toUserResult()

    suspend fun updateProfile(
        token: String,
        userId: String,
        request: UpdateProfileRequest
    ): UserResult<UserDto> = api.updateProfile(bearer(token), userId, request).toUserResult()

    suspend fun search(token: String, query: String): UserResult<List<UserDto>> =
        api.searchUsers(bearer(token), query).toUserResult()

    private fun <T : Any> Response<T>.toUserResult(): UserResult<T> {
        if (isSuccessful) {
            body()?.let { return UserResult.Success(it) }
        }
        return UserResult.Failure(code(), ApiErrorParser.message(errorBody()?.string()))
    }

    private fun bearer(token: String): String = if (token.startsWith("Bearer ")) token else "Bearer $token"
}
