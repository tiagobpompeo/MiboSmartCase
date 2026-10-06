package br.com.pompeo.casa.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.platform.WeekDay
import br.com.pompeo.casa.platform.todayDayMonth
import br.com.pompeo.casa.platform.todayMonthDay
import br.com.pompeo.casa.platform.weekStrip
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Explica por que o comando pela nuvem está bloqueado. */
const val REMOTE_HINT = "Habilite a abertura remota no app Mibo Smart para comandar pela nuvem."

/** Segurar 600 ms (RF05): mais longo que o padrão da plataforma, para a porta não abrir por um toque demorado. */
private const val LOCK_LONG_PRESS_MS = 600L
/** Abaixo disso a bateria fica âmbar, como o "26%" de fechadura-mfr2030.jpg. */
private const val LOW_BATTERY_PERCENT = 30
/** Onde o degradê verde-claro do topo da fechadura termina no fundo da página (seção 6.6). */
private const val LOCK_GRADIENT_END = 0.45f
/** Altura do cabeçalho cinza do hub medida em hub-mca1002.jpg. */
private const val HUB_HEADER_FRACTION = 0.42f
/** Botão central do hub grande: 44 dp num disco de 200 dp. */
private const val HUB_CENTER_FRACTION = 44f / 200f
private const val TEXT_PLACEHOLDER = "—"

private val HubRingLarge = Color(0xFFE3E3E3)
private val DatePillBg = Color(0xFFF1F3F4)
private val EmptyBubble = Color(0xFFE0E0E0)
private val InactiveIcon = Color(0xFFBDBDBD)

@Composable
fun DeviceScreen(vm: HomeViewModel, ns: String, onBack: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val device = devices.firstOrNull { it.ns == ns }
    if (device == null) { LaunchedEffect(Unit) { onBack() }; return }
    val snack = rememberSnack()
    var info by remember { mutableStateOf<List<Pair<String, String>>?>(null) } // null = diálogo fechado
    Scaffold(
        containerColor = MiboColors.PageBg,
        snackbarHost = { SnackbarHost(snack.host) },
        // Os cabeçalhos coloridos (degradê/cinza) sobem até a status bar; cada tela aplica statusBarsPadding.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (device.kind) {
                DeviceKind.LOCK -> LockScreen(vm, device, snack, onBack, onInfo = { info = it })
                DeviceKind.HUB -> HubScreen(vm, device, children = devices.filter { it.parentNs == device.ns }, onBack, onInfo = { info = emptyList() })
                else -> OtherDeviceScreen(vm, device, onBack, onInfo = { info = emptyList() })
            }
        }
    }
    info?.let { extra -> DeviceInfoDialog(device, vm.providerName(device) ?: device.providerId, extra = extra, onDismiss = { info = null }) }
}

/** Cabeçalho das telas de detalhe. Aplica statusBarsPadding porque o Scaffold da DeviceScreen não reserva o topo. */
@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    titleSize: TextUnit,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            color = MiboColors.TextPrimary,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Settings, contentDescription = "Informações do dispositivo", tint = MiboColors.TextPrimary)
    }
}

/** Online relido ao abrir a tela: a listagem pode ter minutos; se a leitura falhar, vale o valor da lista. */
@Composable
private fun rememberOnline(vm: HomeViewModel, device: Device): Boolean? {
    var online by remember(device.ns) { mutableStateOf(device.online) }
    LaunchedEffect(device.ns) { vm.isOnline(device).onSuccess { online = it ?: online } }
    return online
}

// ---------------------------------------------------------------- Fechadura (RF05, RF06, RF09)

