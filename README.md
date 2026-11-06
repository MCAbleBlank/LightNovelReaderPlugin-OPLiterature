# LightNovelReaderPlugin-OPLiterature

为 [LightNovelReader（轻小说阅读器）](https://github.com/dmzz-yyhyy/LightNovelReader) 编写的网络数据源插件，
从 [www.opliterature.com](https://www.opliterature.com/)（OP文学社阅读器）获取作品信息、目录与正文。

## 信息

| 项目 | 值 |
| --- | --- |
| 插件名称 | OP文学社 |
| 插件 id | `org.opliterature.lnr` |
| 数据源 id | `0x4F504C33` |
| Api 版本 | 3（对应 LightNovelReader 1.2.x） |
| 当前版本 | 1.2.4 (versionCode 10) |
| 作者 | [MCAbleBlank](https://github.com/MCAbleBlank) |

## 功能

- 探索页「书架」：最近更新、连载中、已完结、评分最高
- 书籍详情：标题、副标题（一句话简介）、作者、简介、标签、字数、更新时间、完结状态、封面
- 目录：按站点章节顺序生成（站点无分卷，全部章节归入「正文」一卷）
- 正文：整章生成一个 `simpleText` 组件，段间空一行，每段首行缩进两个字符
- 搜索：按标题 / 作者 / 标签，在整站目录上本地过滤
- 离线探测：定时访问 `/api/data-version` 判断在线状态

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

## 说明

本站作品均为站内原创，请尊重作者，勿将抓取内容用于二次分发。
