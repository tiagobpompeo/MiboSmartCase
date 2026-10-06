package br.com.pompeo.casa.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Android: libVLC para RTSP (o que a GDI real devolve) e também HTTP(S), se a API um dia seguir o Swagger. */
@Composable
actual fun CameraPlayer(url: String, muted: Boolean, modifier: Modifier, onProgress: (PlayerProgress) -> Unit) {
    val context = LocalContext.current.applicationContext // LibVLC não deve segurar a Activity
    val currentOnProgress by rememberUpdatedState(onProgress) // o listener nativo sempre chama a lambda mais recente
    var restart by remember(url) { mutableIntStateOf(0) }
    var layoutReady by remember { mutableStateOf(false) }

    // "--rtsp-tcp": RTP dentro da conexão TCP do RTSP (atravessa NAT/firewall, sem perda de UDP).
    val libVlc = remember { LibVLC(context, arrayListOf("--rtsp-tcp", "--network-caching=$LIVE_NETWORK_CACHING_MS")) }
    val player = remember(libVlc) { MediaPlayer(libVlc) }
    // Depois do 1.º quadro, rebuffering do libVLC (reset de PCR) não pode desligar o AO VIVO (igual ao iOS).
    val firstFrameShown = remember(player) { AtomicBoolean(false) }
    // stop()/release()/setMedia() do libVLC são síncronos e, com a leitura RTSP travada, esperam o timeout:
    // na main thread isso deu ANR. Uma thread única tira essas chamadas da main e preserva a ordem stop → (re)play.
    val vlcControl = remember(player) { Executors.newSingleThreadExecutor { r -> Thread(r, "vlc-control") } }
    DisposableEffect(player) {
        // Eventos do libVLC viram a porcentagem da tela (30–100 %).
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> currentOnProgress(PlayerProgress(35, "Conectando ao stream"))
                MediaPlayer.Event.Buffering ->
                    if (!firstFrameShown.get() && event.buffering < 100f) {
                        currentOnProgress(PlayerProgress(40 + (event.buffering * 0.5f).toInt(), "Carregando vídeo"))
                    }
                MediaPlayer.Event.Playing ->
                    if (!firstFrameShown.get()) currentOnProgress(PlayerProgress(92, "Decodificando o primeiro quadro"))
                // Vout = a saída de vídeo recebeu a primeira imagem: é o "primeiro quadro na tela".
                MediaPlayer.Event.Vout -> if (event.voutCount > 0) {
                    firstFrameShown.set(true)
                    currentOnProgress(PlayerProgress(100, "Ao vivo", playing = true))
                }
                MediaPlayer.Event.EncounteredError -> currentOnProgress(PlayerProgress(0, "", error = "Falha ao reproduzir o stream"))
                MediaPlayer.Event.EndReached -> currentOnProgress(PlayerProgress(0, "", error = "Transmissão encerrada"))
            }
        }
        onDispose {
            player.setEventListener(null) // nada chega a uma composição morta
            player.detachViews() // mexe em View: main thread
            vlcControl.post {
                player.stop()
                player.release()
                libVlc.release()
            }
            vlcControl.shutdown() // a fila ainda termina: um stop() pendente roda antes do release()
        }
    }

    // Só inicia depois que a superfície de vídeo existe (attachViews no factory).
    // layoutReady não é chave: a virada false→true relançaria o efeito e abriria o RTSP duas vezes.
    LaunchedEffect(url, restart) {
        snapshotFlow { layoutReady }.first { it }
        firstFrameShown.set(false) // nova conexão: o próximo Vout religa o AO VIVO
        currentOnProgress(PlayerProgress(32, "Iniciando player"))
        // A Media nasce dentro da tarefa: se ela não rodar (shutdown), nada nativo fica sem release.
        vlcControl.post {
            val media = Media(libVlc, Uri.parse(url)).apply {
                // Sem "fmtp" no SDP o decoder de HARDWARE não conhece a resolução e falha ("Set Resolution failed");
                // o de software lê VPS/SPS/PPS do próprio fluxo. (enabled = false, force = false)
                setHWDecoderEnabled(false, false)
                // O áudio AAC também vem sem "config" no SDP e decodifica como ruído: desativado.
                addOption(":no-audio")
                addOption(":network-caching=$LIVE_NETWORK_CACHING_MS") // opção por mídia usa ":"; global usa "--"
            }
            player.media = media
            media.release() // Media é nativo com contagem de referência; o player guarda a sua
            player.play()
        }
    }
    LaunchedEffect(player, muted) { vlcControl.post { player.volume = if (muted) 0 else 100 } }

    // Ao vivo não "pausa": em background para o stream e reconecta ao voltar (poupa banda e cota).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        // addObserver reenvia ON_START na hora (a tela já está ativa): só reconecta depois de um ON_STOP real.
        var stoppedInBackground = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    vlcControl.post { player.stop() }
                    stoppedInBackground = true
                }
                Lifecycle.Event.ON_START -> if (stoppedInBackground) {
                    stoppedInBackground = false
                    restart++
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        // (layout, displayManager = null, subtitles = false, textureView = false)
        factory = { ctx -> VLCVideoLayout(ctx).also { player.attachViews(it, null, false, false); layoutReady = true } },
        modifier = modifier,
    )
}

/**
 * Depois do shutdown (onDispose), execute lançaria RejectedExecutionException: a tarefa é descartada.
 * Quem posta (efeitos, observer do ciclo de vida, onDispose) roda na main: sem corrida entre teste e execute.
 */
private fun ExecutorService.post(task: Runnable) {
    if (!isShutdown) execute(task)
}
