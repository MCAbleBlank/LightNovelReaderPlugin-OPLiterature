package org.opliterature.lnr.data

import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URLEncoder

/**
 * OP 文学社（www.opliterature.com）的接口封装。
 *
 * 站点是一个纯前端阅读器，数据全部走同源 JSON 接口：
 * - `GET /api/data`              整站目录（含各作品章节元信息，不含正文）
 * - `GET /api/content/<作品id>`  某部作品的正文，按章节顺序返回若干段落的数组
 * - `GET /api/data-version`      数据版本号，用来判断在线状态
 *
 * 作品 id 可能包含中文（例如 `imp-夏之妖-改`），因此所有路径片段都要做百分号编码。
 */
class OpLiteratureApi(private val client: HttpClient) {

    companion object {
        const val HOST = "https://www.opliterature.com"

        private const val CATALOG_TTL = 5 * 60 * 1000L
        private const val CONTENT_TTL = 60 * 60 * 1000L
        private const val MAX_CONTENT_CACHE = 16

        /** 把单个路径片段编码成 URL 安全形式 */
        fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

        /** 拼接站点上的接口地址 */
        fun url(vararg segments: String): String = buildString {
            append(HOST)
            for (segment in segments) {
                if (segment.isEmpty()) continue
                append('/').append(encodeSegment(segment))
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val catalogMutex = Mutex()
    private var catalogCache: SiteData? = null
    private var catalogTime = 0L

    private val contentMutex = Mutex()
    private val contentCache = mutableMapOf<String, Pair<Long, List<List<String>>>>()

    private suspend fun fetch(url: String): String {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value} @ $url")
        }
        return response.bodyAsText()
    }

    /** 整站目录，带 5 分钟内存缓存 */
    suspend fun catalog(force: Boolean = false): SiteData = catalogMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = catalogCache
        if (!force && cached != null && now - catalogTime < CATALOG_TTL) {
            return@withLock cached
        }
        val data = json.decodeFromString(SiteData.serializer(), fetch(url("api", "data")))
        catalogCache = data
        catalogTime = now
        data
    }

    /** 按 id 查找作品；缓存里没有时强制刷新一次目录，防止站点新书看不到 */
    suspend fun work(id: String): Work? {
        catalog().works.firstOrNull { it.id == id }?.let { return it }
        return catalog(force = true).works.firstOrNull { it.id == id }
    }

    /**
     * 某部作品的正文：[外层下标] 对应章节序号（从 0 开始），[内层] 为段落文本
     */
    suspend fun content(workId: String): List<List<String>> = contentMutex.withLock {
        val now = System.currentTimeMillis()
        contentCache[workId]?.let { (time, value) ->
            if (now - time < CONTENT_TTL) return@withLock value
        }
        val serializer = ListSerializer(ListSerializer(String.serializer()))
        val parsed = json.decodeFromString(serializer, fetch(url("api", "content", workId)))
        if (contentCache.size >= MAX_CONTENT_CACHE) contentCache.clear()
        contentCache[workId] = now to parsed
        parsed
    }

    /** 轻量探活，用于判断是否离线 */
    suspend fun ping(): Boolean = try {
        client.get(url("api", "data-version")).status.isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** 把站点上的相对图片路径转换为可直接加载的绝对地址 */
    fun absoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return buildString {
            append(HOST)
            path.split('/').filter { it.isNotEmpty() }.forEach {
                append('/').append(encodeSegment(it))
            }
        }
    }

    /** 作品封面，缺失时返回 [Uri.EMPTY] 让宿主显示默认封面 */
    fun coverUri(work: Work): Uri {
        val image = work.cover.image
        if (image.isBlank()) return Uri.EMPTY
        return Uri.parse(absoluteUrl(image))
    }
}
