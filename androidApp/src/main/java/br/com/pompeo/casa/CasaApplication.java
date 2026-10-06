package br.com.pompeo.casa;

import android.app.Application;

import br.com.pompeo.casa.di.KoinKt;
import kotlin.Unit;
import org.koin.android.ext.koin.KoinExtKt;

// Em Java de propósito, para mostrar a interop com o Kotlin: a função de topo initKoin vira o estático
// KoinKt.initKoin, o tipo-função KoinApplication.() -> Unit vira Function1 (por isso o Unit.INSTANCE)
// e a extensão androidContext vira um estático que recebe o receptor como primeiro argumento.
public final class CasaApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Koin uma vez por processo.
        KoinKt.initKoin(koin -> {
            KoinExtKt.androidContext(koin, this);
            return Unit.INSTANCE;
        });
    }
}
