import Foundation
import Shared

/// Vídeo ao vivo (RF03): pede a sessão à GDI (`startLive` -> URL RTSP), acompanha o progresso do player e encerra
/// a sessão ao sair (`stopLive`). A GDI não informa progresso: 0–29 % é estimado pelo tempo típico da chamada
/// (~2,4 s medidos); 30–100 % vem do VLCKit.
@MainActor
final class CameraModel: ObservableObject {
    static let requestingLabel = "Solicitando sessão à Intelbras"
    /// Fato da API: criar-fluxo-video devolve o mesmo RTSP para canalVideo 0 e 1 na iM4 Dual.
    static let sameStreamNote = "A API GDI devolve o mesmo vídeo para as duas lentes desta câmera."
    static let lensLabels = ["Lente móvel", "Lente fixa"] // canalVideo 0 = móvel, 1 = fixa
    static let fixedLensChannel: Int32 = 1
    private static let requestTypicalMs: Int64 = 2400

    let ns: String
    let camera: Device?
    private let sdk: CasaSdk

    @Published private(set) var session: StreamSession?
    @Published private(set) var error: String?
    @Published private(set) var percent = 0
    @Published private(set) var label = CameraModel.requestingLabel
    @Published private(set) var playing = false
    /// Métrica de desempenho: do toque (ou retry/troca de lente) até o 1.º quadro, incluindo a GDI.
    @Published private(set) var firstFrameMs: Int64?
    /// Lente (canalVideo), repassada à GDI sem interpretação. Int do Kotlin = Int32.
    @Published private(set) var channel: Int32 = 0

    private var startedAt = DispatchTime.now()
    private var requestTask: Task<Void, Never>?
    private var ticker: Task<Void, Never>?
    private var started = false

    init(ns: String, sdk: CasaSdk = Sdk.shared) {
        self.ns = ns
        self.sdk = sdk
        camera = sdk.device(ns: ns)
    }

    deinit {
        requestTask?.cancel()
        ticker?.cancel()
    }

    var lensCount: Int { camera?.hasDualLens == true ? 2 : 1 }
    /// Depois do 1.º quadro o overlay não volta: rebuffering breve não deve cobrir o vídeo.
    var loading: Bool { error == nil && !playing && firstFrameMs == nil }

    private var elapsedMs: Int64 {
        Int64((DispatchTime.now().uptimeNanoseconds - startedAt.uptimeNanoseconds) / 1_000_000)
    }

    // MARK: Ciclo de vida (a tela chama em onAppear/onDisappear)

    func start() {
        guard !started else { return } // onAppear pode disparar mais de uma vez no NavigationStack
        started = true
        attempt()
    }

    func retry() { attempt() }

    /// Um player por vez: encerra a sessão ANTES de trocar o canal.
    func switchLens(_ lens: Int32) {
        guard lens != channel else { return }
        channel = lens
        attempt()
    }

    func stopLive() {
        requestTask?.cancel()
        ticker?.cancel()
        endSession()
    }

    private func attempt() {
        guard let camera else { return }
        requestTask?.cancel()
        ticker?.cancel()
        // Trocar de lente ou "Tentar novamente" substitui a sessão: a anterior é encerrada, não abandonada.
        endSession()
        error = nil
        percent = 0
        label = Self.requestingLabel
        playing = false
        firstFrameMs = nil
        startedAt = DispatchTime.now()

        ticker = Task { [weak self] in
            while let self, !Task.isCancelled, self.session == nil, self.error == nil {
                self.percent = min(29, Int(self.elapsedMs * 30 / Self.requestTypicalMs))
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
        let channel = channel
        requestTask = Task { [weak self] in
            guard let self else { return }
            do {
                // Outcome<StreamSession>: a falha já vem traduzida em AppError.
                let outcome = try await self.sdk.startLive(camera: camera, channel: channel)
                if Task.isCancelled {
                    // A tela saiu (ou trocou de lente) enquanto a GDI respondia: não deixa sessão pendurada.
                    if let orphan = outcome.valueOrNull { self.sdk.stopLive(session: orphan) }
                    return
                }
                if let session = outcome.valueOrNull {
                    self.session = session
                    self.percent = max(self.percent, 30)
                    self.label = "Conectando ao stream"
                } else {
                    self.error = outcome.errorOrNull?.userMessage ?? "Não foi possível iniciar o vídeo."
                }
            } catch {
                // Cancelamento convertido em NSError pelo Kotlin/Native: nada a mostrar.
            }
            self.ticker?.cancel()
        }
    }

    /// `stopLive` não é suspend: roda no escopo da fachada e nunca falha para a UI.
    private func endSession() {
        if let session {
            sdk.stopLive(session: session)
        }
        session = nil
    }

    // MARK: Callbacks do VLCPlayerView (30–100 %)

    func onPlayerProgress(percent: Int, label: String) {
        self.percent = max(self.percent, percent) // nunca regride
        self.label = label
    }

    func onFirstFrame() {
        if firstFrameMs == nil { firstFrameMs = elapsedMs }
        percent = 100
        playing = true
    }

    func onPlayerError(_ message: String) {
        error = message
    }
}
