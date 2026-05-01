package com.zpc.fucktheddl.auth

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
) {
    val isLoggedIn: Boolean = userId.isNotBlank() && email.isNotBlank() && accessToken.isNotBlank()
}

data class LoginCodeVerifyResult(
    val userId: String = "",
    val email: String = "",
    val accessToken: String = "",
    val newlyCreated: Boolean = false,
    val error: String? = null,
)
