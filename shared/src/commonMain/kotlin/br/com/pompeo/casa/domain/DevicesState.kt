package br.com.pompeo.casa.domain

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery

/** Estado único da listagem de dispositivos (RF02, RF04, RF07, RF08). */
sealed interface DevicesState {
    data object NoToken : DevicesState
    /** Primeira página em andamento, sem conteúdo anterior. */
    data object Loading : DevicesState

    data class Content(
        val devices: List<Device>,
        val query: DeviceQuery,
        val hasMore: Boolean,
        val pagesLoaded: Int,
        /** refresh() com conteúdo já na tela: barra fina no topo, a lista continua visível. */
        val refreshing: Boolean = false,
        /** "Carregar mais" em andamento. */
        val loadingMore: Boolean = false,
        /** Erro ao carregar mais não derruba a lista. */
        val loadMoreError: AppError? = null,
        /** Ex.: "Filtro aplicado no aparelho". */
        val note: String? = null,
    ) : DevicesState

    /** 200 com lista vazia na página 1. */
    data class Empty(val query: DeviceQuery) : DevicesState
    data class Error(val error: AppError, val query: DeviceQuery) : DevicesState
}
