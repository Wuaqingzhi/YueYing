/*
 * YueYing (月影) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
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

package com.yueying.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.yueying.app.util.DiagnosticLog
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed class CredentialKeyException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class PermanentlyInvalid(reason: String, cause: Throwable? = null) :
        CredentialKeyException("本机密钥不可用：$reason", cause)

    class Unavailable(reason: String, cause: Throwable? = null) :
        CredentialKeyException("本机密钥暂时不可用：$reason", cause)
}

object CredentialStore {
    private const val PREFS = "yueying_settings"
    private const val KEY_LOST_FLAG = "credential_key_lost"

    @Volatile
    private var appContext: Context? = null

    internal const val LOST_TITLE = "登录状态已失效"
    internal const val LOST_MESSAGE =
        "本机加密密钥不可用（常见于修改锁屏密码、指纹或系统升级后），已保存的网盘登录凭证与 " +
            "GitHub Token 无法再解密，需要重新登录。\n\n" +
            "如果之前导出过认证备份，可在「设置 → 认证备份」里导入恢复。"

    internal fun isKeyFailure(error: Throwable): Boolean = error is CredentialKeyException

    internal fun isKeyLost(error: Throwable): Boolean =
        error is CredentialKeyException.PermanentlyInvalid

    internal fun markKeyLost() {
        appContext?.let { markKeyLost(it) }
    }

    internal fun markKeyLost(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LOST_FLAG, true)
                .commit()
        }
    }

    fun hasKeyLostNotice(context: Context): Boolean = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOST_FLAG, false)
    }.getOrDefault(false)

    fun consumeKeyLostNotice(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOST_FLAG)
                .commit()
        }
    }

    fun installRecovery(context: Context) {
        val app = context.applicationContext
        appContext = app
        AndroidKeystoreCredentialCipher.shared.onKeyProvisioned { markKeyLost(app) }
    }
}

internal interface CredentialCipher {
    fun encrypt(plaintext: String, purpose: String): String
    fun decrypt(stored: String, purpose: String): String
    fun isEncrypted(stored: String): Boolean
    fun onKeyProvisioned(listener: () -> Unit)
}

internal class AndroidKeystoreCredentialCipher : CredentialCipher {

    @Volatile
    private var cachedKey: SecretKey? = null

    @Volatile
    private var provisionedListener: (() -> Unit)? = null

    override fun onKeyProvisioned(listener: () -> Unit) {
        provisionedListener = listener
    }

    override fun encrypt(plaintext: String, purpose: String): String {
        val aad = purpose.toByteArray(Charsets.UTF_8)
        val plain = plaintext.toByteArray(Charsets.UTF_8)
        DiagnosticLog.log(
            DiagnosticLog.CRYPTO, "encrypt", size = plain.size.toLong(),
            summary = "purpose=$purpose"
        )
        return withRetry {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plain)
            listOf(
                PREFIX,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            ).joinToString(":")
        }
    }

    override fun decrypt(stored: String, purpose: String): String {
        if (!isEncrypted(stored)) return stored
        DiagnosticLog.log(
            DiagnosticLog.CRYPTO, "decrypt", size = stored.length.toLong(),
            summary = "purpose=$purpose"
        )
        val parts = stored.split(':', limit = 4)
        require(parts.size == 4 && parts[0] == "yueying" && parts[1] == "v1") {
            "Unsupported encrypted credential format"
        }
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[3], Base64.NO_WRAP)
        val aad = purpose.toByteArray(Charsets.UTF_8)
        return withRetry {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }
    }

    override fun isEncrypted(stored: String): Boolean = stored.startsWith("$PREFIX:")

    private inline fun <T> withRetry(block: () -> T): T = try {
        block()
    } catch (first: Throwable) {
        if (!isKeyProblem(first)) throw first
        discardStaleEntry()
        try {
            block()
        } catch (second: Throwable) {
            throw if (isKeyProblem(second)) PermanentlyInvalid(second) else second
        }
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val key = try {
                loadOrCreateKey()
            } catch (first: Throwable) {
                if (!isKeyProblem(first)) throw first
                discardStaleEntry()
                try {
                    loadOrCreateKey()
                } catch (second: Throwable) {
                    throw if (isKeyProblem(second)) PermanentlyInvalid(second) else second
                }
            }
            cachedKey = key
            return key
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = openKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return createKey()
    }

    private fun openKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        val created = generator.generateKey()
        runCatching { provisionedListener?.invoke() }
        return created
    }

    private fun discardStaleEntry() {
        val keyStore = try {
            openKeyStore()
        } catch (error: Throwable) {
            throw CredentialKeyException.Unavailable(describe(error), error)
        }
        cachedKey = null
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            .onFailure { Log.w(TAG, "删除不可用的 Keystore 条目失败：${describe(it)}") }
        Log.w(TAG, "已删除不可用的 Keystore 条目 $KEY_ALIAS，将生成新密钥")
    }

    private fun PermanentlyInvalid(error: Throwable) =
        CredentialKeyException.PermanentlyInvalid(describe(error), error)

    companion object {
        const val TAG = "YueYing"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "yueying.account.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "yueying:v1"
        const val GCM_TAG_BITS = 128

        val shared: CredentialCipher by lazy { AndroidKeystoreCredentialCipher() }

        fun isKeyProblem(error: Throwable): Boolean {
            if (error is CredentialKeyException) return error is CredentialKeyException.PermanentlyInvalid
            if (error is AEADBadTagException) return false
            if (error is javax.crypto.NoSuchPaddingException) return false
            if (error is java.security.NoSuchAlgorithmException) return false
            if (error is android.security.KeyStoreException) return true
            if (error is java.security.ProviderException) return true
            if (error is java.security.GeneralSecurityException) return true
            val message = error.message.orEmpty()
            return message.contains("keystore", ignoreCase = true) ||
                message.contains("key blob", ignoreCase = true) ||
                message.contains("key not found", ignoreCase = true)
        }

        fun describe(error: Throwable): String {
            val type = error::class.java.simpleName.ifBlank { "Keystore 异常" }
            val detail = error.message.orEmpty().replace('\n', ' ').trim()
            return if (detail.isEmpty()) type else "$type: ${detail.take(160)}"
        }
    }
}
