package br.com.pompeo.casa.feature.camera

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import br.com.pompeo.casa.domain.APP_SCOPE_NAME
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Rota tipada da câmera: [ns] é o número de série, o identificador do dispositivo no parceiro. */
@Serializable
data class CameraRoute(val ns: String)

/**
 * Registra a tela no NavHost do umbrella. O ViewModel vem do Koin com o escopo da entrada da pilha
 * (koinViewModel usa o ViewModelStoreOwner atual): um por visita à câmera, descartado ao voltar.
 * [onBack] recebe a entrada para o umbrella decidir se desempilha (só se ela ainda for o topo).
 */
fun NavGraphBuilder.cameraScreen(onBack: (NavBackStackEntry) -> Unit) {
    composable<CameraRoute> { entry ->
        val vm = koinViewModel<CameraViewModel>()
        CameraScreen(vm, entry.toRoute<CameraRoute>().ns, onBack = { onBack(entry) })
    }
}

/** Módulo Koin da feature: só o ViewModel. Repositório, ErrorMapper e o appScope vêm de :core:data. */
val cameraFeatureModule = module {
    viewModel { CameraViewModel(get(), get(), get(named(APP_SCOPE_NAME))) }
}
