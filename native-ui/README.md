# native-ui: UI nativa por plataforma + `shared` só com lógica

Projeto **separado** do case, dentro do mesmo repositório, para mostrar na prática a outra forma de usar KMP:
as telas são nativas em cada plataforma (**Jetpack Compose** no Android, **SwiftUI** no iOS) e o módulo
`shared` fica só com regra de negócio, dados e injeção. Nada do projeto da raiz foi alterado; este é um build
Gradle independente, com `settings.gradle.kts`, catálogo de versões e `gradlew` próprios.

```
native-ui/
  shared/        Kotlin Multiplatform: domain, data, di e a ponte para o Swift (sdk). SEM Compose.
  androidApp/    App Android nativo: Jetpack Compose (AndroidX) + ViewModel em Kotlin + libVLC.
  iosApp/        App iOS nativo: SwiftUI + ObservableObject em Swift + VLCKit.
```

| | Projeto da raiz (Compose Multiplatform) | Este projeto (UI nativa) |
|---|---|---|
| Telas | Uma vez, em `shared/ui` (Compose MP, desenhado com Skia no iOS) | Duas vezes: Compose no `androidApp`, SwiftUI no `iosApp` |
| ViewModel | Um, no `shared` | Um por plataforma: `HomeViewModel` (Kotlin, AndroidX) e `ObservableObject`s em Swift |
| Player | `expect fun CameraPlayer` com `actual` por plataforma | Sem `expect/actual`: cada UI usa o seu (libVLC / VLCKit) direto |
| Ponte para o iOS | `ComposeUIViewController` hospeda a árvore inteira | `CasaSdk`: fachada Kotlin que o Swift consome (`StateWatcher`, `Outcome`) |
| `shared` contém | domain, data, di, ui, player, platform | domain, data, di, sdk |
| Framework iOS | Compose + Skia + Kotlin | Só Kotlin: bem menor |

## O que ficou no `shared`

`domain/`, `data/` e `di/` são cópias do projeto da raiz, sem nenhuma dependência de UI. O `Koin.kt` registra
API, repositórios, parceiro GDI e casos de uso; não registra ViewModel nenhum (apresentação é de cada app).
`platformModule` continua `expect/actual`: Keystore no Android, Keychain no iOS.

### A ponte para o Swift: `shared/src/commonMain/kotlin/br/com/pompeo/casa/sdk/CasaSdk.kt`

O Android consome o `shared` como qualquer biblioteca Kotlin: o `HomeViewModel` recebe `DeviceRepository`,
`TokenRepository` e os casos de uso pelo Koin. O iOS enxerga um framework Objective-C, e aí aparecem os limites
do Kotlin/Native. A fachada `CasaSdk` existe para contorná-los num lugar só:

| Limite do Kotlin/Native → Swift | Como a fachada contorna |
|---|---|
| `Flow`/`StateFlow` não atravessam | `StateWatcher<T>`: `value` síncrono + `watch { }` que devolve `WatchHandle.cancel()` |
| Exceção em `suspend` derruba o app sem `@Throws` | Toda operação devolve `Outcome<T>` (`valueOrNull`, `errorOrNull`) com o `AppError` já mapeado |
| Genéricos viram lightweight generics do Obj-C | Os valores de `Outcome` são sempre classes (`LockDetails`, `StreamSession`…), nunca primitivos |
| `Int` vira `Int32`, sem valores padrão | Assinaturas sem parâmetro opcional; o Swift passa `Int32` |
| Callbacks precisam chegar na main thread | A fachada roda em `Dispatchers.Main`; o `ObservableObject` atualiza `@Published` direto |
| `object` e funções de topo | `CasaSdkKt.casaSdk()` depois de `KoinKt.doInitKoin()` |

