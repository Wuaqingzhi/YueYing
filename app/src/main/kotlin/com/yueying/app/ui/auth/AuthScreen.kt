/*
 * YueYing (月影) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 月影 (YueYing) Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yueying.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yueying.app.R
import com.yueying.app.ui.viewmodel.AuthViewModel

/**
 * 月影 v2.2 账号页：登录 / 注册 / 找回密码（三视图切换）。
 * - 登录：邮箱 + 密码 + 记住密码
 * - 注册：邮箱 + 密码 + 确认密码
 * - 找回密码：邮箱验证码校验后重置
 */
@Composable
fun AuthScreen(
    onLoggedIn: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val navToLogin by viewModel.navToLogin.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(Mode.LOGIN) }

    // 登录表单
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var showPass by remember { mutableStateOf(false) }

    // 注册表单
    var regCode by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regConfirm by remember { mutableStateOf("") }
    var showRegPass by remember { mutableStateOf(false) }

    // 找回表单
    var code by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newConfirm by remember { mutableStateOf("") }
    var showNewPass by remember { mutableStateOf(false) }

    // 提交成功后：写入会话（已在 repo 完成），通知进入主界面
    LaunchedEffect(success) {
        if (success) onLoggedIn()
    }

    // 注册/重置成功后：跳回登录页
    LaunchedEffect(navToLogin) {
        if (navToLogin) {
            mode = Mode.LOGIN
            viewModel.consumeNavToLogin()
        }
    }

    // 提示消息
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(24.dp))

            // 月影 APP 图标（狗狗）
            Image(
                painter = painterResource(R.drawable.yy_logo),
                contentDescription = "月影",
                modifier = Modifier.size(84.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "月影",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (mode) {
                    Mode.LOGIN -> "登录后即可使用解析与高速下载"
                    Mode.REGISTER -> "注册账号，开启网盘解析之旅"
                    Mode.FORGOT -> "邮箱验证码验证后重置密码"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            when (mode) {
                Mode.LOGIN -> LoginForm(
                    email = email, onEmail = { email = it },
                    password = password, onPassword = { password = it },
                    showPass = showPass, onTogglePass = { showPass = !showPass },
                    remember = remember, onRemember = { remember = it },
                    loading = loading,
                    onLogin = { viewModel.login(email, password, remember) },
                    onGoRegister = { mode = Mode.REGISTER },
                    onGoForgot = { mode = Mode.FORGOT }
                )

                Mode.REGISTER -> RegisterForm(
                    email = email, onEmail = { email = it },
                    code = regCode, onCode = { regCode = it.filter { c -> c.isDigit() }.take(6) },
                    password = regPass, onPassword = { regPass = it },
                    confirm = regConfirm, onConfirm = { regConfirm = it },
                    showPass = showRegPass, onTogglePass = { showRegPass = !showRegPass },
                    loading = loading, sending = sending, countdown = countdown,
                    onSendCode = { viewModel.sendRegisterCode(email) },
                    onRegister = { viewModel.register(email, regPass, regConfirm, regCode) },
                    onBack = { mode = Mode.LOGIN }
                )

                Mode.FORGOT -> ForgotForm(
                    email = email, onEmail = { email = it },
                    code = code, onCode = { code = it.filter { c -> c.isDigit() }.take(6) },
                    newPass = newPass, onNewPass = { newPass = it },
                    newConfirm = newConfirm, onNewConfirm = { newConfirm = it },
                    showPass = showNewPass, onTogglePass = { showNewPass = !showNewPass },
                    loading = loading, sending = sending, countdown = countdown,
                    onSendCode = { viewModel.sendCode(email) },
                    onReset = { viewModel.resetPassword(email, code, newPass, newConfirm) },
                    onBack = { mode = Mode.LOGIN }
                )
            }

            Spacer(Modifier.height(20.dp))

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LoginForm(
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    showPass: Boolean, onTogglePass: () -> Unit,
    remember: Boolean, onRemember: (Boolean) -> Unit,
    loading: Boolean,
    onLogin: () -> Unit,
    onGoRegister: () -> Unit,
    onGoForgot: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        placeholder = { Text("请输入邮箱地址") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = password,
        onValueChange = onPassword,
        label = "密码",
        show = showPass,
        onToggle = onTogglePass
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = remember, onCheckedChange = onRemember)
            Text("记住密码", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onGoForgot) { Text("忘记密码？") }
    }

    Button(
        onClick = onLogin,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = !loading
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("登 录", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        text = "还没有账号？",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = onGoRegister) { Text("去注册") }
}

@Composable
private fun RegisterForm(
    email: String, onEmail: (String) -> Unit,
    code: String, onCode: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    confirm: String, onConfirm: (String) -> Unit,
    showPass: Boolean, onTogglePass: () -> Unit,
    loading: Boolean, sending: Boolean, countdown: Int,
    onSendCode: () -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        placeholder = { Text("请输入邮箱地址") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(Modifier.height(12.dp))

    // 验证码 + 发送按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCode,
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            placeholder = { Text("6 位数字") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onSendCode,
            enabled = !sending && countdown == 0,
            modifier = Modifier.height(56.dp)
        ) {
            Text(
                if (countdown > 0) "${countdown}s"
                else if (sending) "发送中"
                else "发送验证码"
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = password,
        onValueChange = onPassword,
        label = "密码（至少 8 位）",
        show = showPass,
        onToggle = onTogglePass
    )
    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = confirm,
        onValueChange = onConfirm,
        label = "确认密码",
        show = showPass,
        onToggle = onTogglePass
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onRegister,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = !loading
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("注 册", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack) { Text("已有账号？去登录") }
}

@Composable
private fun ForgotForm(
    email: String, onEmail: (String) -> Unit,
    code: String, onCode: (String) -> Unit,
    newPass: String, onNewPass: (String) -> Unit,
    newConfirm: String, onNewConfirm: (String) -> Unit,
    showPass: Boolean, onTogglePass: () -> Unit,
    loading: Boolean, sending: Boolean, countdown: Int,
    onSendCode: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        placeholder = { Text("请输入邮箱地址") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(Modifier.height(12.dp))

    // 验证码 + 发送按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = onCode,
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            placeholder = { Text("6 位数字") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onSendCode,
            enabled = !sending && countdown == 0,
            modifier = Modifier.height(56.dp)
        ) {
            Text(
                if (countdown > 0) "${countdown}s"
                else if (sending) "发送中"
                else "发送验证码"
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = newPass,
        onValueChange = onNewPass,
        label = "新密码（至少 8 位）",
        show = showPass,
        onToggle = onTogglePass
    )
    Spacer(Modifier.height(12.dp))

    PasswordField(
        value = newConfirm,
        onValueChange = onNewConfirm,
        label = "确认新密码",
        show = showPass,
        onToggle = onTogglePass
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = !loading
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("重置密码", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack) { Text("返回登录") }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    show: Boolean,
    onToggle: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (show) "隐藏密码" else "显示密码"
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

private enum class Mode { LOGIN, REGISTER, FORGOT }
