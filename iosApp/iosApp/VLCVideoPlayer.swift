import Shared
import UIKit
import VLCKitSPM

/// Implementa em Swift a interface Kotlin `NativeVideoPlayer`: o Compose controla, o VLCKit decodifica.
/// Necessário porque o AVPlayer não reproduz RTSP e a nuvem Intelbras entrega H.265 por RTSP.
/// NSObject: uma interface Kotlin chega ao Swift como protocolo Objective-C.
final class VLCVideoPlayer: NSObject, NativeVideoPlayer, VLCMediaPlayerDelegate {
    private let container = UIView()
    private let player = VLCMediaPlayer()
    private var listenerRef: NativeVideoListener?
    private var firstFrameSent = false

    override init() {
        super.init()
        container.backgroundColor = .black
        player.drawable = container
        player.delegate = self
    }

    func view() -> UIView { container }

    func setListener(listener: NativeVideoListener?) {
        listenerRef = listener
    }

    func play(url: String) {
        guard let mediaURL = URL(string: url) else {
            listenerRef?.onError(message: "URL inválida")
            return
        }
        firstFrameSent = false
        let media = VLCMedia(url: mediaURL)
        // Mesmo diagnóstico do Android: sem "fmtp" no SDP o decoder de hardware não sabe a resolução;
        // o software (avcodec) lê VPS/SPS/PPS do fluxo. O áudio AAC vem sem "config" e é desativado.
        // "codec=avcodec" força o software já na 1.ª tentativa: só "avcodec-hw=none" não basta, o VLC
        // tentava antes o VideoToolbox, que sem "fmtp" espera e falha (abria bem mais devagar).
        // Dicionário chave→valor: aqui as opções vão SEM ":"/"--".
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

    func setMuted(muted: Bool) {
        player.audio?.isMuted = muted
    }

    func stop() {
        player.stop()
    }

    // MARK: - VLCMediaPlayerDelegate -> porcentagem no Compose (30–100 %)

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        switch player.state {
        case .opening: listenerRef?.onProgress(percent: 35, label: "Conectando ao stream") // Int Kotlin = Int32 no Swift
        case .buffering: if !firstFrameSent { listenerRef?.onProgress(percent: 60, label: "Carregando vídeo") }
        case .playing: if !firstFrameSent { listenerRef?.onProgress(percent: 85, label: "Decodificando o primeiro quadro") }
        case .error: listenerRef?.onError(message: "Falha ao reproduzir o stream")
        case .ended: listenerRef?.onError(message: "Transmissão encerrada")
        default: break
        }
    }

    /// O tempo só avança quando quadros estão sendo exibidos: primeiro avanço = primeiro quadro na tela.
    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        guard !firstFrameSent, player.hasVideoOut else { return }
        firstFrameSent = true
        listenerRef?.onFirstFrame()
    }
}

final class VLCVideoPlayerFactory: NSObject, NativeVideoPlayerFactory {
    func create() -> NativeVideoPlayer { VLCVideoPlayer() }
}