Alternativa de mercado: o [SKIE](https://skie.touchlab.co) gera `AsyncSequence` para `Flow`, `async` para
`suspend` e `enum` para `sealed class`. Aqui a ponte foi escrita à mão para ficar visível o que ela resolve.

O contrato é testado no comum (`CasaSdkTest`), com os mesmos fakes dos outros testes, na JVM e no simulador iOS.

## Como abrir e rodar

- **Android Studio:** File → Open → `native-ui/` (é um projeto Gradle separado). Para vê-lo junto do projeto
  da raiz, bastaria `includeBuild("native-ui")` no `settings.gradle.kts` da raiz, o que não foi feito para
  não alterar o projeto existente.
- **Xcode:** `native-ui/iosApp/iosApp.xcodeproj`. A fase de script compila o `shared` pelo Gradle, como na raiz.
- **Token:** o mesmo `local.properties` da raiz (`gdi.token=`) é lido pelo Gradle; `native-ui/local.properties`
  só precisa do `sdk.dir` (e pode sobrescrever o token).

```bash
cd native-ui
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test   # 80 testes de regra, dados e ponte
./gradlew :androidApp:testDebugUnitTest                                 # testes do ViewModel e da lógica de tela
./gradlew :androidApp:installDebug                                      # Moto G9 Play
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "id=<UDID>" -allowProvisioningUpdates build
```

Os dois apps coexistem com os da raiz no mesmo aparelho: `br.com.pompeo.casa.nativeui` (Android) e
`br.com.pompeo.casa.nativa` (iOS), com o rótulo "Casa Nativa".

## Verificação

| Onde | O quê | Resultado |
|---|---|---|
| JVM | 80 testes do `shared` (75 copiados + 5 da ponte `CasaSdk`) | OK |
| Simulador iOS | Os mesmos 80 testes, em Kotlin/Native | OK |
| JVM | 16 testes do `androidApp` (`HomeViewModel`, lógica de tela, formato), portados da raiz | OK |
| Moto G9 Play (08/10/2026) | App Android nativo com a API real: token pré-preenchido e mascarado → Home com 3 dispositivos e filtros → fechadura (bateria 25 %, online, "Porta aberta", histórico; só leitura) → hub (online, firmware 2.4.628243, acessório) → câmera ao vivo (1.º quadro em 26,3 s a frio) | OK |
| Moto G9 Play | Token vencido: "Seu token expirou (ele vale cerca de 2 horas)…" com o detalhe HTTP 403 só em debug (`BuildConfig.DEBUG`) | OK |
| iPhone 13 Pro (08/10/2026) | App iOS nativo em SwiftUI com a API real: token pré-preenchido → Home com 3 dispositivos, filtros e paginação → fechadura (Porta aberta, bateria 25 %, online, abertura remota habilitada, volume com o HTTP 500 real, histórico; só leitura) → hub (online, firmware 2.4.628243, 1 acessório) → câmera ao vivo pelo VLCKit (1.º quadro em 53,0 s a frio) | OK |
| iPhone 13 Pro | Percurso feito por um alvo XCUITest (`iosAppUITests/ReadOnlyWalkthroughUITests.swift`) que só lê e nunca toca em "Confirmar"; capturas em `native-ui/build/screenshots/` | OK |

Medidas comparadas com o projeto da raiz (Debug, mesmo aparelho):

| | Raiz (Compose Multiplatform) | native-ui (UI nativa) |
|---|---|---|
| `Shared.framework` para iPhone (binário) | 371 MB | 56 MB |
| APK debug (dominado pelo libVLC) | 159 MB | 159 MB |
| Código de tela | 2 867 linhas Kotlin, uma vez | 3 121 Kotlin (Android) + 1 589 Swift (iOS) + 156 da ponte |
| 1.º quadro da câmera a frio | 22,6 s Android · 39,2 s iPhone | 26,3 s Android · 53,0 s iPhone (uma medição cada; varia com a nuvem) |

## Trade-offs, em uma linha cada

- **Ganha:** visual e gestos 100 % nativos (voltar por deslize, Dynamic Type, acessibilidade), binário iOS
  menor, times Android e iOS em paralelo, acesso imediato a APIs novas de cada plataforma.
- **Paga:** duas UIs e duas navegações para manter, lógica de apresentação duplicada (o `HomeViewModel` em
  Kotlin e os `ObservableObject`s em Swift resolvem os mesmos problemas) e a ponte Kotlin → Swift.
- **Meio-termo:** manter os ViewModels no `shared` (o ViewModel da JetBrains roda no iOS) e nativizar só a
  camada de tela; o SwiftUI observaria o `StateFlow` do ViewModel pela mesma ponte.
