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

package com.yueying.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 月影应用账号（v2.3 云端版）：邮箱 + 密码登录 / 注册 / 邮箱验证码找回密码。
 * - 账号数据全部存云端（CloudBase 云函数 + MySQL），卸载重装/换机不丢账号；
 * - 会话：登录成功返回 token，「记住密码」持久化保存，否则仅本进程有效；
 * - 验证码由云端 SMTP 发送并校验。
 */
class AuthRepository(private val context: Context) {

    companion object {
        private const val PREFS = "yueying_session"
        private const val KEY_TOKEN = "session_token"
        private const val KEY_SESSION = "session_email"
        private const val API_BASE = "https://yueying-app1-d7gtp9jo6ec5b0305.service.tcloudbase.com"

        @Volatile
        private var memoryToken: String? = null

        @Volatile
        private var memoryEmail: String? = null

        /** 当前是否已登录（持久会话或本进程会话） */
        fun isLoggedIn(context: Context): Boolean = currentEmail(context) != null

        /** 当前登录邮箱；未登录返回 null */
        fun currentEmail(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_SESSION, null)?.ifBlank { null }
            return saved ?: memoryEmail
        }

        /** 当前会话 token；未登录返回 null */
        fun currentToken(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_TOKEN, null)?.ifBlank { null }
            return saved ?: memoryToken
        }

        /** 退出登录：清除持久会话与本进程会话（云端会话由服务端过期） */
        fun logout(context: Context) {
            val token = currentToken(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TOKEN).remove(KEY_SESSION).apply()
            memoryToken = null
            memoryEmail = null
            if (token != null) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)
                        .build()
                    val json = JSONObject().put("token", token)
                    val request = Request.Builder()
                        .url(API_BASE + "/auth_logout")
                        .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    client.newCall(request).execute().use { it.body?.close() }
                } catch (_: Exception) {
                }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class AuthResult {
        data class Success(val email: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    private suspend fun post(path: String, body: Map<String, Any?>): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                body.forEach { (k, v) -> json.put(k, v) }
                val request = Request.Builder()
                    .url(API_BASE + path)
                    .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: return@withContext null
                    JSONObject(text)
                }
            } catch (_: Exception) {
                null
            }
        }

    /** 注册：云端校验邮箱格式 + 邮箱验证码 + 查重 + 哈希入库 */
    suspend fun register(email: String, password: String, confirm: String, code: String): AuthResult {
        val mail = email.trim().lowercase()
        if (!VALID_EMAIL.matches(mail)) return AuthResult.Error("请输入正确的邮箱格式")
        if (code.isBlank()) return AuthResult.Error("请输入验证码")
        if (password.length < 8) return AuthResult.Error("密码至少 8 位")
        if (password != confirm) return AuthResult.Error("两次输入的密码不一致")

        val resp = post("/auth_register", mapOf(
            "email" to mail, "password" to password, "code" to code
        )) ?: return AuthResult.Error("网络异常，请检查网络后重试")
        return if (resp.optInt("code") == 0) AuthResult.Success(mail)
        else AuthResult.Error(resp.optString("msg", "注册失败，请重试"))
    }

    /** 发送注册验证码（云端校验邮箱未注册，SMTP 直发） */
    suspend fun sendRegisterCode(email: String): AuthResult = withContext(Dispatchers.IO) {
        val mail = email.trim().lowercase()
        if (!VALID_EMAIL.matches(mail)) return@withContext AuthResult.Error("请输入正确的邮箱格式")
        val resp = post("/auth_sendcode", mapOf("email" to mail, "purpose" to "register"))
            ?: return@withContext AuthResult.Error("网络异常，请检查网络后重试")
        if (resp.optInt("code") == 0) AuthResult.Success(mail)
        else AuthResult.Error(resp.optString("msg", "验证码发送失败"))
    }

    /** 登录：云端校验密码，remember=true 持久化会话 */
    suspend fun login(email: String, password: String, remember: Boolean): AuthResult {
        val mail = email.trim().lowercase()
        if (!VALID_EMAIL.matches(mail)) return AuthResult.Error("请输入正确的邮箱格式")

        val resp = post("/auth_login", mapOf(
            "email" to mail, "password" to password, "remember" to remember
        )) ?: return AuthResult.Error("网络异常，请检查网络后重试")
        if (resp.optInt("code") != 0) return AuthResult.Error(resp.optString("msg", "登录失败"))

        val data = resp.optJSONObject("data")
        val token = data?.optString("token") ?: return AuthResult.Error("登录失败，请重试")

        if (remember) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TOKEN, token).putString(KEY_SESSION, mail).apply()
            memoryToken = null
            memoryEmail = null
        } else {
            memoryToken = token
            memoryEmail = mail
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TOKEN).remove(KEY_SESSION).apply()
        }
        return AuthResult.Success(mail)
    }

    /** 发送找回密码验证码（云端校验邮箱已注册，SMTP 直发） */
    suspend fun sendCode(email: String): AuthResult = withContext(Dispatchers.IO) {
        val mail = email.trim().lowercase()
        if (!VALID_EMAIL.matches(mail)) return@withContext AuthResult.Error("请输入正确的邮箱格式")
        val resp = post("/auth_sendcode", mapOf("email" to mail, "purpose" to "reset"))
            ?: return@withContext AuthResult.Error("网络异常，请检查网络后重试")
        if (resp.optInt("code") == 0) AuthResult.Success(mail)
        else AuthResult.Error(resp.optString("msg", "验证码发送失败"))
    }

    /** 找回密码：云端验证码校验通过后重置密码 */
    suspend fun resetPassword(email: String, code: String, newPass: String, confirm: String): AuthResult {
        val mail = email.trim().lowercase()
        if (!VALID_EMAIL.matches(mail)) return AuthResult.Error("请输入正确的邮箱格式")
        if (code.isBlank()) return AuthResult.Error("请输入验证码")
        if (newPass.length < 8) return AuthResult.Error("新密码至少 8 位")
        if (newPass != confirm) return AuthResult.Error("两次输入的新密码不一致")

        val resp = post("/auth_reset", mapOf(
            "email" to mail, "code" to code, "newPass" to newPass
        )) ?: return AuthResult.Error("网络异常，请检查网络后重试")
        return if (resp.optInt("code") == 0) AuthResult.Success(mail)
        else AuthResult.Error(resp.optString("msg", "重置失败，请重试"))
    }

    private val VALID_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}
