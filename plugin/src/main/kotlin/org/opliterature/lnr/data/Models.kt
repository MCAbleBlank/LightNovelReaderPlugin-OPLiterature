package org.opliterature.lnr.data

import kotlinx.serialization.Serializable

/**
 * https://www.opliterature.com/api/data 的响应体
 */
@Serializable
data class SiteData(
    val siteName: String = "",
    val works: List<Work> = emptyList()
)

/**
 * 一部作品。站点把目录与正文分开存放，这里的 [chapters] 只包含章节的元信息
 */
@Serializable
data class Work(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    /** 实际上传者，[author] 为空时作为兜底 */
    val creator: String = "",
    val desc: String = "",
    /** 形如 "原创 · 百合 · 校园" 的标签串 */
    val meta: String = "",
    /** 站点上的一句话简介 */
    val slogan: String = "",
    /** ongoing / finished */
    val status: String = "",
    /** 形如 "2026-09-27 10:43:52" */
    val lastModified: String = "",
    val cover: Cover = Cover(),
    val chapters: List<ChapterMeta> = emptyList(),
    val ratingCount: Int = 0,
    val ratingSum: Int = 0,
    val ratingEpoch: Int = -1
) {
    /** 章节按站点给出的 index 升序排列 */
    val sortedChapters: List<ChapterMeta>
        get() = chapters.sortedBy { it.index }

    val isCompleted: Boolean
        get() = status.equals("finished", ignoreCase = true) ||
                status.equals("completed", ignoreCase = true) ||
                status.contains("完结")

    /** 全文字数，取各章节 charCount 之和 */
    val totalCharCount: Int
        get() = chapters.sumOf { it.charCount }

    /** 标签列表，去掉站点通用的「原创」标记 */
    val tagList: List<String>
        get() = meta.split("·", "・", "|", "/", ",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "原创" }

    val displayAuthor: String
        get() = author.ifBlank { creator }

    val ratingAverage: Double
        get() = if (ratingCount > 0) ratingSum.toDouble() / ratingCount else 0.0

    /** 章节在站点内的 id 规则：`<作品id>#<章节序号>` */
    fun chapterId(index: Int): String = "$id#$index"
}

@Serializable
data class Cover(
    val image: String = "",
    val kind: String = "",
    val label: String = ""
)

@Serializable
data class ChapterMeta(
    val index: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val charCount: Int = 0,
    val paraCount: Int = 0
) {
    /** 阅读器里展示的章节名，有副标题时一并带上 */
    val fullTitle: String
        get() = if (subtitle.isBlank()) title else "$title · $subtitle"
}
