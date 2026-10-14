package br.com.pompeo.casa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Regra de honestidade: controle sem rota na GDI avisa em vez de ficar mudo. */
const val GDI_UNAVAILABLE = "Indisponível na API GDI"

/** Fundo da miniatura: a GDI não tem rota de snapshot, então a câmera aparece desenhada. */
private val CameraThumbBg = Color(0xFF2B2F33)
/** Bolinha offline/desconhecido e ícone genérico de dispositivo. */
private val InactiveGray = Color(0xFFBDBDBD)
private const val PLAY_CIRCLE_ALPHA = 0.35f
private const val PLAY_ICON_FRACTION = 0.55f
private const val LENS_BADGE_ALPHA = 0.7f
/** Largura da coluna de rótulos no diálogo de informações, medida em app-13-camera-informacoes.png. */
private const val INFO_KEY_WEIGHT = 0.48f
private val FIT_TEXT_STEP = 0.5.sp

/** Snackbar por tela. Descarta o aviso anterior: toques repetidos não enfileiram segundos de espera. */
class Snack(val host: SnackbarHostState, private val scope: CoroutineScope) {
    operator fun invoke(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
}

@Composable
fun rememberSnack(): Snack {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(host, scope) { Snack(host, scope) }
}

/** Título de página. Sem statusBarsPadding: quem usa (abas da casca) já recebe o inset do Scaffold. */
@Composable
fun Title(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = MiboColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        action?.invoke()
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Voltar", modifier = Modifier.size(20.dp))
    }
}

fun kindIcon(kind: DeviceKind): ImageVector =
    when (kind) {
        DeviceKind.CAMERA -> Icons.Default.Videocam
        DeviceKind.LOCK -> Icons.Default.Lock
        DeviceKind.HUB -> Icons.Default.Hub
        DeviceKind.SENSOR -> Icons.Default.Sensors
        DeviceKind.LAMP -> Icons.Default.Lightbulb
        DeviceKind.OTHER -> Icons.Default.DevicesOther
    }

/** Miniatura desenhada (sem imagem da câmera: a GDI não tem snapshot). Quem chama decide forma e recorte. */
@Composable
fun CameraThumb(device: Device, modifier: Modifier = Modifier, badge: Boolean = true, playSize: Dp = 44.dp) {
    Box(modifier.background(CameraThumbBg), contentAlignment = Alignment.Center) {
        if (badge) LensBadge(lensCount(device.model, device.name), Modifier.align(Alignment.TopStart))
        Box(
            Modifier.size(playSize).background(Color.White.copy(alpha = PLAY_CIRCLE_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(playSize * PLAY_ICON_FRACTION), tint = Color.White)
        }
    }
}

/** Quantidade de lentes no canto da miniatura, como o "2" da câmera Dual em home.jpg. */
@Composable
fun LensBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = LENS_BADGE_ALPHA), RoundedCornerShape(bottomEnd = 8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(count.toString(), color = Color.White, fontSize = 14.sp)
    }
}

/** Cinza também quando a GDI não informa (null): só "online" confirmado ganha a cor de destaque. */
@Composable
fun OnlineDot(online: Boolean?, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(if (online == true) MiboColors.OnlineDot else InactiveGray, CircleShape))
}

/** Desenho do aparelho no cartão: nada de foto de produto Intelbras, só Canvas e ícones. */
@Composable
fun DeviceDrawing(device: Device, modifier: Modifier = Modifier) {
    when (device.kind) {
        DeviceKind.LOCK -> LockDrawing(modifier)
        DeviceKind.HUB -> HubDrawing(modifier.size(84.dp))
        else -> Icon(kindIcon(device.kind), contentDescription = null, modifier = modifier.size(48.dp), tint = InactiveGray)
    }
}

