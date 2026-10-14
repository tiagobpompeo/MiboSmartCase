package br.com.pompeo.casa.di

import br.com.pompeo.casa.data.security.KeystoreTokenStorage
import br.com.pompeo.casa.domain.TokenStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TokenStorage> { KeystoreTokenStorage(androidContext()) }
}
