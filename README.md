# LightNovelReaderPlugin-OPLiterature

为 [LightNovelReader（轻小说阅读器）](https://github.com/dmzz-yyhyy/LightNovelReader) 编写的网络数据源插件，
从 [www.opliterature.com](https://www.opliterature.com/)（OP文学社阅读器）获取作品信息、目录与正文。

工程结构参照 [LightNovelReaderPlugin-Template](https://github.com/dmzz-yyhyy/LightNovelReaderPlugin-Template)。

## 信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | OP文学社 |
| 插件 id | `org.opliterature.lnr` |
| 数据源 id | `0x4F504C33` |
| Api 版本 | 3（对应 LightNovelReader 1.2.x） |
| 当前版本 | 1.2.4 (versionCode 10) |
| 作者 | [MCAbleBlank](https://github.com/MCAbleBlank) |
| 最低系统 | Android 7.0 (API 24) |

## 版本对应（重要）

宿主 **1.2.4** 的插件 API 版本是 **3**（`ApiMetadata.API_VERSION = 3`，分组 `{2,3}`）。
模板与 maven 上的 `api:0.4-SNAPSHOT` 属于 **Api 4** 分组，直接用会被判定「插件基于旧版本构建」。
因此本插件依赖 **`io.nightfish.lightnovelreader:api:0.3`**，`@Plugin(apiVersion = 3)`。

Api 3 与 Api 4 的接口差异（本插件已按 3 适配）：

| 项目 | Api 3（1.2.x） | Api 4（dev / 0.4-SNAPSHOT） |
| --- | --- | --- |
| 数据源 id | `val id: Int` | `val id: Identifier` |
| 取书本详情 | 返回 `BookInformation` | 返回 `Result<BookInformation, WebRequestError>` |
| 取目录 | 返回 `BookVolumes` | 返回 `Result<BookVolumes, ...>` |
| 取正文 | 返回 `ChapterContent` | 返回 `Result<ChapterContent, ...>` |
| 章节前后 | `lastChapter` / `nextChapter`（空串表示无） | `prevChapter` / `nextChapter`（null 表示无） |
| 段落组件 | `simpleText(text)` | `paragraph { text(...) }` |
| 多结果搜索 | `MultipleBook(bookInformation)` | `MultipleBook(bookId)` |
| 构造对象 | `MutableBookInformation` / `MutableChapterContent` | data class 直接构造 |

## 功能

- 探索页「书架」：最近更新、连载中、已完结、评分最高
- 书籍详情：标题、副标题（一句话简介）、作者、简介、标签、字数、更新时间、完结状态、封面
- 目录：按站点章节顺序生成（站点无分卷，全部章节归入「正文」一卷）
- 正文：整章生成一个 `simpleText` 组件，段间空一行，每段首行缩进两个字符
- 搜索：按标题 / 作者 / 标签，在整站目录上本地过滤
- 离线探测：定时访问 `/api/data-version` 判断在线状态

## 站点接口

站点是纯前端阅读器，数据走同源 JSON 接口：

| 接口 | 说明 |
| --- | --- |
| `GET /api/data` | 整站目录，包含全部作品的章节元信息（不含正文） |
| `GET /api/content/<作品id>` | 某部作品的正文，外层数组下标即章节序号，内层为段落数组 |
| `GET /api/data-version` | 数据版本号，用于探活 |

作品 id 可能含中文（如 `imp-夏之妖-改`），所有路径片段都做了百分号编码。

## id 规则

| 对象 | id |
| --- | --- |
| 数据源 | `0x4F504C33`（"OPL3"） |
| 作品（书本） | 站点作品 id，如 `shao-nv-qi-shi-bing` |
| 章节 | `<作品id>#<章节序号>`，如 `shao-nv-qi-shi-bing#3` |

## 目录结构

```
plugin/src/main/kotlin/org/opliterature/lnr/
├── OpLiteraturePlugin.kt              # 插件入口（@Plugin, apiVersion = 3）
├── PluginDiscoveryReceiver.kt         # 供宿主发现插件
├── data/
│   ├── Models.kt                      # 站点 JSON 数据模型
│   └── OpLiteratureApi.kt             # 接口封装 + 缓存
└── source/
    ├── WorkMapping.kt                 # 站点数据 → BookInformation
    ├── OpLiteratureWebDataSource.kt   # @WebDataSource 数据源主体
    ├── OpLiteratureSearchProvider.kt  # 搜索
    └── explore/
        ├── OpLiteratureExplorePageProvider.kt
        └── OpLiteratureShelfPageDataSource.kt
```

## 构建

需要 JDK 17+ 与 Android SDK（`compileSdk 36`、`build-tools 36.0.0`）。

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :plugin:assembleDebug
```

产物位于 `plugin/build/outputs/apk/debug/`，后缀被改名为 `.apk.lnrp`。
CI 配置见 `.github/workflows/build.yml`。

`lightnovelreader:api` 以 `compileOnly` 引入，运行时由宿主提供，不会打进 APK。

## 安装

1. 把 `*.apk.lnrp` 传到手机；
2. 在轻小说阅读器中通过「插件管理 → 从文件安装」选择该文件；
3. 重启应用后，在数据源列表中选择「OP文学社」。

也可以直接用 adb：

```bash
adb install -r OPLiterature-1.2.4.apk.lnrp
```

## 发布到插件仓库

[LightNovelReader-PluginRepository](https://github.com/dmzz-yyhyy/LightNovelReader-PluginRepository)
以 `data/<插件id>/` 为单位收录插件，需要 `metadata.json` 与分卷压缩的 `plugin.zip.00N`：

```json
{
  "id": "org.opliterature.lnr",
  "version": 10,
  "name": "OP文学社",
  "version_name": "1.2.4",
  "author": "MCAbleBlank",
  "description": "从 opliterature.com 获取作品目录与正文",
  "compressed_file_number": 1
}
```

同时要在 `data/plugins.json` 的 `plugins` 数组里追加 `{"id": "...", "name": "..."}`。

## 段间换行与缩进

站点正文是按「段落数组」返回的。早期实现每段生成一个 `simpleText` 组件，
组件在纵向上紧贴，段与段之间没有空行；翻页模式还会按组件切页，一段就占一页。

现在整章只生成一个文本组件，段与段之间空一行，每段开头加两个全角空格缩进：

```kotlin
val text = paragraphs.asSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(PARAGRAPH_SEPARATOR) { PARAGRAPH_INDENT + it }

private const val PARAGRAPH_SEPARATOR = "\n\u3000\n" // 段间空一行(含全角空格)
private const val PARAGRAPH_INDENT = "\u3000\u3000"   // 首行缩进两个字符
```

注意分隔符是「换行 + 全角空格 + 换行」而不是两个连续换行：
纯空行可能被排版实现压掉，而含全角空格的行有实际宽度，一定占一行行高。

## 说明

本站作品均为站内原创，请尊重作者，勿将抓取内容用于二次分发。
