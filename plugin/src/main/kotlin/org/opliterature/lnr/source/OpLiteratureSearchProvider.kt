package org.opliterature.lnr.source

import io.nightfish.lightnovelreader.api.util.local
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import io.nightfish.lightnovelreader.api.web.search.SearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.opliterature.lnr.data.OpLiteratureApi

/**
 * 站点没有搜索接口，但整站作品数量有限（几十部），
 * 所以直接在本地目录上做过滤，响应快且不额外发请求。
 */
class OpLiteratureSearchProvider(
    private val api: OpLiteratureApi
) : SearchProvider {

    override val searchTypes: List<SearchType> = listOf(
        SearchType(
            type = "title",
            name = "按标题搜索".local(),
            tip = "输入作品标题".local()
        ),
        SearchType(
            type = "author",
            name = "按作者搜索".local(),
            tip = "输入作者笔名".local()
        ),
        SearchType(
            type = "tag",
            name = "按标签搜索".local(),
            tip = "输入标签, 如 百合".local()
        )
    )

    override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
        val query = keyword.trim()
        if (query.isEmpty()) {
            emit(SearchResult.Empty())
            return@flow
        }

        val works = try {
            api.catalog().works
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(SearchResult.Error(e))
            return@flow
        }

        val lowered = query.lowercase()
        val matched = works.filter { work ->
            when (searchType.type) {
                "author" -> work.displayAuthor.lowercase().contains(lowered) ||
                        work.creator.lowercase().contains(lowered)

                "tag" -> work.tagList.any { it.lowercase().contains(lowered) } ||
                        work.meta.lowercase().contains(lowered)

                else -> work.title.lowercase().contains(lowered) ||
                        work.slogan.lowercase().contains(lowered)
            }
        }

        when {
            matched.isEmpty() -> emit(SearchResult.Empty())

            // 只有一本书时宿主会直接跳转到详情页
            matched.size == 1 -> emit(SearchResult.SingleBook(matched.first().id))

            else -> {
                matched.forEach { work ->
                    emit(SearchResult.MultipleBook(work.id))
                }
                emit(SearchResult.End())
            }
        }
    }
}
