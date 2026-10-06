package br.com.pompeo.casa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.platform.isDebugBuild

private const val HOME_TITLE = "Minha casa ⌄"
private const val ADD_DEVICE_UNAVAILABLE = "Adicionar dispositivos: use o app Mibo Smart (indisponível na API GDI)"
private const val SCENES_UNAVAILABLE = "Cenas e automações: indisponível na API GDI"
private const val STORAGE_PLANS_UNAVAILABLE = "Planos de armazenamento: indisponível nesta demo"
private const val VIA_HUB = "via hub"
private const val WEBHOOK_NOTE =
    "A GDI envia eventos de movimento e de abertura por webhook, que exige um backend; este app não recebe eventos em tempo real."

/** Miniatura 16:10 da câmera no cartão da grade, como em home.jpg. */
private const val CAMERA_THUMB_RATIO = 1.6f

private val SMART_SECTIONS = listOf("Cenas", "Automações")
private val MESSAGE_SECTIONS = listOf("Notificações da conta", "Novidades")
private val SETTINGS_OPTIONS = listOf("Conta", "Serviços", "Galeria", "Preciso de ajuda", "Configurações")

private val NavUnselected = Color(0xFF9A9A9A)
private val BannerTitle = Color(0xFF0B5A2A)
private val BannerPill = Color(0xFF1B2A22)
private val EmptyBubble = Color(0xFFE0E0E0)

/** Folga no fim da Home para o FAB não cobrir o rodapé de paginação. */
private val FabClearance = 88.dp

/** Abas da barra inferior, na ordem do app Mibo Smart. */
private enum class ShellTab(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    SMART("Inteligente", Icons.Default.Lightbulb),
    MESSAGES("Mensagens", Icons.Default.Email),
    STORE("Loja", Icons.Default.Store),
    SETTINGS("Definições", Icons.Default.Settings),
}

/** Casca com as 5 abas do Mibo Smart; a navegação para câmera/dispositivo/token sai por callbacks (o App decide a rota). */
@Composable
fun HomeShell(vm: HomeViewModel, onCamera: (Device) -> Unit, onDevice: (Device) -> Unit, onLogout: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val token by vm.token.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    // Na casca e não na aba: sair para Definições e voltar mantém grade ou lista.
    var grid by rememberSaveable { mutableStateOf(true) }
    val snack = rememberSnack()
    // A GDI pode renovar o token durante o uso; a máscara acompanha o valor atual.
    val maskedToken = remember(token) { vm.maskedToken() }

    LaunchedEffect(notice) {
        notice?.let { message ->
            snack(message)
            vm.noticeShown()
        }
    }

    Scaffold(
        containerColor = MiboColors.PageBg,
        snackbarHost = { SnackbarHost(snack.host) },
        bottomBar = { ShellBottomBar(tab) { tab = it } },
        floatingActionButton = { if (tab == ShellTab.HOME) AddDeviceFab { snack(ADD_DEVICE_UNAVAILABLE) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                ShellTab.HOME -> HomeTab(
                    vm = vm,
                    state = state,
                    origin = query.origin,
                    grid = grid,
                    onGridChange = { grid = it },
                    snack = snack,
                    onScenes = { tab = ShellTab.SMART },
                    onCamera = onCamera,
                    onDevice = onDevice,
                    onLogout = onLogout,
                )
                ShellTab.SMART -> SmartTab(snack)
                ShellTab.MESSAGES -> MessagesTab(snack)
                ShellTab.STORE -> StoreTab(snack)
                ShellTab.SETTINGS -> SettingsTab(vm, maskedToken, hasToken = token != null, pageSize = query.pageSize, onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun ShellBottomBar(selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    NavigationBar(containerColor = MiboColors.Card, tonalElevation = 0.dp) {
        ShellTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, fontSize = 11.sp) },
                // Transparente tira a pílula do Material 3: no Mibo só a cor marca a aba ativa.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MiboColors.GreenNav,
                    selectedTextColor = MiboColors.GreenNav,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = NavUnselected,
                    unselectedTextColor = NavUnselected,
                ),
            )
        }
    }
}

/** A GDI não cadastra dispositivos: o FAB existe pela fidelidade ao Mibo e avisa em vez de ficar mudo. */
@Composable
private fun AddDeviceFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = MiboColors.GreenBright,
        contentColor = Color.White,
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = "Adicionar dispositivo")
    }
}

