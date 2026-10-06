package br.com.pompeo.casa.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import org.koin.core.context.GlobalContext

// Biblioteca KMP não tem BuildConfig: lê a flag do app hospedeiro. Sem Koin iniciado (Preview da IDE)
// a leitura síncrona lança e cai em false (release) em vez de derrubar a tela.
actual val isDebugBuild: Boolean
    get() = runCatching { (GlobalContext.get().get<Context>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
        .getOrDefault(false)
