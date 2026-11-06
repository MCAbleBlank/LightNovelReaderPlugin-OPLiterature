package org.opliterature.lnr.source

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes
import io.nightfish.lightnovelreader.api.book.ChapterContent
import io.nightfish.lightnovelreader.api.book.ChapterInformation
import io.nightfish.lightnovelreader.api.book.Volume
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.content.builder.paragraph
import io.nightfish.lightnovelreader.api.error.WebRequestError
import io.nightfish.lightnovelreader.api.identifier.Identifier
import io.nightfish.lightnovelreader.api.util.Cache
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.explore.ExplorePageProvider
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opliterature.lnr.data.OpLiteratureApi
import org.opliterature.lnr.source.explore.OpLiteratureExplorePageProvider
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import kotlin.time.Duration.Companion.seconds

/**
 * OP 文学社（https://www.opliterature.com）数据源
 *
 * 对应 LightNovelReader 的 Api 3（1.2.x）。
 */
@Suppress("unused")
@WebDataSource(
    name = "OP文学社",
    provider = "www.opliterature.com"
)
class OpLiteratureWebDataSource : WebBookDataSource {

    companion object {
        const val TAG = "OpLiterature"
        const val HOST = OpLiteratureApi.HOST

        /** 数据源唯一标识，取 "OPL3" 的 ASCII 值 */
        const val DATA_SOURCE_ID = 0x4F504C33

        /**
         * 段与段之间的分隔。
         *
         * 用 "换行 + 只含一个全角空格的行 + 换行", 而不是两个连续换行:
         * 纯空行在某些排版实现里会被压掉, 而这一行里含有全角空格(\u3000),
         * 它是有宽度的"实体空行", 一定会占掉一行行高, 视觉上就是段间空一行。
         */
        private const val PARAGRAPH_SEPARATOR = "\n\u3000\n"

        /** 每段开头的缩进: 两个全角空格 */
        private const val PARAGRAPH_INDENT = "\u3000\u3000"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val offlineFlow = MutableStateFlow(false)

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(UserAgent) { agent = USER_AGENT }
            install(HttpCookies)
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
                retryOnExceptionIf { _, cause ->
                    cause is EOFException || cause is ConnectException || cause is IOException
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.INFO
            }
        }
    }

    private val api: OpLiteratureApi by lazy { OpLiteratureApi(client) }

    override val id: Identifier = Identifier("org.opliterature.lnr", "op-literature")

    override val permits = 4

    override val cache = Cache(
        maxCountEachType = 24,
        timeout = 3_600_000
    )

    override val imageHeader: Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to "$HOST/"
    )

    override val offLine: Boolean
        get() = offlineFlow.value

    override val isOffLineFlow: StateFlow<Boolean> = offlineFlow

    override suspend fun isOffLine(): Boolean = !api.ping()

    override fun onLoad() {
        scope.launch {
            while (isActive) {
                val offline = !api.ping()
                if (offline != offlineFlow.value) {
                    Log.i(TAG, if (offline) "连接失败, 标记为离线" else "已恢复在线")
                }
                offlineFlow.value = offline
                // 离线时更快重试, 在线时降低探测频率
                delay(if (offline) 5.seconds else 60.seconds)
            }
        }
    }

    override suspend fun getBookInformation(id: String): Result<BookInformation, WebRequestError> {
        val work = try {
            api.work(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "获取作品详情失败 id=$id", e)
            return Err(
                WebRequestError(
                    title = "获取作品详情失败",
                    message = "无法从 OP文学社 获取作品 $id：${e.message}",
                    throwable = e
                )
            )
        } ?: return Err(WebRequestError("获取作品详情失败", "OP文学社 未找到作品 $id"))

        return Ok(work.toBookInformation(api.coverUri(work)))
    }

    override suspend fun getBookVolumes(id: String): Result<BookVolumes, WebRequestError> {
        val work = try {
            api.work(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "获取目录失败 id=$id", e)
            return Err(
                WebRequestError(
                    title = "获取目录失败",
                    message = "无法从 OP文学社 获取作品 $id 的目录：${e.message}",
                    throwable = e
                )
            )
        } ?: return Err(WebRequestError("获取目录失败", "OP文学社 未找到作品 $id"))

        return Ok(
            BookVolumes(
                bookId = id,
                // 站点没有分卷, 全部章节放进同一卷
                volumes = listOf(
                    Volume(
                        volumeId = "$id#default",
                        volumeTitle = "正文",
                        chapters = work.sortedChapters.map {
                            ChapterInformation(
                                id = work.chapterId(it.index),
                                title = it.fullTitle
                            )
                        }
                    )
                )
            )
        )
    }

    override suspend fun getChapterContent(
        chapterId: String,
        bookId: String
    ): Result<ChapterContent, WebRequestError> {
        val index = chapterId.substringAfterLast('#').toIntOrNull()
            ?: return Err(WebRequestError("章节不存在", "无法解析章节 id: $chapterId"))

        val work = try {
            api.work(bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "获取作品失败 bookId=$bookId", e)
            return Err(
                WebRequestError(
                    title = "获取作品失败",
                    message = "无法从 OP文学社 获取作品 $bookId：${e.message}",
                    throwable = e
                )
            )
        } ?: return Err(WebRequestError("获取作品失败", "OP文学社 未找到作品 $bookId"))

        val chapters = work.sortedChapters
        val position = chapters.indexOfFirst { it.index == index }
        if (position < 0) {
            Log.e(TAG, "作品 $bookId 中不存在第 $index 章")
            return Err(WebRequestError("章节不存在", "作品 $bookId 中不存在第 $index 章"))
        }

        val paragraphs = try {
            // 站点一次返回整部作品的正文, 外层下标即章节序号
            api.content(bookId).getOrNull(position).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "获取正文失败 bookId=$bookId", e)
            return Err(
                WebRequestError(
                    title = "获取正文失败",
                    message = "无法从 OP文学社 获取作品 $bookId 的正文：${e.message}",
                    throwable = e
                )
            )
        }

        // 相邻两段之间插入空行, 并在每段开头加缩进:
        // 整章只生成一个段落组件, 段与段之间必然换行。
        val text = paragraphs.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(PARAGRAPH_SEPARATOR) { PARAGRAPH_INDENT + it }

        val content = ContentBuilder().apply {
            if (text.isNotEmpty()) paragraph { text(text) }
        }.build()

        // 诊断用: 确认插件侧生成的文本确实带空行与缩进
        Log.i(
            TAG,
            "章节 $chapterId: 段落 ${paragraphs.size} 段, 文本 ${text.length} 字, " +
                    "含空行=${text.contains(PARAGRAPH_SEPARATOR)}, " +
                    "首行缩进=${text.startsWith(PARAGRAPH_INDENT)}"
        )

        return Ok(
            ChapterContent(
                id = chapterId,
                title = chapters[position].fullTitle,
                content = content,
                prevChapter = chapters.getOrNull(position - 1)?.let { work.chapterId(it.index) },
                nextChapter = chapters.getOrNull(position + 1)?.let { work.chapterId(it.index) }
            )
        )
    }

    override val explorePageProvider: ExplorePageProvider by lazy {
        OpLiteratureExplorePageProvider(api)
    }

    override val searchProvider: SearchProvider by lazy {
        OpLiteratureSearchProvider(api)
    }
}
