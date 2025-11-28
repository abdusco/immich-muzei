package dev.abdus.apps.immich.data

data class ImmichUiState(
    val config: ImmichConfig = ImmichConfig(null, null, emptySet(), emptySet(), false),
    val albums: List<ImmichAlbumUiModel> = emptyList(),
    val tags: List<ImmichTagUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sortBy: AlbumSortBy = AlbumSortBy.ASSET_COUNT,
    val sortReversed: Boolean = true
)

enum class AlbumSortBy {
    NAME,
    UPDATED_AT,
    ASSET_COUNT,
    MOST_RECENT_PHOTO
}

