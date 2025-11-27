package dev.abdus.apps.immich.data

import kotlinx.serialization.Serializable

@Serializable
data class ImmichTagUiModel(
    val id: String,
    val name: String
)

