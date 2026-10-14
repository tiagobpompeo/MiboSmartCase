import Foundation
import Shared

/// Fechadura (RF05, RF06, RF09). As leituras são automáticas; os comandos físicos (`setLock`, `changeVolume`) só são
/// chamados pela LockView DEPOIS de o usuário confirmar no `confirmationDialog`. Um comando por vez (`sending`).
/// `LockDetails` é imutável (data class): os campos são copiados para @Published próprios para a tela reagir.
@MainActor
final class LockModel: ObservableObject {
    static let historyFirst = 10          // o `lockDetails` já traz os 10 primeiros
    static let historyMore: Int32 = 30    // "Ver mais" pede 30 (Int do Kotlin = Int32)
    static let lowBatteryPercent = 30 // mesmo limiar do app Compose da raiz

    let ns: String
    let lock: Device?
    private let sdk: CasaSdk

    @Published private(set) var loading = false
    @Published private(set) var loadError: AppError?
    /// `Boolean?` do Kotlin (KotlinBoolean?) já convertido: nil = a API não informou.
    @Published private(set) var isOpen: Bool?
    @Published private(set) var remoteEnabled: Bool?
    @Published private(set) var battery: Int?
    @Published private(set) var online: Bool?
    @Published private(set) var volume: LockVolume?
    @Published private(set) var volumeError: String?
    @Published private(set) var history: [LockEvent] = []
    @Published private(set) var historyError: String?
    @Published private(set) var historyExpanded = false
    @Published private(set) var historyLoading = false
    /// Comando físico em andamento (abrir/fechar ou volume): bloqueia os demais até terminar.
    @Published private(set) var sending = false
    /// Resultado do último comando, mostrado em alerta pela tela.
    @Published var notice: String?

    init(ns: String, sdk: CasaSdk = Sdk.shared) {
        self.ns = ns
        self.sdk = sdk
        lock = sdk.device(ns: ns)
        online = lock?.isOnline
    }

    /// Já respondeu (com dado ou erro): as ações deixam de dizer "Consultando…".
    var settled: Bool { !loading && (loadError != nil || isOpen != nil || battery != nil) }
    var lowBattery: Bool { (battery ?? 100) < Self.lowBatteryPercent }
    var visibleHistory: [LockEvent] { historyExpanded ? history : Array(history.prefix(Self.historyFirst)) }
    var canExpandHistory: Bool { !historyExpanded && history.count >= Self.historyFirst }

    // MARK: Leituras

    func load() {
        guard let lock, !loading else { return }
        loading = true
        loadError = nil
        Task {
            do {
                // Outcome<LockDetails>: sucesso em valueOrNull, falha já traduzida em errorOrNull.
                let outcome = try await sdk.lockDetails(lock: lock)
                if let details = outcome.valueOrNull {
                    apply(details)
                } else {
                    loadError = outcome.errorOrNull
                }
            } catch {
                // Cancelado: a tela saiu.
            }
            loading = false
        }
        Task {
            // Consulta separada: o online da listagem pode estar velho.
            if let value = try? await sdk.isOnline(device: lock) { online = value.boolValue }
        }
    }

    private func apply(_ details: LockDetails) {
        isOpen = details.open?.boolValue
        remoteEnabled = details.remoteEnabled?.boolValue
        battery = details.battery?.intValue
        volume = details.volume
        volumeError = details.volumeError
        history = details.history
        historyError = details.historyError
        historyExpanded = false
    }

    /// "Ver mais": troca os 10 primeiros pelos 30 mais recentes (Outcome<List<LockEvent>> chega como NSArray).
    func loadMoreHistory() {
        guard let lock, !historyLoading else { return }
        historyLoading = true
        Task {
            do {
                let outcome = try await sdk.lockHistory(lock: lock, quantity: Self.historyMore)
                if let events = outcome.valueOrNull as? [LockEvent] {
                    history = events
                    historyError = nil
                    historyExpanded = true
                } else {
                    historyError = outcome.errorOrNull?.userMessage
                }
            } catch {}
            historyLoading = false
        }
    }

    func collapseHistory() { historyExpanded = false }

    // MARK: Comandos físicos (só depois da confirmação explícita na tela)

    /// Abre (`open = true`) ou fecha a porta pela nuvem. `AppError?` nil = comando aceito pela GDI.
    func setLock(open: Bool) {
        guard let lock, !sending else { return }
        sending = true
        Task {
            do {
                if let error = try await sdk.setLock(lock: lock, open: open) {
                    notice = "Comando falhou: \(error.userMessage)"
                } else {
                    notice = open ? "Comando de abertura enviado." : "Comando de fechamento enviado."
                    reloadState()
                }
            } catch {}
            sending = false
        }
    }

    /// Grava e relê (regra do caso de uso); o Outcome traz o volume que a tela deve exibir.
    func changeVolume(_ level: LockVolume) {
        guard let lock, !sending, level != volume else { return }
        sending = true
        Task {
            do {
                let outcome = try await sdk.changeLockVolume(lock: lock, volume: level)
                if let applied = outcome.valueOrNull {
                    volume = applied
                    volumeError = nil
                    notice = "Volume alterado para \(applied.label)."
                } else {
                    notice = "Não foi possível alterar o volume: \(outcome.errorOrNull?.userMessage ?? "erro desconhecido")"
                }
            } catch {}
            sending = false
        }
    }

    /// Depois de um comando de porta, relê só estado/bateria sem bloquear a tela com "Consultando…".
    private func reloadState() {
        guard let lock else { return }
        Task {
            if let details = try? await sdk.lockDetails(lock: lock).valueOrNull {
                isOpen = details.open?.boolValue
                remoteEnabled = details.remoteEnabled?.boolValue
                battery = details.battery?.intValue
                if historyError == nil, !historyExpanded { history = details.history }
            }
        }
    }
}