@Composable
private fun HomeTab(
    vm: HomeViewModel,
    state: DevicesState,
    origin: OriginFilter,
    grid: Boolean,
    onGridChange: (Boolean) -> Unit,
    snack: Snack,
    onScenes: () -> Unit,
    onCamera: (Device) -> Unit,
    onDevice: (Device) -> Unit,
    onLogout: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        HomeHeader(
            onToggleSearch = {
                searching = !searching
                if (!searching) search = ""
            },
            onAdd = { snack(ADD_DEVICE_UNAVAILABLE) },
        )
        if (searching) SearchField(search) { search = it }
        StorageBanner { snack(STORAGE_PLANS_UNAVAILABLE) }
        Spacer(Modifier.height(16.dp))
        ScenesCard(onScenes)
        DevicesHeader(grid, onGridChange)
        OriginChips(origin) { if (it != origin) vm.setOrigin(it) }
        if ((state as? DevicesState.Content)?.refreshing == true) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(2.dp),
                color = MiboColors.Green,
                trackColor = MiboColors.Divider,
            )
        }
        Spacer(Modifier.height(16.dp))
        DevicesBody(vm, state, grid, search, onCamera, onDevice, onLogout)
        Spacer(Modifier.height(FabClearance))
    }
}

@Composable
private fun HomeHeader(onToggleSearch: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(HOME_TITLE, Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MiboColors.TextPrimary)
        IconButton(onClick = onToggleSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Pesquisar", tint = Color.Black)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MiboColors.GreenBright).clickable(role = Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar dispositivo", tint = Color.White)
        }
    }
}

/** Busca só nos dispositivos já carregados: a GDI não tem rota de busca por nome. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        label = { Text("Buscar pelo nome") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MiboColors.Card,
            unfocusedContainerColor = MiboColors.Card,
            focusedBorderColor = MiboColors.Green,
            unfocusedBorderColor = MiboColors.Divider,
            focusedLabelColor = MiboColors.Green,
            cursorColor = MiboColors.Green,
        ),
    )
}

/** Banner do Mibo desenhado (sem imagem de produto); planos de armazenamento não existem na GDI. */
@Composable
private fun StorageBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(150.dp),
        shape = RoundedCornerShape(12.dp),
        color = MiboColors.GreenBright,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Armazenamento Complementar por Fotos",
                Modifier.weight(1.2f),
                color = BannerTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Mais registros para sua câmera", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.End)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Contratar agora",
                    Modifier.background(BannerPill, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ScenesCard(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MiboColors.Card) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = MiboColors.Green)
            Spacer(Modifier.width(12.dp))
            Text("Selecione suas cenas preferidas", Modifier.weight(1f), color = MiboColors.TextSecondary, fontSize = 15.sp)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MiboColors.TextSecondary)
        }
    }
}

@Composable
private fun DevicesHeader(grid: Boolean, onGridChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Meus dispositivos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MiboColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(20.dp, 4.dp).background(MiboColors.Green, RoundedCornerShape(2.dp)))
        }
        IconButton(onClick = { onGridChange(true) }) {
            Icon(Icons.Default.GridView, contentDescription = "Ver em grade", tint = if (grid) MiboColors.Green else MiboColors.TextSecondary)
        }
        IconButton(onClick = { onGridChange(false) }) {
            Icon(
                Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "Ver em lista",
                tint = if (grid) MiboColors.TextSecondary else MiboColors.Green,
            )
        }
    }
}

/** Filtro de origem (RF07): rolagem horizontal porque "Compartilhados" com o check não cabe em telas de 360 dp. */
@Composable
private fun OriginChips(selected: OriginFilter, onSelect: (OriginFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OriginFilter.entries.forEach { filter ->
            val active = filter == selected
            FilterChip(
                selected = active,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                leadingIcon = if (active) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MiboColors.Card,
                    labelColor = MiboColors.TextPrimary,
                    selectedContainerColor = MiboColors.Green,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                ),
                border = BorderStroke(1.dp, if (active) MiboColors.Green else MiboColors.Divider),
            )
        }
    }
}

