import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Mesmo grafo Koin do Android: repositórios, token store e ViewModel.
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
