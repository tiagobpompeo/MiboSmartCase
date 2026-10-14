import SwiftUI
import UIKit
import VLCKitSPM

/// Player RTSP em SwiftUI via UIViewRepresentable. VLCKit porque o AVPlayer não reproduz RTSP e a nuvem Intelbras
/// entrega H.265 por RTSP. Mesmas opções do projeto da raiz (ver comentário em `play`).
struct VLCPlayerView: UIViewRepresentable {
    let url: String
    /// Progresso 30–100 % + rótulo, primeiro quadro e erro: a CameraModel consolida.
    let onProgress: (Int, String) -> Void
    let onFirstFrame: () -> Void
    let onError: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .black
        context.coordinator.attach(to: container)
        context.coordinator.play(url)
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.play(url) // só reinicia se a URL mudou (troca de lente)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator: NSObject, VLCMediaPlayerDelegate {
        var parent: VLCPlayerView
        private let player = VLCMediaPlayer()
        private var currentUrl: String?
        private var firstFrameSent = false

        init(_ parent: VLCPlayerView) {
            self.parent = parent
            super.init()
            player.delegate = self
        }

        func attach(to view: UIView) { player.drawable = view }

        func play(_ url: String) {
            guard url != currentUrl else { return }
            currentUrl = url
            guard let mediaURL = URL(string: url) else {
                parent.onError("URL inválida")
                return
            }
            firstFrameSent = false
            let media = VLCMedia(url: mediaURL)
            // Mesmo diagnóstico do Android: sem "fmtp" no SDP o decoder de hardware não sabe a resolução;
            // o software (avcodec) lê VPS/SPS/PPS do fluxo. O áudio AAC vem sem "config" e é desativado.
            // "codec=avcodec" força o software já na 1.ª tentativa: só "avcodec-hw=none" não basta, o VLC
            // tentava antes o VideoToolbox, que sem "fmtp" espera e falha (abria bem mais devagar).
            media.addOptions([
                "network-caching": 300,
                "clock-jitter": 0,
                "rtsp-tcp": true,
                "codec": "avcodec",
                "avcodec-hw": "none",
                "no-audio": true,
            ])
            player.media = media
            player.play()
        }

        func stop() {
            player.stop()
            currentUrl = nil
        }

        // MARK: VLCMediaPlayerDelegate (entregue na main thread pelo VLCKit)

        func mediaPlayerStateChanged(_ aNotification: Notification) {
            switch player.state {
            case .opening: parent.onProgress(35, "Conectando ao stream")
            case .buffering: if !firstFrameSent { parent.onProgress(60, "Carregando vídeo") }
            case .playing: if !firstFrameSent { parent.onProgress(85, "Decodificando o primeiro quadro") }
            case .error: parent.onError("Falha ao reproduzir o stream")
            case .ended: parent.onError("Transmissão encerrada")
            default: break
            }
        }

        /// O tempo só avança quando quadros estão sendo exibidos: primeiro avanço = primeiro quadro na tela.
        func mediaPlayerTimeChanged(_ aNotification: Notification) {
            guard !firstFrameSent, player.hasVideoOut else { return }
            firstFrameSent = true
            parent.onFirstFrame()
        }
    }
}
