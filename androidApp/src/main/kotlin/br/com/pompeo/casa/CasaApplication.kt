package br.com.pompeo.casa

import android.app.Application
import br.com.pompeo.casa.di.initKoin
import org.koin.android.ext.koin.androidContext

class CasaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@CasaApplication) } // Koin uma vez por processo
    }
}