@Composable
private fun DevicesBody(
    vm: HomeViewModel,
    state: DevicesState,
    grid: Boolean,
    search: String,
    onCamera: (Device) -> Unit,
    onDevice: (Device) -> Unit,
    onLogout: () -> Unit,
) {
    when (state) {
        // Sem token o App volta para a tela de token; aqui não há o que desenhar.
        DevicesState.NoToken -> Unit
        DevicesState.Loading -> LoadingBlock()
        is DevicesState.Error -> ErrorCard(state.error, onRetry = { vm.refresh() }, onChangeToken = onLogout)
        is DevicesState.Empty -> EmptyCard(state.query.origin, onRefresh = { vm.refresh() })
        is DevicesState.Content -> {
            val visible = state.devices.filterByName(search)
            when {
                visible.isEmpty() && search.isNotBlank() -> Text(
                    "Nenhum dispositivo com esse nome",
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MiboColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                grid -> DeviceGrid(visible, onCamera, onDevice)
                else -> DeviceList(visible, onCamera, onDevice)
            }
            PaginationFooter(state) { vm.loadMore() }
        }
    }
}

private fun List<Device>.filterByName(search: String): List<Device> {
    val term = search.trim()
    return if (term.isEmpty()) this else filter { it.name.contains(term, ignoreCase = true) }
}

/** A altura de cada linha vem do cartão mais alto (nome + miniatura 16:10), para a fechadura ao lado da câmera ficar igual. */
@Composable
private fun DeviceGrid(devices: List<Device>, onCamera: (Device) -> Unit, onDevice: (Device) -> Unit) {
    devices.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { d ->
                if (d.isCamera) CameraCard(d, Modifier.weight(1f).fillMaxHeight()) { onCamera(d) }
                else DeviceCard(d, Modifier.weight(1f).fillMaxHeight()) { onDevice(d) }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Nome sem negrito, como em home.jpg (só fechadura e hub têm nome em negrito); a GDI não dá snapshot, a miniatura é desenhada. */
@Composable
private fun CameraCard(device: Device, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), color = MiboColors.Card) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // softWrap = false: no iOS o nome quebrava no meio em vez de virar reticências.
                    Text(
                        device.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = MiboColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    sharedLabel(device.origin)?.let { Text(it, fontSize = 11.sp, color = MiboColors.TextSecondary) }
                }
                Text("••", color = MiboColors.TextSecondary)
            }
            CameraThumb(device, Modifier.fillMaxWidth().aspectRatio(CAMERA_THUMB_RATIO))
        }
    }
}

@Composable
private fun DeviceCard(device: Device, modifier: Modifier, onClick: () -> Unit) {
    val subtitle = listOfNotNull(VIA_HUB.takeIf { device.subdevice }, sharedLabel(device.origin)).joinToString(" • ")
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 150.dp),
        shape = RoundedCornerShape(12.dp),
        color = MiboColors.Card,
    ) {
        Column(Modifier.padding(12.dp)) {
            OnlineDot(device.online)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DeviceDrawing(device) }
            Spacer(Modifier.height(8.dp))
            Text(
                device.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiboColors.TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = MiboColors.TextSecondary)
        }
    }
}

@Composable
private fun DeviceList(devices: List<Device>, onCamera: (Device) -> Unit, onDevice: (Device) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { d -> DeviceRow(d) { if (d.isCamera) onCamera(d) else onDevice(d) } }
    }
}

@Composable
private fun DeviceRow(device: Device, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MiboColors.Card) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp, 45.dp).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (device.isCamera) {
                    CameraThumb(device, Modifier.fillMaxSize(), badge = false, playSize = 24.dp)
                } else {
                    Icon(kindIcon(device.kind), contentDescription = null, modifier = Modifier.size(28.dp), tint = MiboColors.TextSecondary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiboColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    deviceSubtitle(device),
                    fontSize = 12.sp,
                    color = MiboColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OnlineDot(device.online)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MiboColors.TextSecondary)
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MiboColors.Green)
        Spacer(Modifier.height(12.dp))
        Text("Consultando dispositivos da conta…", color = MiboColors.TextSecondary)
    }
}

