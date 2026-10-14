package br.com.pompeo.casa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.player.CameraPlayer
import br.com.pompeo.casa.player.PlayerProgress
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/** Fato da API: criar-fluxo-video devolve o mesmo RTSP para canalVideo 0 e 1 na iM4 Dual. */
const val SAME_STREAM_NOTE = "A API GDI devolve o mesmo vídeo para as duas lentes desta câmera."

/** 3140 -> "3,1 s" (formato brasileiro sem String.format, que não existe no commonMain). Trunca. */
internal fun formatSeconds(ms: Long): String = "${ms / 1000},${(ms % 1000) / 100} s"

private const val MULTIVIEW_UNAVAILABLE = "Multiview indisponível na API GDI"
private const val PTZ_UNAVAILABLE = "PTZ não disponível na API GDI"
private const val STREAM_INFO_LABEL = "Fluxo"
/** Sem session_id a GDI não tem sessão a encerrar: a URL RTSP vence sozinha (parâmetro "expire"). */
private const val CLOUD_STREAM_INFO = "RTSP da nuvem • expira automaticamente"
/** Um player por vez (cota e CPU de aparelho fraco): o contador do Mibo fica sempre em 1 de 1. */
private const val SINGLE_VIEW_BADGE = "1/1"
private const val LIVE_LABEL = "AO VIVO"
private const val DUAL_LENS_COUNT = 2
/** canalVideo 1 = lente fixa, a que a GDI não separa da móvel. */
private const val FIXED_LENS_CHANNEL = 1
private const val VIDEO_ASPECT_RATIO = 16f / 9f
/** Pílula PTZ esmaecida enquanto o vídeo não toca, como em camera-carregando.jpg. */
private const val DIMMED_ALPHA = 0.5f
private const val BADGE_ALPHA = 0.7f
private const val LIVE_CHIP_ALPHA = 0.5f
private const val FIRST_FRAME_ALPHA = 0.6f

private val PtzCloseBg = Color(0xFF3A3A3A)
private val ToolbarIconSize = 28.dp

// Margens do aviso de erro: abaixo do selo "1/1" e acima dos botões Multiview/Tela cheia.
private val ErrorOverlayTopInset = 52.dp
private val ErrorOverlayBottomInset = 56.dp
private const val ERROR_MAX_LINES = 3

