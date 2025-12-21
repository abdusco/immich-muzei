package dev.abdus.apps.immich.api

import dev.abdus.apps.immich.data.ImmichConfig

object ImmichClientProvider {
    fun fromConfig(config: ImmichConfig): ImmichClient? {
        if (!config.isConfigured) return null
        return ImmichClient.create(
            baseUrl = checkNotNull(config.apiBaseUrl),
            apiKey = checkNotNull(config.apiKey)
        )
    }
}
