package com.github.eprendre.tingshu.sources.impl

import android.view.View
import com.github.eprendre.tingshu.App
import com.github.eprendre.tingshu.R
import com.github.eprendre.tingshu.sources.AudioUrlExtractor
import com.github.eprendre.tingshu.sources.AudioUrlWebViewExtractor
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.sources.TingShuSourceHandler
import com.github.eprendre.tingshu.utils.*
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.upstream.DataSource
import io.reactivex.Completable
import io.reactivex.Single
import org.jsoup.Jsoup
import java.net.URLEncoder

object M56TingShu : TingShu {
    override fun getCategoryMenus(): List<CategoryMenu> {
        val menu1 = CategoryMenu(
            "小说", R.drawable.ic_library_books, View.generateViewId(), listOf(
                CategoryTab("玄幻武侠", "http://m.ting56.com/paihangbang/1-1.html"),
                CategoryTab("都市言情", "http://m.ting56.com/paihangbang/2-2.html"),
                CategoryTab("恐怖悬疑", "http://m.ting56.com/paihangbang/3-3.html"),
                CategoryTab("网游竞技", "http://m.ting56.com/paihangbang/4-4.html"),
                CategoryTab("军事历史", "http://m.ting56.com/paihangbang/6-6.html"),
                CategoryTab("刑侦推理", "http://m.ting56.com/paihangbang/41-41.html")
            )
        )
        val menu2 = CategoryMenu(
            "其它", R.drawable.ic_more_horiz, View.generateViewId(), listOf(
                CategoryTab("职场商战", "http://m.ting56.com/paihangbang/7-7.html"),
                CategoryTab("百家讲坛", "http://m.ting56.com/paihangbang/10-10.html"),
                CategoryTab("广播剧", "http://m.ting56.com/paihangbang/40-40.html"),
                CategoryTab("幽默笑话", "http://m.ting56.com/paihangbang/44-44.html"),
                CategoryTab("相声", "http://m.ting56.com/book/43.html"),
                CategoryTab("儿童读物", "http://m.ting56.com/paihangbang/11-11.html")
            )
        )
        return listOf(menu1, menu2)
    }

    override fun getAudioUrlExtractor(exoPlayer: ExoPlayer, dataSourceFactory: DataSource.Factory): AudioUrlExtractor {
        AudioUrlWebViewExtractor.setUp(exoPlayer, dataSourceFactory) { doc ->
            val audioElement = doc.getElementById("jp_audio_0")
            audioElement?.attr("src")
        }
        return AudioUrlWebViewExtractor
    }

    override fun playFromBookUrl(bookUrl: String): Completable {
        return Completable.fromCallable {
            val doc = Jsoup.connect(bookUrl).get()
//            val book = doc.getElementsByClass("list-ov-tw").first()
//            val cover = book.getElementsByTag("img").first().attr("src")

            TingShuSourceHandler.downloadCoverForNotification()

            //获取书本信息
//            val bookInfos = book.getElementsByTag("span").map { it.text() }
//            Prefs.currentBookName = bookInfos[0]
//            Prefs.author = bookInfos[2]
//            Prefs.artist = bookInfos[3]

            //获取章节列表
            val episodes = doc.getElementById("playlist")
                .getElementsByTag("a")
                .map {
                    Episode(it.text(), it.attr("abs:href"))
                }
            App.playList = episodes
            Prefs.currentIntro = doc.selectFirst(".book_intro").ownText()
            return@fromCallable null
        }
    }

    override fun getCategoryDetail(url: String): Single<Category> {
        return Single.fromCallable {
            var currentPage: Int
            var totalPage: Int

            val list = ArrayList<Book>()
            val doc = Jsoup.connect(url).get()
            val container = doc.selectFirst(".xsdz")
            doc.getElementById("page_num1").text().split("/").let {
                currentPage = it[0].toInt()
                totalPage = it[1].toInt()
            }
            val nextUrl = doc.getElementById("page_next1").attr("abs:href")
            val elementList = container.getElementsByClass("list-ov-tw")
            elementList.forEach { item ->
                var coverUrl = item.selectFirst(".list-ov-t a img").attr("original")
                if (coverUrl.startsWith("/")) {//有些网址已拼接好，有些没有拼接
                    //这里用主站去拼接，因为用http://m.ting56.com/拼接时经常封面报错
                    coverUrl = "http://www.ting56.com$coverUrl"
                }
                val ov = item.selectFirst(".list-ov-w")
                val bookUrl = ov.selectFirst(".bt a").attr("abs:href")
                val title = ov.selectFirst(".bt a").text()
                val (author, artist) = ov.select(".zz").let { element ->
                    Pair(element[0].text(), element[1].text())
                }
                val intro = ov.selectFirst(".nr").text()
                list.add(Book(coverUrl, bookUrl, title, author, artist).apply { this.intro = intro })
            }

            return@fromCallable Category(list, currentPage, totalPage, url, nextUrl)
        }
    }

    override fun search(keywords: String, page: Int): Single<Pair<List<Book>, Int>> {
        return Single.fromCallable {
            //            var currentPage: Int
            var totalPage: Int
            val url = "http://m.ting56.com/search.asp?searchword=${URLEncoder.encode(keywords, "gb2312")}&page=$page"
            val list = ArrayList<Book>()
            val doc = Jsoup.connect(url).get()
            val container = doc.selectFirst(".xsdz")
            container.getElementById("page_num1").text().split("/").let {
                //                currentPage = it[0].toInt()
                totalPage = it[1].toInt()
            }
            val elementList = container.getElementsByClass("list-ov-tw")
            elementList.forEach { item ->
                val coverUrl = item.selectFirst(".list-ov-t a img").attr("original")
                val ov = item.selectFirst(".list-ov-w")
                val bookUrl = ov.selectFirst(".bt a").attr("abs:href")
                val title = ov.selectFirst(".bt a").text()
                val (author, artist) = ov.select(".zz").let { element ->
                    Pair(element[0].text(), element[1].text())
                }
                val intro = ov.selectFirst(".nr").text()
                list.add(Book(coverUrl, bookUrl, title, author, artist).apply { this.intro = intro })
            }
            return@fromCallable Pair(list, totalPage)
        }
    }
}
