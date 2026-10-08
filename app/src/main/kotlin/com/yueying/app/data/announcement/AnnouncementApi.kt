package com.yueying.app.data.announcement

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 公告客户端 — 从 GitHub Raw 拉取 announcements.json
 * 格式：{"list": [{"id":"1","title":"...","summary":"...","content":"...","isPinned":true,"createdAt":"2026-10-08"}]}
 */
object AnnouncementApi {

    const val BASE_URL = "https://raw.githubusercontent.com/Wuaqingzhi/YueYing/main/announcements.json"

    private const val TAG = "YY-Announce"

    data class Announcement(
        val id: String,
        val title: String,
        val summary: String,
        val content: String,
        val coverImage: String?,
        val images: List<String>,
        val publishAt: String?,
        val author: String,
        val isPinned: Boolean,
        val createdAt: String,
    ) {
        val effectiveMillis: Long get() = parseIsoMillis(publishAt) ?: parseIsoMillis(createdAt) ?: 0L
    }

    data class Page(
        val total: Int,
        val list: List<Announcement>,
    )

    sealed interface Result<out T> {
        data class Success<T>(val data: T) : Result<T>
        data class Failure(val message: String) : Result<Nothing>
    }

    suspend fun fetchPage(page: Int = 1): Result<Page> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val req = Request.Builder()
                .url(BASE_URL)
                .header("User-Agent", "YueYing")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) return@withContext Result.Failure("响应为空")
                val json = JSONObject(text)
                val arr = json.optJSONArray("list") ?: JSONArray()
                val list = parseArray(arr)
                Log.i(TAG, "拉取公告 ${list.size} 条")
                Result.Success(Page(total = list.size, list = list))
            }
        } catch (e: Exception) {
            Log.e(TAG, "公告请求失败: ${e.message}", e)
            Result.Failure(e.message ?: "网络异常")
        }
    }

    suspend fun fetchDetail(id: String): Result<Announcement> {
        return when (val r = fetchPage()) {
            is Result.Success -> {
                val item = r.data.list.find { it.id == id }
                if (item != null) Result.Success(item) else Result.Failure("公告不存在")
            }
            is Result.Failure -> r
        }
    }

    private fun parseArray(arr: JSONArray): List<Announcement> {
        val out = ArrayList<Announcement>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Announcement(
                id = o.optString("id", i.toString()),
                title = o.optString("title", ""),
                summary = o.optString("summary", ""),
                content = o.optString("content", ""),
                coverImage = if (o.isNull("coverImage")) null else o.optString("coverImage", null),
                images = o.optJSONArray("images")?.let {
                    (0 until it.length()).mapNotNull { idx -> it.optString(idx).takeIf { s -> s.isNotEmpty() } }
                } ?: emptyList(),
                publishAt = if (o.isNull("publishAt")) null else o.optString("publishAt", null),
                author = o.optString("author", ""),
                isPinned = o.optBoolean("isPinned", false),
                createdAt = o.optString("createdAt", "")
            ))
        }
        out.sortWith(compareByDescending<Announcement> { it.isPinned }.thenByDescending { it.effectiveMillis })
        return out
    }
}

internal fun parseIsoMillis(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(s.replace("Z", "").substringBefore("."))?.time
    } catch (e: Exception) { null }
}
