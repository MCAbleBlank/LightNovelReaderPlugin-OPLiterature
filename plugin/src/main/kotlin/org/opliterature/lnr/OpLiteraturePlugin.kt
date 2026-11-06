package org.opliterature.lnr

import android.util.Log
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin

/**
 * OP 文学社（https://www.opliterature.com）轻小说阅读器数据源插件
 *
 * 实际的数据获取由 [org.opliterature.lnr.source.OpLiteratureWebDataSource] 完成，
 * 宿主会扫描带 [io.nightfish.lightnovelreader.api.web.WebDataSource] 注解的类并自动注册。
 *
 * apiVersion 必须与宿主支持版本同组，LightNovelReader 1.3.x 为 Api 4。
 */
@Suppress("unused")
@Plugin(
    version = BuildConfig.VERSION_CODE,
    name = "OP文学社",
    versionName = BuildConfig.VERSION_NAME,
    author = "MCAbleBlank",
    description = "从 opliterature.com 获取作品目录与正文",
    updateUrl = "",
    apiVersion = 4
)
class OpLiteraturePlugin : LightNovelReaderPlugin {

    companion object {
        private const val TAG = "OpLiteraturePlugin"
    }

    override fun onLoad() {
        Log.i(
            TAG,
            "OP文学社数据源插件已加载: versionCode=${BuildConfig.VERSION_CODE} " +
                    "versionName=${BuildConfig.VERSION_NAME} apiVersion=4"
        )
    }
}
