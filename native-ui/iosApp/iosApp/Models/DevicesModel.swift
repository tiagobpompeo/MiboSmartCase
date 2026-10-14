import Foundation
import Shared

/// Listagem (RF02, RF07, RF08): espelha `sdk.devicesState` e `sdk.query` em @Published e repassa as ações.
/// `DevicesState` é uma sealed interface no Kotlin; no Swift vira protocolo + classes (`DevicesStateContent`…),
/// então as telas fazem `as?` em vez de `switch`.
@MainActor
final class DevicesModel: ObservableObject {
    @Published private(set) var state: DevicesState
    @Published private(set) var query: DeviceQuery

    private let sdk: CasaSdk
    private var handles: [WatchHandle] = []

    init(sdk: CasaSdk = Sdk.shared) {
        self.sdk = sdk
        state = sdk.devicesState.value ?? DevicesStateNoToken.shared
        query = sdk.query.value ?? DeviceQuery(origin: .all, page: 1, pageSize: DeviceQuery.companion.DEFAULT_PAGE_SIZE)
        handles.append(sdk.devicesState.watch { [weak self] state in
            guard let self, let state else { return }
            self.state = state
        })
        handles.append(sdk.query.watch { [weak self] query in
            guard let self, let query else { return }
            self.query = query
        })
    }

    deinit { handles.forEach { $0.cancel() } }

    // MARK: Leitura

    /// `List<Int>` do Kotlin chega como `[KotlinInt]`; `Int` do Kotlin é `Int32` no Swift.
    var pageSizes: [Int32] { DeviceQuery.companion.PAGE_SIZES.map { $0.int32Value } }
    var providerNames: [String] { sdk.providerNames }
    func providerName(_ device: Device) -> String? { sdk.providerName(device: device) }

    /// As telas de detalhe recebem só o `ns` pela navegação e buscam o Device aqui.
    func device(ns: String) -> Device? { sdk.device(ns: ns) }
    func children(hubNs: String) -> [Device] { sdk.children(hubNs: hubNs) }

    /// refresh() com conteúdo na tela (barra fina) ou primeira página em andamento.
    var isBusy: Bool {
        if state is DevicesStateLoading { return true }
        return (state as? DevicesStateContent)?.refreshing == true
    }

    // MARK: Ações (síncronas na fachada: o repositório lança a coroutine no escopo dele)

    func refresh() { sdk.refresh() }
    func loadMore() { sdk.loadMore() }
    func setOrigin(_ filter: OriginFilter) { sdk.setOrigin(filter: filter) }
    func setPageSize(_ size: Int32) {
        guard size != query.pageSize else { return }
        sdk.setPageSize(size: size)
    }

    /// Para o pull-to-refresh do SwiftUI, que espera um `async`: dispara o refresh e aguarda o estado assentar
    /// (máximo 15 s, para o indicador nunca ficar preso).
    func refreshAndWait() async {
        sdk.refresh()
        for _ in 0..<150 {
            try? await Task.sleep(nanoseconds: 100_000_000)
            if !isBusy { return }
        }
    }
}
