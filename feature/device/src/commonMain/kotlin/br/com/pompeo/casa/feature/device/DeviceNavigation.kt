package br.com.pompeo.casa.feature.device

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import br.com.pompeo.casa.domain.APP_SCOPE_NAME
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Rota tipada do detalhe: [ns] = número de série do dispositivo na lista acumulada pela Home. */
@Serializable
data class DeviceRoute(val ns: String)

/**
 * Registra a tela no grafo do umbrella. O ViewModel vem do Koin preso à entrada da pilha (koinViewModel dentro
 * do composable): nasce ao abrir a tela e é limpo ao sair. [onBack] recebe a entrada para o App só desempilhar
 * se ela ainda for o topo (2.º toque em "voltar" durante a transição de saída).
 */
fun NavGraphBuilder.deviceScreen(onBack: (NavBackStackEntry) -> Unit) {
    composable<DeviceRoute> { entry ->
        val vm = koinViewModel<DeviceViewModel>()
        DeviceScreen(vm, entry.toRoute<DeviceRoute>().ns, onBack = { onBack(entry) })
    }
}

/**
 * Módulo Koin da feature: o caso de uso de volume (factory: sem estado) e o ViewModel. O escopo da aplicação é
 * pedido pelo qualificador [APP_SCOPE_NAME], registrado por :core:data; a feature não conhece a implementação.
 */
val deviceFeatureModule = module {
    factoryOf(::ChangeLockVolumeUseCase)
    // Estado do comando físico em andamento, único para o app: o ViewModel é por tela, a trava não.
    single(named(LOCK_COMMAND_STATE)) { MutableStateFlow<LockCommand?>(null) }
    viewModel { DeviceViewModel(get(), get(), get(), get(named(APP_SCOPE_NAME)), get(named(LOCK_COMMAND_STATE))) }
}

/** Qualificador do estado compartilhado do comando da fechadura. */
private const val LOCK_COMMAND_STATE = "lockCommandState"
