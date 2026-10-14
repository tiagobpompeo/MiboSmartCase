// O único ponto nativo da UI: expect fun CameraPlayer. Android com libVLC; iOS com VLCKit em Swift sobre a
// interface Kotlin NativeVideoPlayer (por isso o umbrella :shared exporta este módulo para o framework).
plugins {
    // O plugin Kotlin Multiplatform vem declarado aqui também: é dele que o Gradle gera os acessores tipados
    // (kotlin { }, sourceSets.commonMain) deste script. A configuração continua toda em casa.kmp.compose.
    alias(libs.plugins.kotlinMultiplatform)
    id("casa.kmp.compose")
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            // RTSP da nuvem Intelbras: H.265 sem "fmtp" no SDP, que o Media3 não aceita; o libVLC aceita.
            implementation(libs.libvlc)
        }
    }
}