@Composable
private fun LockScreen(vm: HomeViewModel, lock: Device, snack: Snack, onBack: () -> Unit, onInfo: (List<Pair<String, String>>) -> Unit) {
    val scope = rememberCoroutineScope()
    var details by remember(lock.ns) { mutableStateOf<LockDetails?>(null) }
    var error by remember(lock.ns) { mutableStateOf<String?>(null) }
    var loading by remember(lock.ns) { mutableStateOf(true) }
    var reload by remember(lock.ns) { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Boolean?>(null) } // true = destrancar, false = trancar
    var sending by remember { mutableStateOf(false) }
    var online by remember(lock.ns) { mutableStateOf(lock.online) }
    // Volume e histórico atualizam só o próprio campo, sem repetir as outras leituras.
    val update: ((LockDetails) -> LockDetails) -> Unit = { f -> details = f(details ?: LockDetails(null, null, null, null, null, emptyList())) }
    var volumeOpen by rememberSaveable { mutableStateOf(false) }
    var historyTop by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()

    LaunchedEffect(lock.ns, reload) {
        loading = true
        vm.lockDetails(lock).onSuccess { details = it; error = null }.onFailure { error = vm.messageOf(it) }
        loading = false
    }
    LaunchedEffect(lock.ns) { vm.isOnline(lock).onSuccess { online = it ?: online } }

    val open = details?.open
    val settled = details != null || !loading // campo que falhou vira texto, não sumiço
    val remote = details?.remoteEnabled == true
    val stateText = when (open) { true -> "Porta aberta"; false -> "Porta fechada"; null -> if (settled) "Estado indisponível" else "Consultando…" }
    val subtitle = when {
        !settled -> null
        !remote -> REMOTE_HINT
        open == true -> "Pressione e segure para trancar a porta."
        open == false -> "Pressione e segure para abrir a porta."
        else -> null
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to MiboColors.LockHeaderTop, LOCK_GRADIENT_END to MiboColors.PageBg, 1f to MiboColors.PageBg))) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            ScreenHeader(lock.name, onBack, titleSize = 22.sp) {
                IconButton(onClick = { snack(GDI_UNAVAILABLE) }) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MiboColors.TextPrimary)
                }
                InfoButton { onInfo(lockInfoRows(details)) }
            }
            LockStatusRow(details?.battery, online)
            Spacer(Modifier.height(28.dp))
            LockCircle(
                open = open,
                consulting = loading || sending,
                enabled = !sending,
                onTap = { snack(subtitle ?: stateText) },
                onLongPress = {
                    when {
                        !settled -> snack("Consultando…")
                        !remote -> snack(REMOTE_HINT)
                        open == null -> snack("Estado indisponível")
                        else -> confirm = !open
                    }
                },
            )
            Spacer(Modifier.height(28.dp))
            Text(stateText, color = MiboColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MiboColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            error?.let {
                Text(it, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (error != null && !loading && !sending) TextButton(onClick = { reload++ }) { Text("Tentar de novo") }
            if (sending) {
                LinearProgressIndicator(Modifier.padding(top = 8.dp).width(160.dp), color = MiboColors.Green, trackColor = MiboColors.Divider)
            }
            Spacer(Modifier.height(24.dp))
            LockActions(
                volumeOpen = volumeOpen,
                onVolume = { volumeOpen = !volumeOpen },
                onHistory = { scope.launch { scroll.animateScrollTo(historyTop) } },
            ) { VolumeControls(vm, lock, details, snack, onUpdate = update) }
            Spacer(Modifier.height(16.dp))
            HistoryCard(
                vm, lock, details, loading, generation = reload, onUpdate = update,
                // Posição no conteúdo rolável: o botão "Histórico" rola até aqui.
                modifier = Modifier.onGloballyPositioned { historyTop = it.positionInParent().y.roundToInt() },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    confirm?.let { wantOpen ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = MiboColors.Card,
            title = { Text(if (wantOpen) "Destrancar a fechadura?" else "Trancar a fechadura?") },
            text = { Text(commandNotice(lock)) },
            confirmButton = { TextButton(onClick = {
                confirm = null; sending = true
                scope.launch {
                    vm.setLock(lock, wantOpen).onSuccess { error = null }.onFailure {
                        val message = "Comando falhou: ${vm.messageOf(it)}"
                        error = message
                        // A releitura logo abaixo limpa `error` quando dá certo; o Snackbar mantém a falha visível.
                        snack(message)
                    }
                    sending = false
                    reload++ // relê o estado REAL depois do comando
                }
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

/** Texto dos diálogos de confirmação: deixa claro que a ação chega ao aparelho real pela nuvem. */
private fun commandNotice(lock: Device): String = "O comando será enviado para ${lock.name} pela nuvem Intelbras."

/** Linhas extras do diálogo de informações; campo que a API não respondeu aparece como "—". */
private fun lockInfoRows(details: LockDetails?): List<Pair<String, String>> =
    listOf(
        "Bateria" to (details?.battery?.let { "$it%" } ?: TEXT_PLACEHOLDER),
        "Abertura remota" to when (details?.remoteEnabled) { true -> "Habilitada"; false -> "Desabilitada"; null -> TEXT_PLACEHOLDER },
    )

/** A GDI só informa online/offline: no lugar do "Excelente" do Mibo aparece [onlineLabel]. */
@Composable
private fun LockStatusRow(battery: Int?, online: Boolean?) {
    val low = battery != null && battery < LOW_BATTERY_PERCENT
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.BatteryStd, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (low) MiboColors.Amber else MiboColors.TextPrimary)
        Text(battery?.let { "$it%" } ?: "Bateria $TEXT_PLACEHOLDER", Modifier.padding(start = 6.dp), color = MiboColors.TextPrimary, fontSize = 15.sp)
        Text("|", Modifier.padding(horizontal = 12.dp), color = MiboColors.TextSecondary, fontSize = 15.sp)
        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(20.dp), tint = MiboColors.TextPrimary)
        Text(onlineLabel(online), Modifier.padding(start = 6.dp), color = MiboColors.TextPrimary, fontSize = 15.sp)
    }
}

/** Círculo grande da fechadura. Segurar 600 ms abre a confirmação: o padrão da plataforma é ~400–500 ms,
 *  por isso uma ViewConfiguration própria SÓ neste nó (delegação de interface com `by`). */
@Composable
private fun LockCircle(open: Boolean?, consulting: Boolean, enabled: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val base = LocalViewConfiguration.current
    val longPress = remember(base) { object : ViewConfiguration by base { override val longPressTimeoutMillis: Long get() = LOCK_LONG_PRESS_MS } }
    CompositionLocalProvider(LocalViewConfiguration provides longPress) {
        Box(
            Modifier.size(280.dp).clip(CircleShape).background(MiboColors.LockCircle)
                .border(14.dp, MiboColors.LockRing, CircleShape)
                .combinedClickable(enabled = enabled, onLongClick = onLongPress, onClick = onTap)
                .semantics {
                    role = Role.Button
                    contentDescription = if (open == true) "Pressione e segure para trancar a porta" else "Pressione e segure para abrir a porta"
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                consulting -> CircularProgressIndicator(Modifier.size(56.dp), color = MiboColors.Green, strokeWidth = 4.dp)
                open == true -> Icon(Icons.Default.LockOpen, null, Modifier.size(72.dp), tint = MiboColors.Green)
                else -> Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = Color.Black)
            }
        }
    }
}

/** Volume (RF06) e Histórico (RF09) no lugar de "Gerenciamento de usuários"/"Senhas temporárias", fora do case. */
@Composable
private fun LockActions(volumeOpen: Boolean, onVolume: () -> Unit, onHistory: () -> Unit, volumeControls: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = MiboColors.Card) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionCircle(Icons.AutoMirrored.Filled.VolumeUp, "Volume", MiboColors.ActionGreen, onVolume)
                ActionCircle(Icons.Default.History, "Histórico", MiboColors.ActionBlue, onHistory)
            }
            AnimatedVisibility(visible = volumeOpen) { volumeControls() }
        }
    }
}

