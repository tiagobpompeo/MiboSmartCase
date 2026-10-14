package br.com.pompeo.casa.di

import br.com.pompeo.casa.ui.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Módulo de apresentação do app Android. Mudou no porte: na raiz o viewModelOf(::HomeViewModel) ficava no
 * appModule do shared; aqui o shared só tem domain/data, e cada app nativo registra o seu ViewModel.
 */
val uiModule = module {
    viewModelOf(::HomeViewModel)
}
