import SwiftUI
import Shared

/// App iOS 100 % SwiftUI. O `shared` entra só como biblioteca (regra + dados); nenhuma tela vem do Kotlin.
@main
struct iOSApp: App {
    init() {
        // Mesmo grafo Koin do Android: repositório GDI, Keychain, casos de uso e a fachada CasaSdk.
        // Precisa rodar antes de qualquer `CasaSdkKt.casaSdk()`.
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup { RootView() }
    }
}