@Composable
private fun ActionCircle(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = MiboColors.TextPrimary, fontSize = 14.sp)
    }
}

/**
 * Cartão Volume (RF06). A leitura da fechadura de teste responde HTTP 500, por isso os níveis ficam habilitados
 * mesmo sem leitura; mudar o volume age no aparelho real e passa por confirmação (regra 0.2.5).
 */
@Composable
private fun VolumeControls(vm: HomeViewModel, lock: Device, details: LockDetails?, snack: Snack, onUpdate: ((LockDetails) -> LockDetails) -> Unit) {
    val scope = rememberCoroutineScope()
    var changing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<LockVolume?>(null) } // nível aguardando confirmação

    // O ViewModel chama o ChangeLockVolumeUseCase: grava, relê e, se a releitura falhar, assume o pedido.
    fun change(level: LockVolume) {
        changing = true
        scope.launch {
            vm.setLockVolume(lock, level)
                .onSuccess { applied ->
                    onUpdate { it.copy(volume = applied, volumeError = null) }
                    snack("Volume alterado para ${applied.label}")
                }
                .onFailure { snack("Não foi possível alterar o volume: ${vm.messageOf(it)}") }
            changing = false
        }
    }

    fun reread() {
        changing = true
        scope.launch {
            vm.lockVolume(lock)
                .onSuccess { volume -> onUpdate { it.copy(volume = volume, volumeError = null) } }
                .onFailure { e -> onUpdate { it.copy(volume = null, volumeError = vm.shortMessageOf(e)) } }
            changing = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (details != null && details.volume == null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    details.volumeError?.let { "Volume indisponível ($it)" } ?: "Volume indisponível",
                    Modifier.weight(1f),
                    color = MiboColors.TextSecondary,
                    fontSize = 13.sp,
                )
                TextButton(onClick = { reread() }, enabled = !changing) { Text("Tentar de novo") }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LockVolume.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = details?.volume == level,
                    onClick = { if (details?.volume != level) pending = level },
                    shape = SegmentedButtonDefaults.itemShape(index, LockVolume.entries.size),
                    enabled = details != null && !changing,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MiboColors.Green,
                        activeContentColor = Color.White,
                        activeBorderColor = MiboColors.Divider,
                        inactiveContainerColor = MiboColors.Card,
                        inactiveContentColor = MiboColors.TextPrimary,
                        inactiveBorderColor = MiboColors.Divider,
                    ),
                ) { Text(level.label) }
            }
        }
        if (changing) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = MiboColors.Green, trackColor = MiboColors.Divider)
        }
    }

    pending?.let { level ->
        AlertDialog(
            onDismissRequest = { pending = null }, containerColor = MiboColors.Card,
            title = { Text("Alterar o volume para ${level.label}?") },
            text = { Text(commandNotice(lock)) },
            confirmButton = { TextButton(onClick = { pending = null; change(level) }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

/**
 * Histórico de abertura (RF09). A GDI pagina só por quantidade (10, depois 30), por isso a pílula mostra só a data
 * de hoje, sem as setas de dia do Mibo. [generation] muda a cada releitura completa, que volta a trazer só os 10
 * primeiros: o cartão volta a oferecer "Ver mais".
 */
@Composable
private fun HistoryCard(
    vm: HomeViewModel,
    lock: Device,
    details: LockDetails?,
    loading: Boolean,
    generation: Int,
    onUpdate: ((LockDetails) -> LockDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var fetching by remember { mutableStateOf(false) }
    var lastMore by remember { mutableStateOf(false) }
    var expanded by remember(generation) { mutableStateOf(false) }
    val today = remember { todayDayMonth() }

    fun fetch(more: Boolean) {
        if (fetching) return
        fetching = true
        lastMore = more
        scope.launch {
            vm.lockHistory(lock, more)
                .onSuccess { events ->
                    onUpdate { it.copy(history = events, historyError = null) }
                    expanded = more
                }
                .onFailure { e -> onUpdate { it.copy(historyError = vm.messageOf(e)) } }
            fetching = false
        }
    }

    val history = details?.history.orEmpty()
    val historyError = details?.historyError
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = MiboColors.Card) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !fetching, role = Role.Button) { fetch(more = !expanded) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Histórico de abertura", color = MiboColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Ver menos" else "Ver mais",
                            tint = MiboColors.TextPrimary,
                        )
                    }
                }
                if (fetching) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), color = MiboColors.Green, strokeWidth = 2.dp)
                }
                Text(
                    today,
                    Modifier.background(DatePillBg, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MiboColors.TextPrimary,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                details == null && loading -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), color = MiboColors.Green, strokeWidth = 3.dp)
                }
                historyError != null -> HistoryProblem("Histórico indisponível: $historyError", enabled = !fetching) { fetch(lastMore) }
                details == null -> HistoryProblem("Histórico indisponível", enabled = !fetching) { fetch(more = false) }
                history.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(72.dp), tint = EmptyBubble)
                    Spacer(Modifier.height(12.dp))
                    Text("Ainda não há registros de abertura", color = MiboColors.TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
                else -> {
                    history.forEach { HistoryRow(it) }
                    if (history.size >= HomeViewModel.HISTORY_FIRST && !expanded) {
                        OutlinedButton(onClick = { fetch(more = true) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !fetching) {
                            Text("Ver mais", color = MiboColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryProblem(message: String, enabled: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(message, color = MiboColors.TextSecondary, fontSize = 14.sp)
        TextButton(onClick = onRetry, enabled = enabled) { Text("Tentar de novo") }
    }
}

/** Evento já formatado pelo parceiro (descrição traduzida e "dd/MM/aaaa HH:mm:ss"): a UI não conhece a GDI. */
@Composable
private fun HistoryRow(event: LockEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(24.dp), tint = MiboColors.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(event.description, color = MiboColors.TextPrimary, fontSize = 16.sp)
            Text(event.time, color = MiboColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- Hub

/** "Dispositivos"/"Firmware" no lugar de "Modo Desarmar"/"Sirene silenciada", que não existem na GDI. */
@Composable
private fun HubScreen(vm: HomeViewModel, hub: Device, children: List<Device>, onBack: () -> Unit, onInfo: () -> Unit) {
    var showMessages by rememberSaveable { mutableStateOf(false) }
    var firmwareOpen by remember { mutableStateOf(false) }
    val online = rememberOnline(vm, hub)
    val days = remember { weekStrip() }
    val today = remember { todayMonthDay() }

    Column(Modifier.fillMaxSize()) {
        HubHeader(
            hub, online, onBack, onInfo,
            onDevices = { showMessages = false },
            onFirmware = { firmwareOpen = true },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(HUB_HEADER_FRACTION),
        )
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 16.dp)) {
            HubTab("Acessório", selected = !showMessages) { showMessages = false }
            HubTab("Mensagens", selected = showMessages) { showMessages = true }
            Spacer(Modifier.weight(1f))
            Text(today, Modifier.padding(top = 10.dp), color = MiboColors.TextSecondary, fontSize = 13.sp)
        }
        WeekStrip(days)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (showMessages) HubMessages() else HubAccessories(hub, children)
        }
    }
    if (firmwareOpen) FirmwareDialog(vm, hub) { firmwareOpen = false }
}

@Composable
private fun HubHeader(
    hub: Device,
    online: Boolean?,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onDevices: () -> Unit,
    onFirmware: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MiboColors.HubHeader, RoundedCornerShape(bottomStart = 40.dp))) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(hub.name, onBack, titleSize = 20.sp, centered = true) { InfoButton(onInfo) }
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        hub.name,
                        color = MiboColors.TextPrimary,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(24.dp), tint = MiboColors.TextPrimary)
                    Text(onlineLabel(online), color = MiboColors.TextPrimary, fontSize = 14.sp)
                }
                HubDrawing(
                    Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp).fillMaxHeight().aspectRatio(1f),
                    ring = HubRingLarge,
                    ringWidth = 3.dp,
                    centerFraction = HUB_CENTER_FRACTION,
                    shadow = 4.dp,
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(Icons.Default.Devices, "Dispositivos", onDevices)
                RoundAction(Icons.Default.SystemUpdate, "Firmware", onFirmware)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(88.dp), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
            // Box próprio: o conteúdo do Surface herda o tamanho mínimo (88 dp) e esticaria o ícone.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp), tint = MiboColors.Green)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = MiboColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun HubTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) MiboColors.TextPrimary else MiboColors.TextSecondary,
            fontSize = 18.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(32.dp, 3.dp).background(if (selected) MiboColors.Green else Color.Transparent, RoundedCornerShape(2.dp)))
    }
}

