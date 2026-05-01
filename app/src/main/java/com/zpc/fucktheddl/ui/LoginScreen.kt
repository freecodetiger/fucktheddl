package com.zpc.fucktheddl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    sending: Boolean,
    verifying: Boolean,
    message: String,
    onRequestCode: (String) -> Unit,
    onVerifyCode: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("DDL Agent", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("输入邮箱，使用验证码登录。", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                enabled = !sending && email.contains("@"),
                onClick = { onRequestCode(email.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(if (sending) "发送中..." else "发送验证码")
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("验证码") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                enabled = !verifying && email.contains("@") && code.length == 6,
                onClick = { onVerifyCode(email.trim(), code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(if (verifying) "登录中..." else "登录")
            }
            if (message.isNotBlank()) {
                Text(message, modifier = Modifier.padding(top = 14.dp), fontSize = 13.sp)
            }
        }
    }
}
