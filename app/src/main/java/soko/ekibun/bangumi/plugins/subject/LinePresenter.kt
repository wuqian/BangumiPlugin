package soko.ekibun.bangumi.plugins.subject

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.subject_episode.view.*
import soko.ekibun.bangumi.plugins.App
import soko.ekibun.bangumi.plugins.PluginPresenter
import soko.ekibun.bangumi.plugins.R
import soko.ekibun.bangumi.plugins.bean.Episode
import soko.ekibun.bangumi.plugins.bean.EpisodeCache
import soko.ekibun.bangumi.plugins.model.LineInfoModel
import soko.ekibun.bangumi.plugins.model.LineProvider
import soko.ekibun.bangumi.plugins.model.line.LineInfo
import soko.ekibun.bangumi.plugins.model.provider.ProviderInfo
import soko.ekibun.bangumi.plugins.provider.Provider
import soko.ekibun.bangumi.plugins.provider.book.BookProvider
import soko.ekibun.bangumi.plugins.service.DownloadService
import soko.ekibun.bangumi.plugins.ui.provider.ProviderActivity
import soko.ekibun.bangumi.plugins.util.AppUtil
import soko.ekibun.bangumi.plugins.util.JsonUtil
import soko.ekibun.bangumi.plugins.util.ReflectUtil
import soko.ekibun.bangumi.plugins.util.ResourceUtil
import java.lang.ref.WeakReference

class LinePresenter(val activityRef: WeakReference<Activity>) : PluginPresenter(activityRef) {
    val proxy = ReflectUtil.proxyObjectWeak(activityRef, ISubjectActivity::class.java)!!
    val pluginContext = App.createThemeContext(activityRef)

    val episodeDetailAdapter = EpisodeAdapter(this)
    val episodeAdapter = SmallEpisodeAdapter(this)
    val emptyView = {
        val view = TextView(pluginContext)
        view.gravity = Gravity.CENTER
        view.height = ResourceUtil.dip2px(pluginContext, 65f)
        view
    }()

    var type = ""
    val pluginView: Provider.PluginView by lazy {
        Provider.providers[type]!!.newInstance().createPluginView(this)
    }

    val epLayout = ReflectUtil.findViewById<LinearLayout>(proxy.subjectPresenter.subjectView.detail, "item_episodes")

    val epView = LayoutInflater.from(pluginContext).inflate(
        R.layout.subject_episode, epLayout, false
    )
    val subjectView = SubjectView(this, epView)

    // 读一次，大部分只用到id
    val subject = proxy.subjectPresenter.subject

    var onDestroyListener = {}

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val episode = JsonUtil.toEntity<Episode>(intent.getStringExtra(DownloadService.EXTRA_EPISODE) ?: "")!!
                val downloading = intent.getBooleanExtra(DownloadService.EXTRA_DOWNLOADING, false)
                val cache = if (intent.hasExtra(DownloadService.EXTRA_CACHE))
                    JsonUtil.toEntity<EpisodeCache>(
                        intent.getStringExtra(DownloadService.EXTRA_CACHE) ?: "{}"
                    )!! else null

                arrayOf(subjectView.episodeDetailAdapter, episodeDetailAdapter).forEach { adapter ->
                    try {
                        val index = adapter.data.indexOfFirst { Episode.compareEpisode(it.t, episode) }
                        adapter.getViewByPosition(index, R.id.item_layout)?.let {
                            adapter.updateDownload(
                                it,
                                cache?.cache(),
                                downloading
                            )
                        }
                    } catch (e: Exception) {
                    }
                }

                arrayOf(subjectView.episodeAdapter, episodeAdapter).forEach { adapter ->
                    try {
                        val index = adapter.data.indexOfFirst { Episode.compareEpisode(it, episode) }
                        adapter.getViewByPosition(index, R.id.item_layout)?.let {
                            adapter.updateDownload(
                                it,
                                cache?.cache(),
                                downloading
                            )
                        }
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        activityRef.get()?.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadService.getBroadcastAction(subject))
        )
        proxy.onDestroyListener = {
            activityRef.get()?.unregisterReceiver(downloadReceiver)
            onDestroyListener()
        }