/** Faixa de hoje−6 até hoje+1, como no Mibo; hoje num quadrado verde. */
@Composable
private fun WeekStrip(days: List<WeekDay>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        days.forEach { day ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.size(48.dp).background(if (day.isToday) MiboColors.Green else Color.Transparent, RoundedCornerShape(8.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(day.initial, color = if (day.isToday) Color.White else MiboColors.TextSecondary, fontSize = 12.sp, lineHeight = 14.sp)
                    Text(day.day, color = if (day.isToday) Color.White else MiboColors.TextPrimary, fontSize = 18.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun HubAccessories(hub: Device, children: List<Device>) {
    Text("Dispositivos conectados ao hub (${children.size})", color = MiboColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    if (children.isEmpty()) {
        Text("Nenhum subdispositivo", Modifier.padding(bottom = 12.dp), color = MiboColors.TextSecondary, fontSize = 14.sp)
    }
    children.forEach { child ->
        ChildRow(child)
        Spacer(Modifier.height(8.dp))
    }
    FirmwareRow(hub.version, hub.updateAvailable)
}

@Composable
private fun ChildRow(child: Device) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MiboColors.Card) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                kindIcon(child.kind),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (child.online == true) MiboColors.Green else InactiveIcon,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(child.name, color = MiboColors.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(child.model ?: child.kind.label, onlineLabel(child.online).lowercase()).joinToString(" • "),
                    color = MiboColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** A GDI não entrega eventos por consulta (só por webhook, que exige backend): a aba diz isso em vez de simular. */
@Composable
private fun HubMessages() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(96.dp), tint = EmptyBubble)
        Spacer(Modifier.height(16.dp))
        Text("Sem eventos", color = MiboColors.TextSecondary, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text("Eventos em tempo real chegam por webhook e exigem backend", color = MiboColors.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Consulta o firmware na hora (a listagem pode estar velha) e mostra o resultado ou o erro amigável. */
@Composable
private fun FirmwareDialog(vm: HomeViewModel, device: Device, onDismiss: () -> Unit) {
    val result by produceState<Result<Firmware>?>(initialValue = null, device.ns) { value = vm.firmware(device) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MiboColors.Card,
        title = { Text("Firmware") },
        text = {
            val current = result
            if (current == null) {
                CircularProgressIndicator(Modifier.size(32.dp), color = MiboColors.Green, strokeWidth = 3.dp)
            } else {
                current.fold(
                    onSuccess = { firmware ->
                        Column {
                            Text("Versão ${firmware.version ?: TEXT_PLACEHOLDER}", color = MiboColors.TextPrimary)
                            firmwareStatus(firmware.updateAvailable)?.let { Text(it, color = firmwareStatusColor(firmware.updateAvailable)) }
                        }
                    },
                    onFailure = { Text("Não foi possível consultar o firmware: ${vm.messageOf(it)}") },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
    )
}

/** null = a API não informou: nada de afirmar "Atualizado" sem dado. */
private fun firmwareStatus(updateAvailable: Boolean?): String? =
    when (updateAvailable) { true -> "Atualização disponível"; false -> "Atualizado"; null -> null }

private fun firmwareStatusColor(updateAvailable: Boolean?): Color =
    if (updateAvailable == true) MiboColors.Amber else MiboColors.TextSecondary

@Composable
private fun FirmwareRow(version: String?, updateAvailable: Boolean?, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MiboColors.Card) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(28.dp), tint = MiboColors.TextSecondary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Firmware ${version ?: TEXT_PLACEHOLDER}", color = MiboColors.TextPrimary, fontSize = 16.sp)
                firmwareStatus(updateAvailable)?.let { Text(it, color = firmwareStatusColor(updateAvailable), fontSize = 12.sp) }
            }
        }
    }
}

// ---------------------------------------------------------------- Outros dispositivos (sensores, lâmpadas…)

/** Sem captura de referência: segue o tema claro (fundo PageBg, cartões brancos, verde nos destaques). */
@Composable
private fun OtherDeviceScreen(vm: HomeViewModel, device: Device, onBack: () -> Unit, onInfo: () -> Unit) {
    val online = rememberOnline(vm, device)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(device.name, onBack, titleSize = 20.sp) { InfoButton(onInfo) }
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), color = MiboColors.Card) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    kindIcon(device.kind),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = if (online == true) MiboColors.Green else InactiveIcon,
                )
                Spacer(Modifier.height(12.dp))
                Text(device.kind.label, color = MiboColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(onlineLabel(online), color = MiboColors.TextSecondary, fontSize = 14.sp)
            }
        }
        FirmwareRow(device.version, device.updateAvailable, Modifier.padding(horizontal = 16.dp))
    }
}
