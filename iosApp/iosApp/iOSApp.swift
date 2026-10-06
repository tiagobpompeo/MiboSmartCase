import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Mesmo grafo Koin do Android: repositório GDI, token store e ViewModel.
        KoinKt.doInitKoin()
        // Registra o player nativo (Swift + VLCKit) que o Compose usa via expect/actual.
        NativePlayers.shared.factory = VLCVideoPlayerFactory()
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
