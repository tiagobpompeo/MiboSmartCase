package br.com.pompeo.casa.domain.model

/** Filtro de origem enviado no campo "origem" de /produtos/listar-dispositivos/v1. */
enum class OriginFilter(val apiValue: String, val label: String) {
    ALL("todos", "Todos"),
    LINKED("vinculados", "Vinculados"),
    SHARED("compartilhados", "Compartilhados");

    /** Filtro defensivo no cliente: só exclui quando a origem é conhecida e diferente da pedida. */
    fun accepts(origin: DeviceOrigin): Boolean =
        when (this) {
            ALL -> true
            LINKED -> origin != DeviceOrigin.SHARED
            SHARED -> origin != DeviceOrigin.LINKED
        }
}

data class DeviceQuery(val origin: OriginFilter = OriginFilter.ALL, val page: Int = 1, val pageSize: Int = DEFAULT_PAGE_SIZE) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 5
        /** 2 existe para o avaliador ver "Carregar mais" funcionando na conta de teste (3 dispositivos). */
        val PAGE_SIZES = listOf(2, 5, 10, 50)
    }
}

/** Uma página da listagem. A GDI não devolve total: [hasMore] = veio página cheia. */
data class DevicePage(val devices: List<Device>, val page: Int, val pageSize: Int) {
    val hasMore: Boolean get() = devices.size >= pageSize
}