        episodeAdapter.setEmptyView(emptyView)
//        epView.episode_list.adapter = episodeAdapter
        // 添加自己的view
        epView.episode_list.adapter = subjectView.episodeAdapter
        epView.episode_list.layoutManager = LinearLayoutManager(pluginContext, LinearLayoutManager.HORIZONTAL, false)
        epLayout.addView(
            epView, 2,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        subjectView.updateEpisode(subject)
        epView.btn_detail.setOnClickListener {
            EpisodeListDialog.showDialog(this)
        }
        proxy.subjectPresenter.subjectRefreshListener = { _ ->
            val newSubject = proxy.subjectPresenter.subject
            subject.type = newSubject.type
            subject.eps = if (newSubject.eps?.size ?: 0 > 0) newSubject.eps else subject.eps
            subject.eps_count = newSubject.eps_count
            subject.ep_status = newSubject.ep_status
            subject.vol_count = newSubject.vol_count
            subject.vol_status = newSubject.vol_status
            subjectView.updateEpisode(subject)
            refreshLines()
        }
        proxy.onActivityResultListener = onActivityResultListener@{ requestCode: Int, resultCode: Int, data: Intent? ->
            if (requestCode == AppUtil.REQUEST_PROVIDER && resultCode == AppCompatActivity.RESULT_OK) {//Provider
                loadProviderCallback?.invoke(
                    JsonUtil.toEntity<ProviderInfo>(
                        data?.getStringExtra(
                            ProviderActivity.EXTRA_PROVIDER_INFO
                        ) ?: ""
                    )
                )
            }
        }
    }

    private var loadProviderCallback: ((ProviderInfo?) -> Unit)? = null
    fun loadProvider(type: String, info: ProviderInfo?, callback: (ProviderInfo?) -> Unit) {
        if (type.isEmpty()) return
        loadProviderCallback = callback
        val intent = Intent(pluginContext, ProviderActivity::class.java)
        intent.putExtra(ProviderActivity.EXTRA_PROVIDER_TYPE, type)
        info?.let { intent.putExtra(ProviderActivity.EXTRA_PROVIDER_INFO, JsonUtil.toJson(info)) }
        activityRef.get()?.startActivityForResult(intent, AppUtil.REQUEST_PROVIDER)
    }

    private fun updateProgress() {
        val cats = episodeAdapter.data.mapNotNull { it.category }.distinct()
        episodeAdapter.data.forEach { episode ->
            episode.progress = if (cats.indexOf(episode.category) + 1 < subject.vol_status ||
                (cats.indexOf(episode.category) + 1 == subject.vol_status && episode.sort <= subject.ep_status)
            )
                Episode.PROGRESS_WATCH else Episode.PROGRESS_REMOVE
        }
        episodeAdapter.notifyDataSetChanged()
    }

    private fun showEpDialog(ep: Episode, v: View) {
        if (ep.book == null) {
            proxy.subjectPresenter.showEpisodeDialog(ep.id)
            return
        }
        val popupMenu = PopupMenu(pluginContext, v)
        popupMenu.menu.add("看到")
        popupMenu.menu.add("打开")
        popupMenu.setOnMenuItemClickListener { menu ->
            val cats = episodeAdapter.data.mapNotNull { it.category }.distinct()
            when (menu.title) {
                "看到" -> proxy.subjectPresenter.updateSubjectProgress(cats.indexOf(ep.category) + 1, ep.sort.toInt())
                "打开" -> activityRef.get()?.let { ctx -> AppUtil.openBrowser(ctx, ep.book?.url ?: "") }
            }

            false
        }
        popupMenu.show()
    }

