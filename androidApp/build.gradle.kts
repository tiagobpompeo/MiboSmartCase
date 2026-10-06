import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler) // MainActivity chama setContent { App() }
    // Sem org.jetbrains.kotlin.android: o AGP 9 já traz o Kotlin embutido e os dois entram em conflito.
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    testImplementation(libs.junit) // TokenFormatJavaTest: teste em Java chamando o Kotlin do shared
}

android {
    namespace = "br.com.pompeo.casa"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "br.com.pompeo.casa"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // libVLC traz um .so de 40–54 MB por ABI; limitar reduz o APK (aparelhos reais + emulador).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
