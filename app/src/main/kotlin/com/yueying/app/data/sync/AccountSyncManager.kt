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

package com.yueying.app.data.sync

import android.content.Context
import com.yueying.app.data.db.AppDatabase
import com.yueying.app.data.db.BaiduAccountEntity
import com.yueying.app.data.db.C139AccountEntity
import com.yueying.app.data.db.Pan123AccountEntity
import com.yueying.app.data.db.QuarkAccountEntity
import com.yueying.app.data.db.UCAccountEntity
import com.yueying.app.data.db.XunleiAccountEntity
import com.yueying.app.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 网盘账号云端同步（v2.3.2）：
 * - 登录月影账号后，网盘账号（夸克/UC/迅雷/百度/中国移动云盘/123云盘）自动上传云端；
 * - 换机/重装后登录同一账号，自动拉取恢复，无需重新导入。
 * 说明：网盘 Cookie 有有效期，过期后仍需在应用内重新登录对应网盘。
 */
object AccountSyncManager {

    private const val API_BASE = "https://yueying-app1-d7gtp9jo6ec5b0305.service.tcloudbase.com"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(API_BASE + path)
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: return@withContext null
                    JSONObject(text)
                }
            } catch (_: Exception) {
                null
            }
        }

    /** 把全部网盘账号上传到云端（未登录则跳过） */
    suspend fun pushAll(context: Context): Boolean {
        val token = AuthRepository.currentToken(context) ?: return false
        val db = AppDatabase.get(context)
        val accounts = JSONArray()

        db.quarkAccountDao().getAccount()?.let {
            accounts.put(JSONObject().put("provider", "quark").put("data", entityToJson(it)))
        }
        db.ucAccountDao().getAccount()?.let {
            accounts.put(JSONObject().put("provider", "uc").put("data", entityToJson(it)))
        }
        db.xunleiAccountDao().getAccount()?.let {
            accounts.put(JSONObject().put("provider", "xunlei").put("data", entityToJson(it)))
        }
        db.baiduAccountDao().getAccount()?.let {
            accounts.put(JSONObject().put("provider", "baidu").put("data", entityToJson(it)))
        }
        db.c139AccountDao().getAccount()?.let {
            accounts.put(JSONObject().put("provider", "c139").put("data", entityToJson(it)))
        }
        db.pan123AccountDao().getAccount()?.let {
            accounts.put(JSONObject().put("provider", "pan123").put("data", entityToJson(it)))
        }

        if (accounts.length() == 0) return true
        val body = JSONObject()
            .put("token", token)
            .put("action", "upload")
            .put("accounts", accounts)
        val resp = post("/pan_sync", body) ?: return false
        return resp.optInt("code") == 0
    }

    /** 从云端拉取网盘账号并恢复到本地（未登录则跳过） */
    suspend fun pullAndRestore(context: Context): Boolean {
        val token = AuthRepository.currentToken(context) ?: return false
        val body = JSONObject().put("token", token).put("action", "download")
        val resp = post("/pan_sync", body) ?: return false
        if (resp.optInt("code") != 0) return false
        val accounts = resp.optJSONObject("data")?.optJSONArray("accounts") ?: return true
        val db = AppDatabase.get(context)

        withContext(Dispatchers.IO) {
            for (i in 0 until accounts.length()) {
                val acc = accounts.optJSONObject(i) ?: continue
                val provider = acc.optString("provider")
                val data = acc.optString("data")
                if (data.isBlank()) continue
                runCatching {
                    val json = JSONObject(data)
                    when (provider) {
                        "quark" -> db.quarkAccountDao().upsert(
                            QuarkAccountEntity(
                                cookie = json.optString("cookie"),
                                nickname = json.optString("nickname"),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                        "uc" -> db.ucAccountDao().upsert(
                            UCAccountEntity(
                                cookie = json.optString("cookie"),
                                nickname = json.optString("nickname"),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                        "xunlei" -> db.xunleiAccountDao().upsert(
                            XunleiAccountEntity(
                                accessToken = json.optString("accessToken"),
                                refreshToken = json.optString("refreshToken"),
                                deviceId = json.optString("deviceId"),
                                captchaToken = json.optString("captchaToken"),
                                nickname = json.optString("nickname"),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                        "baidu" -> db.baiduAccountDao().upsert(
                            BaiduAccountEntity(
                                cookie = json.optString("cookie"),
                                nickname = json.optString("nickname"),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                        "c139" -> db.c139AccountDao().upsert(
                            C139AccountEntity(
                                cookie = json.optString("cookie"),
                                nickname = json.optString("nickname"),
                                authorization = json.optString("authorization"),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                        "pan123" -> db.pan123AccountDao().upsert(
                            Pan123AccountEntity(
                                accessToken = json.optString("accessToken"),
                                account = json.optString("account"),
                                nickname = json.optString("nickname"),
                                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }
        return true
    }

    private fun entityToJson(e: Any): String {
        val j = JSONObject()
        when (e) {
            is QuarkAccountEntity -> {
                j.put("cookie", e.cookie)
                j.put("nickname", e.nickname)
                j.put("updatedAt", e.updatedAt)
            }
            is UCAccountEntity -> {
                j.put("cookie", e.cookie)
                j.put("nickname", e.nickname)
                j.put("updatedAt", e.updatedAt)
            }
            is XunleiAccountEntity -> {
                j.put("accessToken", e.accessToken)
                j.put("refreshToken", e.refreshToken)
                j.put("deviceId", e.deviceId)
                j.put("captchaToken", e.captchaToken)
                j.put("nickname", e.nickname)
                j.put("updatedAt", e.updatedAt)
            }
            is BaiduAccountEntity -> {
                j.put("cookie", e.cookie)
                j.put("nickname", e.nickname)
                j.put("updatedAt", e.updatedAt)
            }
            is C139AccountEntity -> {
                j.put("cookie", e.cookie)
                j.put("nickname", e.nickname)
                j.put("authorization", e.authorization)
                j.put("updatedAt", e.updatedAt)
            }
            is Pan123AccountEntity -> {
                j.put("accessToken", e.accessToken)
                j.put("account", e.account)
                j.put("nickname", e.nickname)
                j.put("updatedAt", e.updatedAt)
            }
        }
        return j.toString()
    }
}
