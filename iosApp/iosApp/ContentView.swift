import SwiftUI
import UIKit
import Shared

/// Hospeda a árvore Compose Multiplatform inteira; não há telas SwiftUI além desta.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // O Compose ocupa a tela inteira e trata os insets por conta própria.
        ComposeView().ignoresSafeArea()
    }
}
