package br.com.pompeo.casa.player

import platform.UIKit.UIView

/**
 * Contrato que o Swift implementa (VLCKit). O Kotlin/Native não importa o VLCKit: em vez de cinterop
 * (.def, headers e linkagem por target, sincronizados com o SPM), o app iOS registra uma implementação
 * na inicialização. Kotlin define, Swift entrega; o Gradle nem sabe que o VLCKit existe.
 */
interface NativeVideoPlayer {
    fun view(): UIView
    /** O Swift chama este listener com os estados do VLCKit. */
    fun setListener(listener: NativeVideoListener?)
    fun play(url: String)
    fun setMuted(muted: Boolean)
    fun stop()
}

/** Callbacks do player nativo para o Compose: porcentagem, primeiro quadro e erro. (Flow não atravessa para o Swift.) */
interface NativeVideoListener {
    fun onProgress(percent: Int, label: String)
    fun onFirstFrame()
    fun onError(message: String)
}

interface NativeVideoPlayerFactory {
    fun create(): NativeVideoPlayer
}

/** No Swift: `NativePlayers.shared.factory = VLCVideoPlayerFactory()`. */
object NativePlayers {
    var factory: NativeVideoPlayerFactory? = null
}
