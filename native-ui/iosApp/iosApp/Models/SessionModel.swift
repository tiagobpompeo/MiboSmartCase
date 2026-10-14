import Foundation
import Shared

/// Sessão (RF01): observa `sdk.session` (StateFlow do Kotlin, exposto como StateWatcher) e valida o token.
/// O valor do token NUNCA passa por aqui além do `submit`: a UI só vê `maskedToken`.
@MainActor
final class SessionModel: ObservableObject {
    @Published private(set) var info: SessionInfo
    @Published private(set) var validating = false
    @Published private(set) var error: AppError?

    private let sdk: CasaSdk
    private var handle: WatchHandle?

    init(sdk: CasaSdk = Sdk.shared) {
        self.sdk = sdk
        // `value` chega como opcional (genérico leve do Obj-C): o fallback nunca acontece na prática.
        info = sdk.session.value ?? SessionInfo(hasToken: false, maskedToken: nil, persisted: false)
        // O callback roda no Dispatchers.Main (dispatcher da fachada): pode escrever no @Published direto.
        handle = sdk.session.watch { [weak self] info in
            guard let self, let info else { return }
            self.info = info
        }
    }

    deinit { handle?.cancel() }

    var hasToken: Bool { info.hasToken }
    var maskedToken: String? { info.maskedToken }
    var persisted: Bool { info.persisted }
    /// Token de local.properties (desenvolvimento): só pré-preenche o campo, nunca login automático.
    var suggestedToken: String? { sdk.suggestedToken }

    /// `submitToken` é suspend: chega como `async throws`, mas só lança em cancelamento; o erro de negócio
    /// vem no retorno (`AppError?`, nil = aceito e 1.ª página já carregada).
    func submit(_ raw: String) {
        guard !validating else { return }
        validating = true
        error = nil
        Task { [weak self] in
            guard let self else { return }
            do {
                self.error = try await self.sdk.submitToken(raw: raw)
            } catch {
                // Cancelamento da Task: o caso de uso já tirou o token da memória.
            }
            self.validating = false
        }
    }

    func logout() {
        error = nil
        sdk.logout()
    }
}
