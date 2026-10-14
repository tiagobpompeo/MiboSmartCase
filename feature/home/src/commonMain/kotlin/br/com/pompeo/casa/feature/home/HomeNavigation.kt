package br.com.pompeo.casa.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.com.pompeo.casa.domain.model.Device
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Rota tipada da Home (Navigation Compose multiplataforma + kotlinx.serialization). Sem argumentos. */
@Serializable object HomeRoute

/**
 * Destino da Home no NavHost do umbrella. A feature não conhece as outras rotas: para onde ir ao tocar numa
 * câmera, num dispositivo ou em Sair é decisão do App, recebida por callback. O ViewModel vem do Koin com o
 * dono padrão (a entrada da pilha): sai da pilha, some o ViewModel.
 */
fun NavGraphBuilder.homeScreen(onCamera: (Device) -> Unit, onDevice: (Device) -> Unit, onLogout: () -> Unit) {
    composable<HomeRoute> {
        val vm = koinViewModel<HomeViewModel>()
        // Sair apaga a sessão aqui (repositório + token) e só então o App troca de rota.
        HomeShell(vm, onCamera, onDevice, onLogout = { vm.logout(); onLogout() })
    }
}

/** Módulo Koin da feature: só o ViewModel. Repositórios e ErrorMapper vêm de :core:data pelas interfaces do domain. */
val homeFeatureModule = module {
    viewModelOf(::HomeViewModel)
}
