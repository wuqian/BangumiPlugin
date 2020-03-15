package soko.ekibun.bangumi.plugins.scripts.webpage

import soko.ekibun.bangumi.plugins.VideoScriptTest
import soko.ekibun.bangumi.plugins.model.LineProvider
import soko.ekibun.bangumi.plugins.provider.Provider

class TestData : VideoScriptTest.VideoTestData() {
    /**
     * 线路配置
     */
    override val info = LineProvider.ProviderInfo(
        site = "webpage",
        color = 0,
        title = "网页",
        type = Provider.TYPE_VIDEO
    )
}