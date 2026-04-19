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
        "$mainUrl/brother-sister/" to "StepSister",
        "$mainUrl/teen/" to "Teen",
        "$mainUrl/threesome/" to "Threesome",
        "$mainUrl/stepmom/" to "StepMom",
        "$mainUrl/stepdaughter/" to "StepDaughter",
        "$mainUrl/sneaky/" to "Sneaky",
        "$mainUrl/milf/" to "MILF",
        "$mainUrl/foursome/" to "Foursome",
        "$mainUrl/cheating/" to "Cheating",
        "$mainUrl/swap/" to "Swap",
        "$mainUrl/freeuse/" to "Freeuse",
        "$mainUrl/public/" to "Public",
        "$mainUrl/asian/" to "Asian",
        "$mainUrl/hijab/" to "Hijab",
        "$mainUrl/grandparent/" to "Grandparent",
        "$mainUrl/" to "Latest",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data
                  else request.data.trimEnd('/') + "/page/$page/"
        val doc = app.get(url, headers = ua).document

        // শেষ .videos div এ actual video links থাকে
        val videoSection = doc.select(".videos").last()
        val items = videoSection?.select("a.video")?.mapNotNull { a ->
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = a.attr("title").trim().ifBlank { return@mapNotNull null }
            // poster style attribute থেকে বের করো
            val style = a.attr("style")
            val poster = Regex("""url\(['"]?([^'")\s]+)['"]?\)""").find(style)?.groupValues?.get(1)
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        } ?: emptyList()

        return newHomePageResponse(request.name, items, page < 10)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}", headers = ua).document
        val videoSection = doc.select(".videos").last()
        return videoSection?.select("a.video")?.mapNotNull { a ->
            val href = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val title = a.attr("title").trim().ifBlank { return@mapNotNull null }
            val style = a.attr("style")
            val poster = Regex("""url\(['"]?([^'")\s]+)['"]?\)""").find(style)?.groupValues?.get(1)
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        val title = doc.selectFirst("h1, .entry-title")?.text()?.trim() ?: doc.title().trim()
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")

        // source[type=video/mp4] থেকে URL নাও
        val videoUrl = doc.selectFirst("video.xplayer-video source[type*=mp4]")
            ?.attr("src")?.trim() ?: ""

        return newMovieLoadResponse(title, url, TvType.Movie, videoUrl.ifBlank { url }) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        if (data.contains("vcdn.cc") || data.contains(".mp4")) {
            callback(newExtractorLink(name, name, data, ExtractorLinkType.VIDEO) {
                this.quality = Qualities.Unknown.value
                this.referer = mainUrl
                this.headers = ua
            })
            return true
        }

        // data page URL হলে page থেকে video URL বের করো
        val doc = app.get(data, headers = ua).document
        val videoUrl = doc.selectFirst("video.xplayer-video source[type*=mp4]")
            ?.attr("src")?.trim() ?: return false

        callback(newExtractorLink(name, name, videoUrl, ExtractorLinkType.VIDEO) {
            this.quality = Qualities.Unknown.value
            this.referer = mainUrl
            this.headers = ua
        })
        return true
    }
}
