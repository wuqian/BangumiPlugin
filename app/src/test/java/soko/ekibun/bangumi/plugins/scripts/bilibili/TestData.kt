package soko.ekibun.bangumi.plugins.scripts.bilibili

import soko.ekibun.bangumi.plugins.VideoScriptTest
import soko.ekibun.bangumi.plugins.model.LineInfoModel
import soko.ekibun.bangumi.plugins.model.LineProvider
import soko.ekibun.bangumi.plugins.provider.Provider
import soko.ekibun.bangumi.plugins.provider.video.VideoProvider

class TestData : VideoScriptTest.VideoTestData() {
    /**
     * 线路配置
     */
    override val info = LineProvider.ProviderInfo(
        site = "bilibili",
        color = 0xf25d8e,
        title = "哔哩哔哩",
        type = Provider.TYPE_VIDEO
    )
    override val searchKey = "日常"
    override val lineInfo = LineInfoModel.LineInfo(
        "bilibili",
        id = "844",
        title = "日常"
    )
    override val video = VideoProvider.VideoInfo(
        site = "bilibili",
        id = "97495910",
        url = "https://www.bilibili.com/bangumi/play/ep15189"
    )
}