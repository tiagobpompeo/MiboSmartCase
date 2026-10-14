package br.com.pompeo.casa.feature.token

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Rota tipada da tela de token (RF01), sem argumentos: o umbrella a usa como destino inicial e no logout. */
@Serializable object TokenRoute

/**
 * Entrada desta feature no NavHost do umbrella. O ViewModel vem do Koin com dono na entrada da pilha: sobrevive
 * à rotação e é descartado quando a tela sai da pilha (popUpTo inclusive depois do Done).
 */
fun NavGraphBuilder.tokenScreen(onConnected: () -> Unit) {
    composable<TokenRoute> {
        val vm = koinViewModel<TokenViewModel>()
        TokenScreen(vm, onConnected)
    }
}

/** O que esta feature registra no Koin: só o próprio caso de uso e ViewModel; repositórios vêm de :core:data. */
val tokenFeatureModule = module {
    factoryOf(::SubmitTokenUseCase)
    viewModelOf(::TokenViewModel)
}
