package br.com.pompeo.casa

import android.app.Application
import br.com.pompeo.casa.di.initKoin
import br.com.pompeo.casa.di.uiModule
import org.koin.android.ext.koin.androidContext

// Mudou no porte: na raiz esta classe é Java (para mostrar a interop) e só passa o androidContext, porque o
// HomeViewModel já estava no appModule do shared. Aqui o shared não conhece ViewModel nenhum, então o app
// acrescenta o seu uiModule; initKoin(config) do shared ainda soma appModule e platformModule.
class CasaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Koin uma vez por processo.
        initKoin {
            androidContext(this@CasaApplication)
            modules(uiModule)
        }
    }
}
