package com.yueying.app.data.announcement

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 公告客户端 — 从 GitHub Raw 拉取 announcements.json
 * 格式：{"list": [{"id":"1","title":"...","summary":"...","content":"...","isPinned":true,"createdAt":"2026-10-08"}]}
 *
 * 模型字段对齐 YunX 远程公告接口（publisher / viewCount / updatedAt 等），
 * 服务端 JSON 里没有的字段一律用安全默认值兜底，保证旧公告数据也能正常渲染。
 */
object AnnouncementApi {

    const val BASE_URL = "https://raw.githubusercontent.com/Wuaqingzhi/YueYing/main/announcements.json"

    /** 列表分页大小：GitHub Raw 是一次性取全量，这里给个足够大的常量占位（ViewModel 按 PAGE_SIZE 调用） */
    const val PAGE_SIZE = 100

    private const val TAG = "YY-Announce"

    /** 发布者（服务端 `publisher` 对象）；JSON 缺失时用空名 + 无头像兜底 */
    data class Publisher(val name: String, val avatarUrl: String?)

    data class Announcement(
        val id: String,
        val title: String,
        val summary: String,
        val content: String?,
        val coverImage: String?,
        val images: List<String>,
        val publishAt: String?,
        val author: String,
        val viewCount: Long,
        val publisher: Publisher,
        val isPinned: Boolean,
        val createdAt: String,
        val updatedAt: String,
    ) {
        val effectiveMillis: Long get() = parseIsoMillis(publishAt) ?: parseIsoMillis(createdAt) ?: 0L
    }

    data class Page(
        val total: Int,
        val page: Int,
        val pageSize: Int,
        val totalPages: Int,
        val hasMore: Boolean,
        val list: List<Announcement>,
    )

    sealed interface Result<out T> {
        data class Success<T>(val data: T) : Result<T>
        data class Failure(val message: String) : Result<Nothing>
    }

    suspend fun fetchPage(page: Int = 1, pageSize: Int = PAGE_SIZE): Result<Page> = withContext(Dispatchers.IO) {
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
                // GitHub Raw 一次取全量：只有一页，hasMore 恒为 false
                Result.Success(
                    Page(
                        total = list.size,
                        page = page,
                        pageSize = pageSize,
                        totalPages = 1,
                        hasMore = false,
                        list = list
                    )
                )
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
            val publisherObj = o.optJSONObject("publisher")
            out.add(Announcement(
                id = o.optString("id", i.toString()),
                title = o.optString("title", ""),
                summary = o.optString("summary", ""),
                content = if (o.isNull("content")) null else o.optString("content", null),
                coverImage = if (o.isNull("coverImage")) null else o.optString("coverImage", null),
                images = o.optJSONArray("images")?.let {
                    (0 until it.length()).mapNotNull { idx -> it.optString(idx).takeIf { s -> s.isNotEmpty() } }
                } ?: emptyList(),
                publishAt = if (o.isNull("publishAt")) null else o.optString("publishAt", null),
                author = o.optString("author", ""),
                viewCount = o.optLong("viewCount"),
                publisher = Publisher(
                    name = publisherObj?.optString("name", "").orEmpty(),
                    avatarUrl = if (publisherObj == null || publisherObj.isNull("avatarUrl")) null
                    else publisherObj.optString("avatarUrl", null)
                ),
                isPinned = o.optBoolean("isPinned", false),
                createdAt = o.optString("createdAt", ""),
                updatedAt = if (o.isNull("updatedAt")) o.optString("createdAt", "") else o.optString("updatedAt", "")
            ))
        }
        out.sortWith(compareByDescending<Announcement> { it.isPinned }.thenByDescending { it.effectiveMillis })
        return out
    }
}
