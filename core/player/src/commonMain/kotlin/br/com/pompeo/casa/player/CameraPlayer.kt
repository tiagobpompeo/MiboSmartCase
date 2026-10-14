package br.com.pompeo.casa.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Ponto onde a UI compartilhada precisa de código nativo: decodificar vídeo depende da plataforma.
 * Android: libVLC. iOS: VLCKit, implementado em Swift sobre uma interface Kotlin.
 */
@Composable
expect fun CameraPlayer(
    url: String,
    muted: Boolean,
    modifier: Modifier = Modifier,       // valores padrão só no expect
    onProgress: (PlayerProgress) -> Unit = {},
)

/** Progresso reportado pelo player nativo, de 30 a 100 %. Os primeiros 30 % são a chamada à GDI, medida pela tela. */
data class PlayerProgress(val percent: Int, val label: String, val playing: Boolean = false, val error: String? = null)

/**
 * Buffer de rede do libVLC no Android. 300 ms fez o Moto G9 Play (H.265 por software) entrar em espiral:
 * "more than 5 seconds of late video -> dropping frame". 800 ms dá folga; custa ~0,5 s de latência.
 */
const val LIVE_NETWORK_CACHING_MS = 800
