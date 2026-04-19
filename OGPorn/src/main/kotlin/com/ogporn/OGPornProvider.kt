package com.ogporn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class OGPornProvider : MainAPI() {
    override var mainUrl = "https://ogporn.com"
    override var name = "OGPorn"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Others)

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/stepsister/" to "StepSister",
        "$mainUrl/category/teen/" to "Teen",
        "$mainUrl/category/threesome/" to "Threesome",
        "$mainUrl/category/stepmom/" to "StepMom",
        "$mainUrl/category/stepdaughter/" to "StepDaughter",
        "$mainUrl/category/sneaky/" to "Sneaky",
        "$mainUrl/category/milf/" to "MILF",
        "$mainUrl/category/foursome/" to "Foursome",
        "$mainUrl/category/cheating/" to "Cheating",
        "$mainUrl/category/swap/" to "Swap",
        "$mainUrl/category/freeuse/" to "Freeuse",
        "$mainUrl/category/public/" to "Public",
        "$mainUrl/category/asian/" to "Asian",
        "$mainUrl/category/hijab/" to "Hijab",
        "$mainUrl/category/grandparent/" to "Grandparent",
        "$mainUrl/" to "Latest",
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else request.data.trimEnd('/') + "/page/$page/"
        val doc = app.get(url, headers = ua).document
        val items = doc.select("article.post, div.post-item, .video-card").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            if (!href.contains(mainUrl)) return@mapNotNull null
            val title = (el.selectFirst("h2, h3, .title, .post-title")?.text() ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            if (poster.isNullOrBlank()) return@mapNotNull null
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
        return newHomePageResponse(request.name, items, page < 10)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}", headers = ua).document
        return doc.select("article.post").mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = (el.selectFirst("h2, h3")?.text() ?: a.attr("title")).trim().ifBlank { return@mapNotNull null }
            val poster = el.selectFirst("img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("h1, .entry-title")?.text()?.trim() ?: doc.title().trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        var videoUrl = ""
        val videoSource = doc.selectFirst("video.xplayer-video source")
        if (videoSource != null) videoUrl = videoSource.attr("src")
        if (videoUrl.isBlank()) {
            val pageHtml = doc.html()
            val videoMatch = Regex("""og\.vcdn\.cc/[^\s\"'>]+\.mp4""").find(pageHtml)
            videoUrl = videoMatch?.value ?: ""
        }
        val tags = doc.select(".taxonomy a, .cat, .ogp-tag").map { it.text().trim() }.filter { it.isNotBlank() }
        return newMovieLoadResponse(title, url, TvType.Movie, videoUrl) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isBlank() || !data.startsWith("http")) return false
        val quality = when {
            data.contains("1080") -> Qualities.P1080.value
            data.contains("720") -> Qualities.P720.value
            data.contains("480") -> Qualities.P480.value
            else -> Qualities.Unknown.value
        }
        callback(newExtractorLink(name, "$name [HD]", data, ExtractorLinkType.VIDEO) {
            this.quality = quality
            this.headers = ua + mapOf("Referer" to mainUrl)
        })
        return true
    }
}