@Composable
private fun StateCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MiboColors.Card) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ErrorCard(error: AppError, onRetry: () -> Unit, onChangeToken: () -> Unit) {
    StateCard {
        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
        Text(error.userMessage, color = MiboColors.TextPrimary, textAlign = TextAlign.Center)
        // Detalhe técnico só em debug (RF04): release nunca mostra diagnóstico cru.
        if (isDebugBuild) error.detail?.let { Text(it, color = MiboColors.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center) }
        Button(onClick = onRetry) { Text("Tentar novamente") }
        // 401 e 403 têm a mesma saída: só um token novo resolve, então os dois oferecem a troca.
        if (error is AppError.TokenInvalid || error is AppError.TokenExpired) {
            OutlinedButton(onClick = onChangeToken, colors = ButtonDefaults.outlinedButtonColors(contentColor = MiboColors.TextPrimary)) {
                Text("Trocar token")
            }
        }
    }
}

@Composable
private fun EmptyCard(origin: OriginFilter, onRefresh: () -> Unit) {
    StateCard {
        Icon(Icons.Default.DevicesOther, contentDescription = null, modifier = Modifier.size(40.dp), tint = MiboColors.TextSecondary)
        Text("Nenhum dispositivo encontrado", fontSize = 18.sp, color = MiboColors.TextPrimary, textAlign = TextAlign.Center)
        Text(emptyMessage(origin), fontSize = 13.sp, color = MiboColors.TextSecondary, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onRefresh, colors = ButtonDefaults.outlinedButtonColors(contentColor = MiboColors.TextPrimary)) {
            Text("Atualizar")
        }
    }
}

/** Vazio com texto próprio por filtro (RF07): "vazio em Compartilhados" não é o mesmo que conta sem dispositivos. */
private fun emptyMessage(origin: OriginFilter): String =
    when (origin) {
        OriginFilter.ALL -> "Esta conta não tem dispositivos vinculados nem compartilhados."
        OriginFilter.LINKED -> "Nenhum dispositivo vinculado. Veja em Compartilhados ou Todos."
        OriginFilter.SHARED -> "Nenhum dispositivo compartilhado com esta conta."
    }

/** Rodapé de paginação (RF08): a GDI não devolve total, então "Carregar mais" aparece enquanto a página vem cheia. */
@Composable
private fun PaginationFooter(content: DevicesState.Content, onLoadMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content.note?.let { Text(it, Modifier.padding(bottom = 8.dp), color = MiboColors.TextSecondary, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MiboColors.Divider)
            if (content.hasMore) {
                OutlinedButton(onClick = onLoadMore, enabled = !content.loadingMore, modifier = Modifier.padding(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MiboColors.Green), border = BorderStroke(1.dp, MiboColors.Green)) {
                    if (content.loadingMore) { CircularProgressIndicator(Modifier.size(16.dp), color = MiboColors.Green, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text("Carregar mais")
                }
            } else {
                // Texto da referência Mibo; a frase completa fica para acessibilidade.
                Text("Sem mais informações", Modifier.padding(horizontal = 12.dp).semantics { contentDescription = "Todos os dispositivos carregados" },
                    color = MiboColors.TextSecondary, fontSize = 13.sp)
            }
            HorizontalDivider(Modifier.weight(1f), color = MiboColors.Divider)
        }
        content.loadMoreError?.let { error ->
            Text(error.userMessage, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center)
            TextButton(onClick = onLoadMore) { Text("Tentar novamente") }
        }
        Text("${content.pagesLoaded} página(s) · ${content.devices.size} dispositivo(s) · ${content.query.pageSize} por página",
            Modifier.padding(top = 8.dp), color = MiboColors.TextSecondary, fontSize = 12.sp)
    }
}

/** Abas de texto (Cenas/Automações, Notificações/Novidades): só alternam o destaque, o conteúdo da GDI é o mesmo vazio. */
@Composable
private fun TextTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, arrangement: Arrangement.Horizontal) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = arrangement) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Text(
                label,
                Modifier.selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = if (active) MiboColors.Green else MiboColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

/** Cenas e automações não existem na GDI: o "+" avisa e a aba mostra o vazio do Mibo. */
@Composable
private fun SmartTab(snack: Snack) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Title(HOME_TITLE, action = {
            IconButton(onClick = { snack(SCENES_UNAVAILABLE) }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar cena ou automação", tint = MiboColors.Green)
            }
        })
        Surface(Modifier.padding(horizontal = 16.dp).fillMaxSize(), shape = RoundedCornerShape(24.dp), color = MiboColors.Card) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                TextTabs(SMART_SECTIONS, section, { section = it }, Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally))
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(110.dp), tint = MiboColors.Divider)
                    Spacer(Modifier.height(16.dp))
                    Text("Não há rotinas criadas por aqui.", fontSize = 20.sp, color = MiboColors.TextSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Eventos chegam por webhook (exige backend): sem alertas simulados, o vazio explica o porquê. */
@Composable
private fun MessagesTab(snack: Snack) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Title("Mensagens", action = {
            IconButton(onClick = { snack(GDI_UNAVAILABLE) }) {
                Icon(Icons.Default.Delete, contentDescription = "Apagar mensagens", tint = MiboColors.TextSecondary)
            }
        })
        TextTabs(MESSAGE_SECTIONS, section, { section = it }, Arrangement.SpaceAround)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(96.dp), tint = EmptyBubble)
            Spacer(Modifier.height(16.dp))
            Text("Sem notificações", fontSize = 20.sp, color = MiboColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text(WEBHOOK_NOTE, fontSize = 12.sp, color = MiboColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StoreTab(snack: Snack) {
    Column(Modifier.fillMaxSize()) {
        Title("Loja")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Catálogo indisponível na API GDI", fontSize = 16.sp, color = MiboColors.TextSecondary)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { snack(GDI_UNAVAILABLE) }) { Text("Conhecer câmeras") }
        }
    }
}

@Composable
private fun SettingsTab(vm: HomeViewModel, maskedToken: String?, hasToken: Boolean, pageSize: Int, onLogout: () -> Unit) {
    var openOption by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Title("Definições")
        AccountCard(
            providerNames = vm.providerNames,
            maskedToken = maskedToken,
            hasToken = hasToken,
            pageSize = pageSize,
            onRefresh = { vm.refresh() },
            onLogout = onLogout,
            onPageSize = { if (it != pageSize) vm.setPageSize(it) },
        )
        Spacer(Modifier.height(24.dp))
        SettingsOptions { openOption = it }
    }
    openOption?.let { label -> DemoOptionDialog(label) { openOption = null } }
}