@Composable
fun CameraScreen(vm: HomeViewModel, ns: String, onBack: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val camera = devices.firstOrNull { it.ns == ns && it.isCamera }
    if (camera == null) {
        // Câmera saiu da lista (logout/refresh): volta num efeito, não durante a composição.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var muted by rememberSaveable { mutableStateOf(true) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var ptzOpen by rememberSaveable { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val snack = rememberSnack()
    var channel by rememberSaveable(camera.ns) { mutableIntStateOf(0) } // lente; trocar reinicia a sessão
    val lenses = lensCount(camera.model, camera.name)

    var session by remember(camera.ns) { mutableStateOf<StreamSession?>(null) }
    var liveError by remember(camera.ns) { mutableStateOf<String?>(null) }
    var liveAttempt by remember(camera.ns) { mutableIntStateOf(0) }
    // Métrica de desempenho: do toque (ou retry/troca de lente) até o 1.º quadro, incluindo a GDI. Relógio monotônico.
    var startedAt by remember(camera.ns, liveAttempt) { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var progress by remember(camera.ns, liveAttempt) { mutableStateOf(PlayerProgress(0, "Solicitando sessão à Intelbras")) }
    var firstFrameMs by remember(camera.ns, liveAttempt) { mutableStateOf<Long?>(null) }

    LaunchedEffect(camera.ns, liveAttempt, channel) {
        liveError = null
        // Trocar de lente ou "Tentar novamente" substitui a sessão: a anterior é encerrada, não abandonada.
        session?.let(vm::stopLive)
        session = null
        vm.startLive(camera, channel).onSuccess { session = it }.onFailure { liveError = vm.messageOf(it) }
    }
    // A GDI não informa progresso: estimamos 0–29 % pelo tempo típico da chamada (~2,4 s medidos).
    LaunchedEffect(camera.ns, liveAttempt) {
        while (session == null && liveError == null) {
            val ms = startedAt.elapsedNow().inWholeMilliseconds
            progress = PlayerProgress(minOf(29, (ms * 30 / 2400).toInt()), "Solicitando sessão à Intelbras")
            delay(100)
        }
    }
    // Saída da tela: stopLive roda no viewModelScope (o escopo desta tela já está cancelado aqui).
    DisposableEffect(camera.ns) { onDispose { session?.let(vm::stopLive) } }

    val onProgress: (PlayerProgress) -> Unit = { p ->
        progress = p.copy(percent = maxOf(progress.percent, p.percent)) // nunca regride
        if (p.playing && firstFrameMs == null) firstFrameMs = startedAt.elapsedNow().inWholeMilliseconds
    }

    /** Um player por vez: encerra a sessão ANTES de mudar o canal, para o efeito novo não encerrá-la de novo. */
    fun switchLens(lens: Int) {
        if (lens == channel) return
        session?.let(vm::stopLive)
        session = null
        channel = lens
        liveAttempt++
    }

    val error = liveError ?: progress.error
    // Depois do 1.º quadro o overlay não volta: rebuffering breve não deve cobrir o vídeo.
    val loading = error == null && !progress.playing && firstFrameMs == null

    Scaffold(
        containerColor = if (fullscreen) Color.Black else Color.White,
        snackbarHost = { SnackbarHost(snack.host) },
        bottomBar = {
            if (!fullscreen) {
                CameraToolbar(
                    muted = muted,
                    live = firstFrameMs != null,
                    onToggleMute = { muted = !muted },
                    onUnavailable = { snack(GDI_UNAVAILABLE) },
                    onFullscreen = { fullscreen = true },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!fullscreen) {
                CameraHeader(
                    name = camera.name,
                    onBack = onBack,
                    onUnavailable = { snack(GDI_UNAVAILABLE) },
                    onInfo = { showInfo = true },
                )
                Spacer(Modifier.height(48.dp))
            }
            // O Box do vídeo é o MESMO nó nos dois modos (tela cheia só muda o Modifier), então alternar
            // não recria o player nem a sessão (armadilha 32). CameraPlayer só existe com session != null.
            val videoModifier =
                if (fullscreen) Modifier.fillMaxSize().background(Color.Black).clickable { fullscreen = false }
                else Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO).background(MiboColors.VideoBg)
            Box(videoModifier) {
                session?.let { CameraPlayer(it.streamUrl, muted, Modifier.fillMaxSize(), onProgress) }
                VideoTag(SINGLE_VIEW_BADGE, Modifier.align(Alignment.TopStart).padding(16.dp))
                when {
                    error != null -> ErrorOverlay(error, onRetry = { liveAttempt++ }, Modifier.align(Alignment.Center))
                    loading -> LoadingOverlay(progress, Modifier.align(Alignment.Center))
                }
                if (progress.playing) LiveChip(Modifier.align(Alignment.TopEnd).padding(16.dp))
                firstFrameMs?.let { ms -> FirstFrameLabel(ms, Modifier.align(Alignment.BottomStart).padding(12.dp)) }
                VideoActions(
                    fullscreen = fullscreen,
                    onMultiview = { snack(MULTIVIEW_UNAVAILABLE) },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
            if (!fullscreen) {
                if (lenses == DUAL_LENS_COUNT) {
                    LensSelector(channel, onSelect = { switchLens(it) })
                    if (channel == FIXED_LENS_CHANNEL) {
                        Text(
                            SAME_STREAM_NOTE,
                            Modifier.padding(horizontal = 16.dp),
                            color = MiboColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (ptzOpen) {
                    PtzPad(onArrow = { snack(PTZ_UNAVAILABLE) }, onClose = { ptzOpen = false })
                } else {
                    PtzPill(dimmed = !progress.playing, onOpen = { ptzOpen = true })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showInfo) {
        // A API real não devolve session_id: o fluxo é um RTSP do proxy da nuvem que vence sozinho.
        val extra = if (session?.sessionId == null) listOf(STREAM_INFO_LABEL to CLOUD_STREAM_INFO) else emptyList()
        DeviceInfoDialog(camera, vm.providerName(camera) ?: camera.providerId, extra = extra, onDismiss = { showInfo = false })
    }
}

@Composable
private fun CameraHeader(name: String, onBack: () -> Unit, onUnavailable: () -> Unit, onInfo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Text(
            name,
            Modifier.weight(1f),
            color = MiboColors.TextPrimary,
            fontSize = 20.sp,
            maxLines = 1,
            softWrap = false, // no iOS o nome quebrava no meio (armadilha 31)
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onUnavailable) { Icon(Icons.Default.AutoAwesome, "Assistente", tint = Color.Black) }
        IconButton(onClick = onUnavailable) { Icon(Icons.Default.Share, "Compartilhar", tint = Color.Black) }
        IconButton(onClick = onInfo) { Icon(Icons.Outlined.Settings, "Informações do dispositivo", tint = Color.Black) }
    }
}

@Composable
private fun VideoTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(Color.Black.copy(alpha = BADGE_ALPHA), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = Color.White,
        fontSize = 14.sp,
    )
}

@Composable
private fun LoadingOverlay(progress: PlayerProgress, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(40.dp), color = MiboColors.GreenSpinner)
        Spacer(Modifier.height(12.dp))
        Text("${progress.percent}%", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(progress.label, color = Color.LightGray, fontSize = 12.sp)
    }
}

/** Fica entre o selo e os botões do vídeo: em tela estreita (iPhone de 390 pt) a caixa cobria os dois. */
@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(start = 16.dp, end = 16.dp, top = ErrorOverlayTopInset, bottom = ErrorOverlayBottomInset)
            .background(Color.Black.copy(alpha = BADGE_ALPHA), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = ERROR_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Tentar novamente") }
    }
}

@Composable
private fun LiveChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = LIVE_CHIP_ALPHA), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Wifi, null, Modifier.size(14.dp), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(LIVE_LABEL, color = Color.White, fontSize = 12.sp)
    }
}

/** Métrica de desempenho no lugar do "0KB/s" do Mibo: a frio o proxy da nuvem leva ~21 s, a quente ~3 s. */
@Composable
private fun FirstFrameLabel(ms: Long, modifier: Modifier = Modifier) {
    Text(
        "1º quadro em ${formatSeconds(ms)}",
        modifier
            .background(Color.Black.copy(alpha = FIRST_FRAME_ALPHA), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = Color.White,
        fontSize = 11.sp,
    )
}

@Composable
private fun VideoActions(fullscreen: Boolean, onMultiview: () -> Unit, onToggleFullscreen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        IconButton(onClick = onMultiview) { Icon(Icons.Default.GridView, "Multiview", tint = Color.White) }
        IconButton(onClick = onToggleFullscreen) {
            if (fullscreen) Icon(Icons.Default.FullscreenExit, "Sair da tela cheia", tint = Color.White)
            else Icon(Icons.Default.Fullscreen, "Tela cheia", tint = Color.White)
        }
    }
}

@Composable
private fun LensSelector(channel: Int, onSelect: (Int) -> Unit) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MiboColors.Green,
        activeContentColor = Color.White,
        activeBorderColor = MiboColors.Divider,
        inactiveContainerColor = MiboColors.Card,
        inactiveContentColor = MiboColors.TextPrimary,
        inactiveBorderColor = MiboColors.Divider,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        LENS_LABELS.forEachIndexed { index, label ->
            SegmentedButton(
                selected = channel == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, LENS_LABELS.size),
                colors = colors,
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun PtzPill(dimmed: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier
            .size(90.dp, 60.dp)
            .alpha(if (dimmed) DIMMED_ALPHA else 1f)
            .clip(RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp))
            .background(MiboColors.GreenNav)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.ControlCamera, "Abrir controle PTZ", Modifier.size(28.dp), tint = Color.White)
    }
}

@Composable
private fun PtzPad(onArrow: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.padding(start = 24.dp).size(220.dp, 210.dp)) {
        Box(Modifier.align(Alignment.BottomStart).size(190.dp).background(MiboColors.GreenNav, CircleShape)) {
            PtzArrow(Icons.Default.KeyboardArrowUp, "Para cima", onArrow, Modifier.align(Alignment.TopCenter))
            PtzArrow(Icons.Default.KeyboardArrowDown, "Para baixo", onArrow, Modifier.align(Alignment.BottomCenter))
            PtzArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Para esquerda", onArrow, Modifier.align(Alignment.CenterStart))
            PtzArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Para direita", onArrow, Modifier.align(Alignment.CenterEnd))
            Box(Modifier.align(Alignment.Center).size(70.dp).background(Color.White, CircleShape))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(PtzCloseBg)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, "Fechar PTZ", Modifier.size(18.dp), tint = Color.White)
        }
    }
}

@Composable
private fun PtzArrow(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, description, Modifier.size(28.dp), tint = Color.White)
    }
}

/** Só o mudo é real (volume do player); gravar, falar e capturar não têm rota na GDI. */
@Composable
private fun CameraToolbar(
    muted: Boolean,
    live: Boolean,
    onToggleMute: () -> Unit,
    onUnavailable: () -> Unit,
    onFullscreen: () -> Unit,
) {
    // Cinza até o 1.º quadro, como no Mibo; a tela cheia já nasce preta (camera-carregando.jpg).
    val tint = if (live) Color.Black else MiboColors.TextSecondary
    Surface(color = Color.White) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolbarButton(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                "Alternar áudio",
                tint,
                onToggleMute,
            )
            ToolbarButton(Icons.Default.Videocam, "Gravar", tint, onUnavailable)
            ToolbarButton(Icons.Default.Mic, "Áudio bidirecional", tint, onUnavailable)
            ToolbarButton(Icons.Default.PhotoCamera, "Capturar", tint, onUnavailable)
            ToolbarButton(Icons.Default.Fullscreen, "Tela cheia", Color.Black, onFullscreen)
        }
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, description, Modifier.size(ToolbarIconSize), tint = tint)
    }
}
