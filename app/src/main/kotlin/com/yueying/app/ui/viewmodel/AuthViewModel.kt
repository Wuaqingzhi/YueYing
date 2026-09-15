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

package com.yueying.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yueying.app.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 账号 ViewModel（v2.2）：注册 / 登录（记住密码）/ 邮箱验证码找回密码。
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository(application)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    /** 发送验证码倒计时秒数；0 表示可发送 */
    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success

    /** 注册/重置成功后跳回登录页的信号 */
    private val _navToLogin = MutableStateFlow(false)
    val navToLogin: StateFlow<Boolean> = _navToLogin

    /** 注册 */
    fun register(email: String, password: String, confirm: String, code: String) {
        if (_loading.value) return
        _loading.value = true
        _success.value = false
        viewModelScope.launch {
            when (val r = repo.register(email, password, confirm, code)) {
                is AuthRepository.AuthResult.Success -> {
                    _message.value = "注册成功，请登录"
                    _navToLogin.value = true
                }
                is AuthRepository.AuthResult.Error -> _message.value = r.message
            }
            _loading.value = false
        }
    }

    /** 发送注册验证码：成功则启动 60s 倒计时 */
    fun sendRegisterCode(email: String) {
        if (_sending.value || _countdown.value > 0) return
        _sending.value = true
        viewModelScope.launch {
            when (val r = repo.sendRegisterCode(email)) {
                is AuthRepository.AuthResult.Success -> {
                    _message.value = "验证码已发送，请查收邮箱"
                    _countdown.value = 60
                    launch {
                        while (_countdown.value > 0) {
                            delay(1000)
                            _countdown.value -= 1
                        }
                    }
                }
                is AuthRepository.AuthResult.Error -> _message.value = r.message
            }
            _sending.value = false
        }
    }

    /** 登录 */
    fun login(email: String, password: String, remember: Boolean) {
        if (_loading.value) return
        submit { repo.login(email, password, remember) }
    }

    /** 发送找回密码验证码：成功则启动 60s 倒计时 */
    fun sendCode(email: String) {
        if (_sending.value || _countdown.value > 0) return
        _sending.value = true
        viewModelScope.launch {
            when (val r = repo.sendCode(email)) {
                is AuthRepository.AuthResult.Success -> {
                    _message.value = "验证码已发送，请查收邮箱"
                    _countdown.value = 60
                    launch {
                        while (_countdown.value > 0) {
                            delay(1000)
                            _countdown.value -= 1
                        }
                    }
                }
                is AuthRepository.AuthResult.Error -> _message.value = r.message
            }
            _sending.value = false
        }
    }

    /** 找回密码：验证码 + 新密码重置 */
    fun resetPassword(email: String, code: String, newPass: String, confirm: String) {
        if (_loading.value) return
        _loading.value = true
        _success.value = false
        viewModelScope.launch {
            when (val r = repo.resetPassword(email, code, newPass, confirm)) {
                is AuthRepository.AuthResult.Success -> {
                    _message.value = "密码已重置，请登录"
                    _navToLogin.value = true
                }
                is AuthRepository.AuthResult.Error -> _message.value = r.message
            }
            _loading.value = false
        }
    }

    private fun submit(block: suspend () -> AuthRepository.AuthResult) {
        _loading.value = true
        _success.value = false
        viewModelScope.launch {
            when (val r = block()) {
                is AuthRepository.AuthResult.Success -> {
                    _message.value = null
                    _success.value = true
                }
                is AuthRepository.AuthResult.Error -> _message.value = r.message
            }
            _loading.value = false
        }
    }

    /** 消费消息（Snackbar 显示后清空） */
    fun consumeMessage() {
        _message.value = null
    }

    /** 消费「跳回登录页」信号 */
    fun consumeNavToLogin() {
        _navToLogin.value = false
    }
}
