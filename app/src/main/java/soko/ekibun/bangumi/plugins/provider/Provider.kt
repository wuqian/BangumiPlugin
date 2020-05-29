package soko.ekibun.bangumi.plugins.provider

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import soko.ekibun.bangumi.plugins.bean.Episode
import soko.ekibun.bangumi.plugins.bean.Subject
import soko.ekibun.bangumi.plugins.engine.JsEngine
import soko.ekibun.bangumi.plugins.model.line.LineInfo
import soko.ekibun.bangumi.plugins.provider.book.BookProvider
import soko.ekibun.bangumi.plugins.provider.video.VideoProvider
import soko.ekibun.bangumi.plugins.subject.LinePresenter
import soko.ekibun.bangumi.plugins.util.JsonUtil

abstract class Provider(
    @Code("全局", -2) val header: String? = null,
    @Code("打开", -1) val open: String? = null,
    @Code("搜索", 0) val search: String? = null
) {
    suspend fun open(scriptKey: String, line: LineInfo): String {
        return JsEngine.makeScript(
            "var line = ${JsonUtil.toJson(line)};\n$open",
            header,
            scriptKey
        ) { JsonUtil.toEntity<String>(it) ?: "" }
    }

    suspend fun search(scriptKey: String, key: String): List<LineInfo> {
        return JsEngine.makeScript("var key = ${JsonUtil.toJson(key)};\n$search", header, scriptKey) {
            JsonUtil.toEntity<List<LineInfo>>(it) ?: ArrayList()
        }
    }

    abstract fun createPluginView(linePresenter: LinePresenter): PluginView

    abstract class PluginView(linePresenter: LinePresenter, @LayoutRes resId: Int) {
        val view = LayoutInflater.from(linePresenter.pluginContext).inflate(resId, null)

        init {
            linePresenter.proxy.item_plugin.let {
                it.removeAllViews()
                it.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }

        abstract fun loadEp(episode: Episode)

        abstract fun downloadEp(episode: Episode, updateInfo: (String) -> Unit)
    }

    @Target(AnnotationTarget.FIELD)
    annotation class Code(val label: String, val index: Int)

    companion object {
        const val TYPE_VIDEO = "video"
        const val TYPE_BOOK = "book"

        val providers: Map<String, Class<out Provider>> = mapOf(
            TYPE_VIDEO to VideoProvider::class.java,
            TYPE_BOOK to BookProvider::class.java
        )

        fun getProviderType(subject: Subject): String {
            return when (subject.type) {
                Subject.TYPE_ANIME, Subject.TYPE_REAL -> TYPE_VIDEO
                Subject.TYPE_BOOK -> TYPE_BOOK
                else -> ""
            }
        }

        fun getProviderFileType(type: String): String {
            return when (type) {
                TYPE_VIDEO -> "video/*"
                TYPE_BOOK -> "image/*"
                else -> ""
            }
        }
    }

    data class HttpRequest(
        val url: String,
        val header: HashMap<String, String>? = null,
        val overrideExtension: String? = null
    )
}