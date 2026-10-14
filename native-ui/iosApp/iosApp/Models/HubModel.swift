import Foundation
import Shared

/// Hub Zigbee: online, firmware consultado na hora (a listagem pode estar velha) e acessórios via `sdk.children(hubNs:)`.
@MainActor
final class HubModel: ObservableObject {
    let ns: String
    let hub: Device?
    private let sdk: CasaSdk

    @Published private(set) var online: Bool?
    @Published private(set) var firmware: Firmware?
    @Published private(set) var firmwareError: AppError?
    @Published private(set) var loadingFirmware = false

    init(ns: String, sdk: CasaSdk = Sdk.shared) {
        self.ns = ns
        self.sdk = sdk
        hub = sdk.device(ns: ns)
        online = hub?.isOnline
    }

    /// Subdispositivos já carregados na listagem (parentNs == ns do hub): leitura síncrona, sem rede.
    var children: [Device] { sdk.children(hubNs: ns) }

    func load() {
        guard let hub else { return }
        Task {
            // Boolean? do Kotlin: KotlinBoolean? -> Bool?. nil = a API não informou: a tela mostra "—".
            if let value = try? await sdk.isOnline(device: hub) { online = value.boolValue }
        }
        loadFirmware()
    }

    func loadFirmware() {
        guard let hub, !loadingFirmware else { return }
        loadingFirmware = true
        firmwareError = nil
        Task {
            do {
                let outcome = try await sdk.firmware(device: hub)
                firmware = outcome.valueOrNull
                firmwareError = outcome.errorOrNull
            } catch {}
            loadingFirmware = false
        }
    }
}
