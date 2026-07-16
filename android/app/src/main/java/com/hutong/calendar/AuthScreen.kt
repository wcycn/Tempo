package com.hutong.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScreen(state: AuthState, onLogin: (String, String) -> Unit, onRegister: (String, String, String, String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var registerMode by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    val error = formError ?: (state as? AuthState.Error)?.message

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        TextButton(onClick = onBack) { Text("‹ 返回日历", color = Muted) }
        Image(
            painter = painterResource(com.hutong.calendar.R.drawable.logo),
            contentDescription = "互通日历 Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(18.dp))
        Text("Tempo", color = MainText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(if (registerMode) "创建你的时间空间" else "登录后同步你的日程", color = Muted, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))
        if (registerMode) {
            OutlinedTextField(displayName, { displayName = it }, label = { Text("昵称（对外显示名称）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(account, { account = it }, label = { Text(if (registerMode) "用户名（登录账号，不能重复）" else "用户名或邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(18.dp))
        if (state is AuthState.Loading) {
            CircularProgressIndicator(color = Coral)
        } else {
            Button(onClick = {
                val validationError = when {
                    account.isBlank() -> "请输入用户名"
                    registerMode && account.length < 3 -> "用户名至少需要3个字符"
                    registerMode && account.any { it.isWhitespace() } -> "用户名不能包含空格"
                    registerMode && !email.contains("@") -> "请输入有效邮箱"
                    password.isBlank() -> "请输入密码"
                    registerMode && password.length < 8 -> "密码至少需要8位"
                    registerMode && displayName.isBlank() -> "请输入昵称"
                    else -> null
                }
                formError = validationError
                if (validationError == null) {
                    if (registerMode) onRegister(account, email, password, displayName)
                    else onLogin(account, password)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (registerMode) "注册并登录" else "登录") }
            TextButton(onClick = { registerMode = !registerMode }, modifier = Modifier.fillMaxWidth()) {
                Text(if (registerMode) "已有账号，返回登录" else "还没有账号？注册")
            }
        }
        Text("当前服务地址：${BuildConfig.API_BASE_URL}", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 18.dp))
    }
    }
}
