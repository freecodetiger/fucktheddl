package com.zpc.fucktheddl.auth

import android.content.Context

class AuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun load(): AuthSession {
        return AuthSession(
            userId = preferences.getString("user_id", "").orEmpty(),
            email = preferences.getString("email", "").orEmpty(),
            accessToken = preferences.getString("access_token", "").orEmpty(),
        )
    }

    fun save(session: AuthSession) {
        preferences.edit()
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .putString("access_token", session.accessToken)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
