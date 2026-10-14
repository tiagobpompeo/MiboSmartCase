import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// App Android NATIVO do projeto native-ui: Jetpack Compose da AndroidX (não o Compose Multiplatform da JetBrains).
// O :shared entra só com regra e dados; toda a apresentação (telas, ViewModel, player, calendário) vive aqui.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)      // as telas são @Composable
    alias(libs.plugins.kotlinxSerialization) // rotas tipadas da navegação (App.kt) são @Serializable; na raiz ficava no shared
    // Sem org.jetbrains.kotlin.android: o AGP 9 já traz o Kotlin embutido e os dois entram em conflito.
}

dependencies {
    implementation(projects.shared)
    // Jetpack Compose (AndroidX). Na raiz estas eram as artefatos org.jetbrains.compose.* em commonMain do shared.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // Koin: androidContext() na Application e koinViewModel() nas telas (org.koin.androidx.compose).
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // libVLC: RTSP H.265 da nuvem Intelbras (era androidMain do shared da raiz; aqui é dependência do app).
    implementation(libs.libvlc)
    // Testes unitários na JVM (HomeViewModelTest, MiboLogicTest, CameraFormatTest).
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
}

android {
    namespace = "br.com.pompeo.casa.nativeui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Diferente de br.com.pompeo.casa (app da raiz): os dois coexistem no mesmo aparelho.
        applicationId = "br.com.pompeo.casa.nativeui"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // libVLC traz um .so de 40–54 MB por ABI; limitar reduz o APK (aparelhos reais + emulador).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    buildFeatures {
        buildConfig = true // BuildConfig.DEBUG substitui o expect/actual isDebugBuild do projeto da raiz
        compose = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
}
