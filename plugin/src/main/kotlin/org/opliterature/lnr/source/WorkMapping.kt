package org.opliterature.lnr.source

import android.net.Uri
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.WordCount
import org.opliterature.lnr.data.Work
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * 把站点作品转换成宿主使用的 [BookInformation]
 */
fun Work.toBookInformation(coverUri: Uri): BookInformation = BookInformation(
    id = id,
    title = title,
    subtitle = slogan.ifBlank { meta },
    coverUri = coverUri,
    author = displayAuthor,
    description = desc,
    tags = tagList,
    publishingHouse = "OP文学社",
    wordCount = WordCount(totalCharCount),
    lastUpdated = runCatching { LocalDateTime.parse(lastModified, DATE_FORMAT) }
        .getOrElse { LocalDateTime.now() },
    isComplete = isCompleted
)
