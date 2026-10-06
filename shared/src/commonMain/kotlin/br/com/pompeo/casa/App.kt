package br.com.pompeo.casa

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.com.pompeo.casa.ui.CameraScreen
import br.com.pompeo.casa.ui.DeviceScreen
import br.com.pompeo.casa.ui.HomeShell
import br.com.pompeo.casa.ui.HomeViewModel
import br.com.pompeo.casa.ui.MiboColors
import br.com.pompeo.casa.ui.TokenScreen
import br.com.pompeo.casa.ui.TokenValidation
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

// Rotas tipadas (Navigation Compose multiplataforma + kotlinx.serialization). Argumentos pequenos: a tela
// busca o objeto no StateFlow do ViewModel pelo ns.
@Serializable object TokenRoute
@Serializable object HomeRoute
@Serializable data class CameraRoute(val ns: String)
@Serializable data class DeviceRoute(val ns: String)

/** Raiz da UI compartilhada: a mesma árvore Compose roda no Android e no iOS. */
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
        // Fora do NavHost: o dono é o ViewModelStoreOwner do host (Activity / ComposeUIViewController).
        val vm = koinViewModel<HomeViewModel>()
        val nav = rememberNavController()
        // Decidido UMA vez (senão o NavHost trocaria de destino a cada recomposição). Validação em andamento
        // (token só na sessão) volta para a tela de token, que mostra o progresso.
        val start: Any = remember { if (vm.token.value != null && vm.tokenValidation.value !is TokenValidation.Validating) HomeRoute else TokenRoute }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(nav, startDestination = start) {
                composable<TokenRoute> {
                    TokenScreen(vm, onConnected = { nav.navigate(HomeRoute) { popUpTo<TokenRoute> { inclusive = true } } })
                }
                composable<HomeRoute> {
                    HomeShell(
                        vm,
                        onCamera = { nav.navigate(CameraRoute(it.ns)) },
                        onDevice = { nav.navigate(DeviceRoute(it.ns)) },
                        // Limpa a pilha inteira: Sair só existe na Home, que é a base da pilha (o login a substituiu).
                        // Não use popUpTo(0): popUpTo(Int) não está na API comum do Navigation, e a sobrecarga comum
                        // de rota-objeto trataria 0 como rota e lançaria por não achá-la no grafo.
                        onLogout = { vm.logout(); nav.navigate(TokenRoute) { popUpTo<HomeRoute> { inclusive = true } } },
                    )
                }
                composable<CameraRoute> { entry -> CameraScreen(vm, entry.toRoute<CameraRoute>().ns, onBack = { nav.popBackStack() }) }
                composable<DeviceRoute> { entry -> DeviceScreen(vm, entry.toRoute<DeviceRoute>().ns, onBack = { nav.popBackStack() }) }
            }
        }
    }
}
