package com.ogporn

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OGPornPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(OGPornProvider())
    }
}
