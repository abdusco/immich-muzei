package dev.abdus.apps.immich.api

import dev.abdus.apps.immich.data.ImmichConfig

object ImmichClientProvider {
    @Volatile
    private var cached: ImmichClient? = null

    /** Returns a client for the configured server, reusing the previous one if the config is unchanged. */
    fun fromConfig(config: ImmichConfig): ImmichClient? {
        if (!config.isConfigured) return null
        val baseUrl = checkNotNull(config.apiBaseUrl)
        val apiKey = checkNotNull(config.apiKey)
        cached?.let { if (it.baseUrl == baseUrl && it.apiKey == apiKey) return it }
        return ImmichClient.create(baseUrl, apiKey).also { cached = it }
    }
}