/** Hub desenhado em Canvas (nada de imagem de produto Intelbras): disco branco, anel cinza, botão central com anel azulado. */
@Composable
fun HubDrawing(modifier: Modifier, ring: Color = Color(0xFFD9D9D9), ringWidth: Dp = 2.dp, centerFraction: Float = 22f / 84f,
               centerRing: Color = Color(0xFFBFD9FF), shadow: Dp = 0.dp) {
    Canvas(modifier.shadow(shadow, CircleShape).background(Color.White, CircleShape)) {
        val radius = size.minDimension / 2
        val stroke = ringWidth.toPx()
        drawCircle(ring, radius - stroke / 2, style = Stroke(stroke))
        val inner = radius * centerFraction
        drawCircle(Color.White, inner)
        drawCircle(centerRing, inner, style = Stroke(stroke))
    }
}

/** Fechadura desenhada: corpo escuro, cadeado e três pontos do teclado. */
@Composable
fun LockDrawing(modifier: Modifier = Modifier) {
    Column(modifier.size(34.dp, 64.dp).background(Color(0xFF2E3338), RoundedCornerShape(10.dp)).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.height(10.dp))
        repeat(3) { Box(Modifier.padding(vertical = 2.dp).size(4.dp).background(Color(0xFF8A8F94), CircleShape)) }
    }
}

/**
 * Ficha do dispositivo (ícone de configurações). [Device.lastOnline] já chega formatado do parceiro, então a UI
 * não conhece o formato da GDI; [extra] acrescenta linhas próprias da tela (ex.: fluxo, bateria).
 */
@Composable
fun DeviceInfoDialog(device: Device, providerName: String, extra: List<Pair<String, String>> = emptyList(), onDismiss: () -> Unit) {
    val rows = remember(device, providerName, extra) { deviceInfoRows(device, providerName) + extra }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
        title = { Text(device.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (label, value) -> InfoRow(label, value) }
            }
        },
        containerColor = MiboColors.Card,
    )
}

/** Campos que a API não informou somem, em vez de aparecer vazios; "via hub" só para subdispositivos. */
private fun deviceInfoRows(device: Device, providerName: String): List<Pair<String, String>> =
    listOfNotNull(
        "Tipo" to device.kind.label,
        device.model?.let { "Modelo" to it },
        "Número de série" to device.ns,
        device.productId?.let { "ID do produto" to it },
        "Origem" to device.origin.label,
        "Parceiro" to providerName,
        ("Conectado via hub" to "Sim").takeIf { device.subdevice },
        device.version?.let { "Versão" to it },
        device.lastOnline?.let { "Último contato" to it },
    )

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(INFO_KEY_WEIGHT), color = MiboColors.TextSecondary)
        Text(value, Modifier.weight(1f - INFO_KEY_WEIGHT), color = MiboColors.TextPrimary)
    }
}

/**
 * Texto que reduz a fonte até caber (no mínimo [minSize]) em vez de quebrar palavra no meio ou cortar com
 * reticências: no iPhone de 390 pt o banner quebrava "Armazenament|o" e nomes como "MFR 2030-1B35" viravam
 * "MFR 2030-1B…". Não quebra linha sozinho: as linhas são as do próprio [text] (separadas por '\n'). Abaixo de
 * [minSize] o excesso é cortado; os mínimos de quem chama cobrem os nomes reais da conta com folga.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    minSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = MiboColors.TextPrimary,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    /** Em `em` acompanha a fonte reduzida; sem valor, vale o do tema (24 sp, bom só para texto pequeno). */
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val lineCount = text.count { it == '\n' } + 1
    BasicText(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.merge(
            TextStyle(color = color, fontSize = maxSize, fontWeight = fontWeight, textAlign = textAlign, lineHeight = lineHeight),
        ),
        // Clip e não Ellipsis: o autoSize detecta o estouro de largura nas duas plataformas; com Ellipsis ele
        // depende de a linha vir marcada como "ellipsized", o que o iOS (Skia) não informa, e o texto não encolhia.
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = lineCount,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = FIT_TEXT_STEP),
    )
}
