package br.com.pompeo.casa

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import br.com.pompeo.casa.domain.repository.TokenRepository
import br.com.pompeo.casa.feature.camera.CameraRoute
import br.com.pompeo.casa.feature.camera.cameraScreen
import br.com.pompeo.casa.feature.device.DeviceRoute
import br.com.pompeo.casa.feature.device.deviceScreen
import br.com.pompeo.casa.feature.home.HomeRoute
import br.com.pompeo.casa.feature.home.homeScreen
import br.com.pompeo.casa.feature.token.TokenRoute
import br.com.pompeo.casa.feature.token.tokenScreen
import br.com.pompeo.casa.ui.MiboColors
import org.koin.compose.koinInject

/**
 * Raiz da UI compartilhada: a mesma árvore Compose roda no Android e no iOS. O umbrella só monta o tema e o
 * grafo de navegação; cada feature registra a própria tela (e o próprio ViewModel) por uma extensão de
 * NavGraphBuilder. Rota nova = mais uma linha aqui e um módulo Koin em di/Koin.kt.
 */
@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MiboColors.Green, onPrimary = Color.White,
            background = MiboColors.PageBg, surface = MiboColors.Card,
            onBackground = MiboColors.TextPrimary, onSurface = MiboColors.TextPrimary,
            secondary = MiboColors.GreenNav,
            // Diálogos e menus brancos como os cartões, em vez do cinza-lilás padrão do Material 3.
            surfaceContainerHigh = MiboColors.Card, surfaceContainer = MiboColors.Card,
            outline = MiboColors.Divider,
        )
    ) {
        // O token restaurado do cofre decide a tela inicial. Decidido UMA vez (senão o NavHost trocaria de
        // destino a cada recomposição); o repositório é single do Koin, o mesmo que as features observam.
        val tokens = koinInject<TokenRepository>()
        val nav = rememberNavController()
        val start: Any = remember { if (tokens.token.value != null) HomeRoute else TokenRoute }
        // Só desempilha se a entrada ainda é o topo: um 2.º toque em "voltar" durante a transição de saída
        // desempilharia a Home e deixaria o NavHost em branco (beco sem saída no iOS).
        // Não use dropUnlessResumed: na transição de entrada a tela está só STARTED e o toque legítimo se perderia.
        val popIfTop: (NavBackStackEntry) -> Unit = { entry -> if (nav.currentBackStackEntry?.id == entry.id) nav.popBackStack() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(nav, startDestination = start) {
                tokenScreen(onConnected = { nav.navigate(HomeRoute) { popUpTo<TokenRoute> { inclusive = true } } })
                homeScreen(
                    onCamera = { nav.navigate(CameraRoute(it.ns)) },
                    onDevice = { nav.navigate(DeviceRoute(it.ns)) },
                    // Limpa a pilha inteira: Sair só existe na Home, que é a base da pilha (o login a substituiu).
                    // Não use popUpTo(0): popUpTo(Int) não está na API comum do Navigation.
                    onLogout = { nav.navigate(TokenRoute) { popUpTo<HomeRoute> { inclusive = true } } },
                )
                cameraScreen(onBack = popIfTop)
                deviceScreen(onBack = popIfTop)
            }
        }
    }
}
