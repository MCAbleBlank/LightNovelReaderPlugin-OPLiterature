package org.opliterature.lnr.source.explore

import android.net.Uri
import io.nightfish.lightnovelreader.api.explore.ExploreBooksRow
import io.nightfish.lightnovelreader.api.explore.ExploreDisplayBook
import io.nightfish.lightnovelreader.api.web.explore.ExploreTapPageDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.opliterature.lnr.data.OpLiteratureApi
import org.opliterature.lnr.data.Work

/**
 * 探索页的「书架」卡片页：把站点全部作品按最近更新 / 连载 / 完结 / 评分分组展示
 */
class OpLiteratureShelfPageDataSource(
    private val api: OpLiteratureApi
) : ExploreTapPageDataSource {

    override val title = "书架"

    override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = flow {
        val works = try {
            api.catalog().works
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(emptyList())
            return@flow
        }
        if (works.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val rows = mutableListOf<ExploreBooksRow>()

        rows += row("最近更新", works.sortedByDescending { it.lastModified }.take(12))
        emit(rows.toList())

        rows += row("连载中", works.filter { !it.isCompleted })
        emit(rows.toList())

        rows += row("已完结", works.filter { it.isCompleted })
        emit(rows.toList())

        val rated = works.filter { it.ratingCount > 0 }
            .sortedByDescending { it.ratingAverage }
            .take(12)
        if (rated.isNotEmpty()) {
            rows += row("评分最高", rated)
            emit(rows.toList())
        }
    }

    private fun row(title: String, works: List<Work>): ExploreBooksRow = ExploreBooksRow(
        title = title,
        bookList = works.map { work ->
            ExploreDisplayBook(
                id = work.id,
                title = work.title,
                author = work.displayAuthor,
                coverUri = api.coverUri(work).takeIf { it != Uri.EMPTY } ?: Uri.EMPTY
            )
        },
        expandable = false
    )
}