/** Conta conectada: parceiros, token só mascarado (regra 0.2.2), Atualizar/Sair e itens por página (RF08). */
@Composable
private fun AccountCard(
    providerNames: List<String>,
    maskedToken: String?,
    hasToken: Boolean,
    pageSize: Int,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPageSize: (Int) -> Unit,
) {
    Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MiboColors.Card) {
        Column(Modifier.padding(16.dp)) {
            Text("Conta Intelbras", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiboColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(providerNames.joinToString(" · "), fontSize = 12.sp, color = MiboColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text("Token: ${maskedToken ?: "—"}", color = MiboColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = hasToken) { Text("Atualizar") }
                OutlinedButton(onClick = onLogout, colors = ButtonDefaults.outlinedButtonColors(contentColor = MiboColors.TextPrimary)) {
                    Text("Sair")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Itens por página", fontSize = 12.sp, color = MiboColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            PageSizeSelector(pageSize, onPageSize)
        }
    }
}

@Composable
private fun PageSizeSelector(pageSize: Int, onPageSize: (Int) -> Unit) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MiboColors.Green,
        activeContentColor = Color.White,
        activeBorderColor = MiboColors.Divider,
        inactiveContainerColor = MiboColors.Card,
        inactiveContentColor = MiboColors.TextPrimary,
        inactiveBorderColor = MiboColors.Divider,
    )
    val sizes = DeviceQuery.PAGE_SIZES
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        sizes.forEachIndexed { index, size ->
            SegmentedButton(
                selected = size == pageSize,
                onClick = { onPageSize(size) },
                shape = SegmentedButtonDefaults.itemShape(index, sizes.size),
                colors = colors,
            ) {
                Text(size.toString())
            }
        }
    }
}

@Composable
private fun SettingsOptions(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MiboColors.Card)) {
        SETTINGS_OPTIONS.forEach { label ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(label) }.padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MiboColors.TextSecondary)
                Spacer(Modifier.width(16.dp))
                Text(label, Modifier.weight(1f), fontSize = 16.sp, color = MiboColors.TextPrimary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MiboColors.TextSecondary)
            }
            HorizontalDivider(color = MiboColors.Divider)
        }
    }
}

/** Opções de conta do Mibo sem serviço na GDI: o diálogo diz isso em vez de abrir tela vazia. */
@Composable
private fun DemoOptionDialog(label: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
        text = { Text("$label: área demonstrativa, sem serviço de conta conectado.") },
        containerColor = MiboColors.Card,
    )
}