    var selectCache = false
    var epInfo: ProviderInfo? = null
    fun refreshLines() {
        val infos = LineInfoModel.getInfo(subject)

        episodeAdapter.setOnItemChildClickListener { _, _, position ->
            pluginView.loadEp(episodeAdapter.data[position])
        }
        episodeAdapter.setOnItemChildLongClickListener { _, v, position ->
            showEpDialog(episodeAdapter.data[position], v)
            true
        }
        subjectView.episodeAdapter.setOnItemChildClickListener { _, _, position ->
            infos.getDefaultProvider()?.let {
                pluginView.loadEp(subjectView.episodeAdapter.data[position])
            } ?: Toast.makeText(pluginContext, "请先添加播放源", Toast.LENGTH_SHORT).show()
        }
        subjectView.episodeAdapter.setOnItemChildLongClickListener { _, _, position ->
            proxy.subjectPresenter.showEpisodeDialog(subjectView.episodeAdapter.data[position].id)
            true
        }
        subjectView.episodeDetailAdapter.setOnItemClickListener { _, _, position ->
            infos.getDefaultProvider()?.let {
                subjectView.episodeDetailAdapter.data[position].t?.let { pluginView.loadEp(it) }
            } ?: Toast.makeText(pluginContext, "请先添加播放源", Toast.LENGTH_SHORT).show()
        }
        subjectView.episodeDetailAdapter.setOnItemLongClickListener { _, v, position ->
            subjectView.episodeDetailAdapter.data[position].t?.let {
                showEpDialog(it, v)
            }
            true
        }
        episodeDetailAdapter.setOnItemClickListener { _, _, position ->
            infos.getDefaultProvider()?.let {
                episodeDetailAdapter.data[position].t?.let { pluginView.loadEp(it) }
            } ?: Toast.makeText(pluginContext, "请先添加播放源", Toast.LENGTH_SHORT).show()
        }
        episodeDetailAdapter.setOnItemLongClickListener { _, _, position ->
            episodeDetailAdapter.data[position].t?.let {
                proxy.subjectPresenter.showEpisodeDialog(it.id)
            }
            true
        }

        type = Provider.getProviderType(subject)

        val setProvider = setProvider@{ info: LineInfo?, newInfo: LineInfo? ->
            Log.v("info", "$info, $newInfo")
            val position =
                infos.providers.indexOfFirst { it.id == info?.id && it.site == info.site }
            when {
                newInfo == null -> if (position >= 0) {
                    infos.providers.removeAt(position)
                    infos.subject.defaultLine -= if (infos.subject.defaultLine > position) 1 else 0
                    infos.subject.defaultLine =
                        Math.max(0, Math.min(infos.providers.size - 1, infos.subject.defaultLine))
                }
                position >= 0 -> infos.providers[position] = newInfo
                else -> infos.providers.add(newInfo)
            }
            LineInfoModel.saveInfo(infos)
        }

        val editLines = { info: LineInfo? ->
            LineDialog.showDialog(
                this,
                info
            ) { oldInfo, newInfo ->
                setProvider(oldInfo, newInfo)
                refreshLines()
            }
        }

        val defaultLine = infos?.getDefaultProvider()
        activityRef.get()?.runOnUiThread {
            val showPlugin = type.isNotEmpty()
            epLayout.getChildAt(0).visibility = if (showPlugin) View.GONE else View.VISIBLE
            epLayout.getChildAt(1).visibility = if (showPlugin) View.GONE else View.VISIBLE
            epView.visibility = if (showPlugin) View.VISIBLE else View.GONE
            epLayout.visibility = if (showPlugin || subject.eps?.size ?: 0 > 0) View.VISIBLE else View.GONE

            if (defaultLine != null) {
                val provider = LineProvider.getProvider(type, defaultLine.site)
                epView.episodes_line_id.text = if (defaultLine.title.isEmpty()) defaultLine.id else defaultLine.title
                epView.episodes_line_site.backgroundTintList =
                    ColorStateList.valueOf(((provider?.color ?: 0) + 0xff000000).toInt())
                epView.episodes_line_site.visibility = View.VISIBLE
                epView.episodes_line_site.text = provider?.title ?: { if (defaultLine.site == "") "线路" else "错误接口" }()
                epView.episodes_line.setOnLongClickListener { _ ->
                    LineProvider.getProvider(type, defaultLine.site)?.provider?.let { p ->
                        if (!p.open.isNullOrEmpty())
                            subscribeOnUiThread(p.open("open", App.app.jsEngine, defaultLine), { url ->
                                activityRef.get()?.let { AppUtil.openBrowser(it, url) }
                            }, key = "open")
                    }
                    true
                }
                epView.episodes_line.setOnClickListener {
                    val popList = ListPopupWindow(pluginContext)
                    popList.anchorView = epView.episodes_line
                    val lines = ArrayList(infos.providers)
                    lines.add(LineInfo("", "已缓存", subjectId = 0))
                    lines.add(LineInfo("", "添加线路", subjectId = 0))
                    val adapter = LineAdapter(type, pluginContext, lines)
                    adapter.selectIndex = if (selectCache) lines.size - 2 else infos.subject.defaultLine
                    popList.setAdapter(adapter)
                    popList.isModal = true
                    popList.show()
                    popList.listView?.setOnItemClickListener { _, _, position, _ ->
                        popList.dismiss()
                        selectCache = position == lines.size - 2
                        when (position) {
                            lines.size - 2 -> refreshLines()
                            lines.size - 1 -> editLines(null)
                            else -> {
                                infos.subject.defaultLine = position
                                LineInfoModel.saveInfo(infos)
                                epInfo = null
                                refreshLines()
                            }
                        }
                    }
                    popList.listView?.setOnItemLongClickListener { _, _, position, _ ->
                        popList.dismiss()
                        if (position == lines.size - 1) editLines(null)
                        else if (position < lines.size - 2) editLines(lines[position])
                        true
                    }
                }
                // 加载eps
                if (selectCache) {
                    epView.episodes_line_site.visibility = View.GONE
                    epView.episodes_line_id.text = "已缓存"
                    emptyView.text = "什么都没有哦"
                    val eps = App.app.episodeCacheModel.getSubjectCacheList(subject)?.episodeList?.map {
                        it.episode
                    }
                    episodeAdapter.setNewInstance(eps?.toMutableList())
                    val list = eps?.map {
                        EpisodeAdapter.EpisodeSection(it)
                    }?.toMutableList() ?: ArrayList()
                    list.add(0, EpisodeAdapter.EpisodeSection(true, "已缓存"))
                    episodeDetailAdapter.setNewInstance(list)
                    epView.btn_detail.text = pluginContext.getString(R.string.parse_cache_eps, eps?.size ?: 0)
                } else if (epInfo != provider) (provider?.provider as? BookProvider)?.let {
                    emptyView.text = "加载中..."
                    episodeAdapter.setNewInstance(null)
                    epInfo = provider
                    subscribeOnUiThread(
                        it.getEpisode("loadEps", App.app.jsEngine, defaultLine),
                        { eps ->
                            if (epInfo == provider) {
                                emptyView.text = "什么都没有哦"
                                subject.eps = eps.map { bookEpisode ->
                                    Episode(
                                        type = if (bookEpisode.category.isNullOrEmpty()) Episode.TYPE_MAIN else Episode.TYPE_MUSIC,
                                        sort = bookEpisode.sort,
                                        category = bookEpisode.category,
                                        book = bookEpisode
                                    )
                                }
                                episodeAdapter.setNewInstance(subject.eps?.toMutableList())
                                subjectView.updateEpisode(subject)
                                updateProgress()
                            }
                            (epView.episode_list.layoutManager as LinearLayoutManager)
                                .scrollToPositionWithOffset(
                                    episodeAdapter.data.indexOfLast { it.progress == Episode.PROGRESS_WATCH },
                                    0
                                )
                        }, {
                            emptyView.text = it.message
                        }
                    )
                }
                updateProgress()
            } else {
                epView.episodes_line_site.visibility = View.GONE
                epView.episodes_line_id.text = "+ 添加线路"
                epView.episodes_line.setOnClickListener {
                    editLines(null)
                }
                emptyView.text = "点击线路加载剧集"
                episodeAdapter.setNewInstance(null)
            }

            (if (type == Provider.TYPE_BOOK || selectCache) episodeAdapter else subjectView.episodeAdapter).also { adapter ->
                if (epView.episode_list.adapter != adapter) epView.episode_list.adapter = adapter
            }
        }
    }
}