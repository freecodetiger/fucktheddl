package com.zpc.fucktheddl.auth

import com.zpc.fucktheddl.agent.AgentApiClient

class AuthRepository(
    private val client: AgentApiClient,
) {
    fun requestCode(email: String): String? {
        return client.requestLoginCode(email)
    }

    fun verifyCode(email: String, code: String): LoginCodeVerifyResult {
        return client.verifyLoginCode(email, code)
    }
}
