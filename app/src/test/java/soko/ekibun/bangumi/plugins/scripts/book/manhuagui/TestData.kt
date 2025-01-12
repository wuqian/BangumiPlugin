package soko.ekibun.bangumi.plugins.scripts.book.manhuagui

import soko.ekibun.bangumi.plugins.BookScriptTest
import soko.ekibun.bangumi.plugins.model.line.LineInfo
import soko.ekibun.bangumi.plugins.model.provider.ProviderInfo
import soko.ekibun.bangumi.plugins.provider.Provider

class TestData : BookScriptTest.BookTestData() {
    override val info = ProviderInfo(
        site = "manhuagui",
        color = 0x0791ff,
        title = "漫画柜",
        type = Provider.TYPE_BOOK
    )
    override val searchKey = "刺客"
    override val lineInfo = LineInfo(
        "manhuagui",
        id = "/comic/24086/",
        title = "刺客守则",
        extra = ""
    )
    override val episode = Provider.ProviderEpisode(
        site = "manhuagui",
        id = "304069",
        sort = 1f,
        title = "第01回",
        url = "https://m.manhuagui.com/comic/24086/304069.html"
    )
}