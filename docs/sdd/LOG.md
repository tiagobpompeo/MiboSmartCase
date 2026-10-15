# Log do desenvolvimento orientado por especificação (SDD)

Registro de como a especificação [`PROMPT-CASE-KMP.md`](PROMPT-CASE-KMP.md) foi executada com um agente de IA de código (Claude Code) entre 05 e 06/10/2026, e de como cada etapa foi verificada. A regra de trabalho foi a da própria especificação: um marco só termina quando o build Android e iOS passa, os testes estão verdes e o critério de pronto do marco foi conferido por comando ou no aparelho.

## 1. Especificação

Antes do código, a especificação fixou:

- **Marcos M1–M12**, cada um com objetivo, arquivos, código das partes difíceis, testes e um critério de pronto verificável.
- **O contrato real da API GDI**, medido na conta de teste e divergente do Swagger em vários pontos (JSON servido como `text/plain`, erro dentro de HTTP 200, `ns` composto dos subdispositivos, vídeo só por RTSP, volume com HTTP 500).
- **As capturas de referência** do app Mibo Smart e os prints esperados de cada tela.
- **A lista dos testes** que deviam existir (85) e as armadilhas já conhecidas (sintoma → causa → solução).
- **Regras invioláveis**: token nunca em arquivo versionado nem em log; nenhum comando físico na fechadura sem autorização explícita do dono da conta; MVVM + Clean Architecture conferidas por `grep`.

## 2. Execução

| Etapa | O que foi feito | Como foi verificado |
|---|---|---|
| M1 — esqueleto | Pacote `br.com.pompeo.casa`, Gradle (Koin, Ktor, libVLC, navegação tipada, task `generateGdiConfig`), projeto Xcode (Info.plist fora da pasta sincronizada, Team, VLCKit via SPM, esquema compartilhado). Mantidos Kotlin 2.4.20 / Compose MP 1.12.1 do assistente, como a especificação permitia. | `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64` e `xcodebuild` para o simulador. |
| M2–M11 — implementação | Oito agentes em paralelo, cada um com arquivos próprios (domínio e dados, dois grupos de testes, base da UI, Home, câmera, fechadura/hub, README e CI), presos a um contrato de nomes e assinaturas. Um agente de build compilou tudo e corrigiu o que faltava. | 85 testes verdes na JVM e no simulador iOS; APK; `xcodebuild`; os três `grep` da regra de dependência vazios; nenhum `println`/`Log`/`NSLog`. |
| Verificação visual | O app rodou no simulador iOS contra um servidor local que imita o contrato medido (sem tocar em equipamento real), e 25 estados de tela foram capturados e comparados lado a lado com as referências. | Capturas × `design/*.jpg` e `design/app-*.png`. |
| Revisão | Sete revisores (três visuais, núcleo × código da especificação, testes, regras e armadilhas, documentação) → triagem → um verificador cético por achado, instruído a refutar → correção por dono de arquivo → novo build. 18 achados confirmados e corrigidos (ex.: cabeçalho do hub cortado no iPhone, stream iniciado duas vezes no Android, tela em branco com dois "voltar" rápidos, testes que não conferiam tudo o que prometiam); 7 rejeitados por contrariar a especificação. | Build e testes verdes depois de cada rodada. |
| M12 — aparelho real | Roteiro de 19 passos no Moto G9 Play com a API real; no iPhone 13 Pro físico (build assinado), token vencido, Home, vídeo ao vivo (1.º quadro em 39,2 s a frio), fechadura e hub com a API real. | Tabela "Verificação em aparelho" do README, com data, aparelho e resultado. |

## 3. O que só o aparelho real mostrou

- **Online de subdispositivo:** `/produtos/online/v1` responde `online:false` para a fechadura Zigbee consultada com o `ns` puro (como a especificação mandava) e `true` com o `ns` composto. Corrigido em `GdiDeviceProvider.isOnline`, com teste de regressão, e registrado no README.
- **Layout no iPhone de 390 pt:** o banner quebrava "Armazenamento" no meio e nomes de aparelhos viravam reticências. Textos passaram a reduzir a fonte até caber (`FitText`), e o aviso de erro da câmera passou a respeitar o selo e os botões do vídeo.
- **API real:** a primeira chamada a `criar-fluxo-video` pode voltar HTTP 500 transitório ("Tentar novamente" resolve); o 401 real hoje traz "Não autorizado, verifique os seus limites disponíveis".
- **Volume da fechadura:** a leitura (`/fechaduras/volume/v1`) foi investigada só com pedidos de leitura. O par `ns` composto + `idProduto` da fechadura — o mesmo que funciona para estado, bateria e online — é reconhecido pela API e responde 500 em todas as tentativas (~1,4 s); pares errados respondem 404 "Dispositivo não encontrado". Conclusão: o pedido está certo e a falha é da GDI para esta fechadura; o app mantém o aviso honesto e a escrita habilitada.
- **Android Studio:** o modelo do Gradle da IDE ainda tinha o pacote do assistente e tentava abrir `org.example.project/…MainActivity`; um Sync resolveu, sem mudança de código.

## 4. Auditoria final contra o enunciado

Seis auditores conferiram cada requisito do case (RF01–RF09, entregáveis e eixos de avaliação) contra código, testes e evidências de aparelho. Desse resultado vieram os últimos ajustes: código Java real no `androidApp` (interop Java ↔ Kotlin e teste JUnit em Java), comandos da fechadura e do volume no `viewModelScope`, comandos bloqueantes do libVLC fora da thread principal, Keychain `ThisDeviceOnly`, token de desenvolvimento embutido só em builds de debug, este diretório e o PDF completo.

## 5. Limites mantidos

- Nenhum comando físico (abrir, fechar, mudar volume) foi enviado à fechadura real: a ação é sobre equipamento de terceiros e depende de autorização explícita do dono da conta. Esses caminhos são cobertos por testes com a forma de resposta documentada.
- O token da conta de teste nunca foi impresso, versionado nem incluído nesta documentação; as chamadas de diagnóstico à API real leram o valor de `local.properties` sem exibi-lo.
- Push, visibilidade do repositório e envio do e-mail ficaram com o autor.

## 6. Modularização (14/10/2026)

Depois do case entregue, o projeto foi dividido em módulos Gradle sem mudar o comportamento do app: `core/domain`, `core/data`, `core/ui`, `core/player`, `core/testing`, `feature/token`, `feature/home`, `feature/camera`, `feature/device` e o umbrella `shared`, com plugins de convenção em `build-logic/` (padrão Now in Android, adaptado a KMP). Como foi feito e verificado:

| Etapa | O que foi feito | Como foi verificado |
|---|---|---|
| Mapa de dependências | Antes de mover qualquer arquivo, um script listou os `import` entre pacotes: `ui` → `domain`, `platform`, `player`; `data` → `domain`; `di` → tudo. O mapa virou o grafo de módulos. | Grafo sem ciclo; `domain` sem importar ninguém. |
| Esqueleto | `build-logic` com `casa.kmp.library` e `casa.kmp.compose`; `settings.gradle.kts` com `includeBuild` e os nove módulos; código movido por pacote (sem mudar nomes de pacote nos módulos `core`, para o diff ficar legível); `Koin.kt` dividido em `dataModule`/`platformModule` (`core/data`) e o agregador do umbrella. | `core/*` compilando e testando na JVM antes de qualquer feature existir. |
| Features em paralelo | Quatro agentes, um por feature, com o mesmo contrato: pacote `feature.<nome>`, ViewModel próprio recebendo só interfaces do domínio, rota `@Serializable`, extensão de `NavGraphBuilder` e módulo Koin; testes do `HomeViewModelTest` antigo repartidos. | Cada feature com `testAndroidHostTest` e `compileKotlinIosSimulatorArm64` verdes antes de o umbrella ser montado. |
| Umbrella | `App.kt` só com tema e `NavHost` chamando as extensões; `Koin.kt` só juntando módulos; framework `Shared` exportando `core/player` (o Swift implementa `NativeVideoPlayer`). | APK e framework do simulador iOS compilando; 110 testes (106 Kotlin + 4 Java) verdes; os quatro `grep` de regra de dependência vazios. |

Decisões que a divisão forçou, e por quê:

- **Um ViewModel por feature, escopado à entrada da pilha.** O `HomeViewModel` único sobrevivia ao app inteiro e por isso rodava os comandos físicos no `viewModelScope`. Com ViewModel por tela, o que não pode morrer com a tela (comando na fechadura, `stopLive` ao sair da câmera) passou para um `CoroutineScope` da aplicação registrado no Koin (`APP_SCOPE_NAME`); a trava "um comando físico por vez" virou um `MutableStateFlow` único do app, compartilhado pelas instâncias do `DeviceViewModel`. Os dois comportamentos têm teste: `stopLiveRunsInAppScopeAndSurvivesTheViewModel` e `theInFlightGuardIsSharedBetweenViewModelInstances`.
- **O aviso "token válido, mas não gravado" mudou de dono.** Antes era emitido pelo fluxo do token; agora a Home o deriva de `tokens.persisted` no `init` do seu ViewModel, porque token e Home são features diferentes.
- **`core/testing` como módulo.** Os fakes saíram do `commonTest` do `shared` para um módulo publicado só para testes, no padrão do Now in Android, para todos os módulos usarem os mesmos dublês.

Armadilhas encontradas:

- **Comentário de bloco aninhado.** Kotlin aninha `/* */`; um "feature/*" dentro de um comentário do plugin de convenção engoliu o script inteiro, que virou um plugin vazio: os módulos que o aplicavam ficavam sem alvos e sem acessores tipados, sem nenhuma mensagem de erro. Diagnóstico: `javap` no `.class` gerado mostrou um corpo sem instruções.
- **Acessores tipados e plugins de convenção.** Um plugin pré-compilado que aplica outro plugin pré-compilado não gera os acessores `kotlin { }` para quem o consome. Solução adotada: a configuração comum vive numa função Kotlin (`configureKmpLibrary()`) chamada pelos dois plugins, e cada módulo declara `alias(libs.plugins.kotlinMultiplatform)` ao lado do `id("casa.kmp.compose")` só para o Gradle gerar os acessores do script.
- **`compileSdk` e o alvo Android do AGP 9.** O bloco `android { }` dentro de `kotlin { }` é a extensão aninhada de nome `android` (`KotlinMultiplatformAndroidLibraryTarget`); configurá-la em código Kotlin exige `(kotlin as ExtensionAware).extensions.configure("android")`.

Depois da divisão, o app modular foi instalado com um token novo nos dois aparelhos físicos (14/10): no Moto G9 Play, token → Home → fechadura → hub → câmera ao vivo sem crash; no iPhone 13 Pro, Home e câmera ao vivo. O registro módulo a módulo (arquivos, origem, API pública, testes) está em [modularizacao.md](../modularizacao.md).

## 7. Testes de UI (15/10/2026)

O enunciado pede testes unitários e de UI. Os unitários existiam desde o início (106 Kotlin + 4 Java); os de UI entraram em 15/10, depois da modularização, no mesmo padrão "cada módulo testa o que é seu": uma classe `*ScreenUiTest` por feature, em `commonTest`, com a API de teste do Compose Multiplatform. Como foi feito e verificado:

| Etapa | O que foi feito | Como foi verificado |
|---|---|---|
| Fontes | Lida a página [Testing Compose Multiplatform UI](https://kotlinlang.org/docs/multiplatform/compose-test.html) da JetBrains: testes em `commonTest` com `org.jetbrains.compose.ui:ui-test`; no Android, `withDeviceTestBuilder { sourceSetTreeName = "test" }` para o source set de aparelho compilar o `commonTest`; `AndroidJUnitRunner`, `ui-test-junit4-android` e `ui-test-manifest`. | A versão do Jetpack Compose resolvida no classpath Android (`androidx.compose.ui:ui` 1.12.1) bate com a do Compose Multiplatform 1.12.1, então `ui-test-junit4-android` e `ui-test-manifest` ficaram em 1.12.1. |
| Convenção | Terceiro plugin, `casa.kmp.feature` (= `casa.kmp.compose` + `configureComposeUiTests()` + as dependências de toda feature). A configuração de Compose saiu do script para `configureComposeMultiplatform()`, chamada pelos dois plugins. O `build.gradle.kts` das quatro features caiu para o bloco `plugins` (a câmera acrescenta `core:player`). | Um teste de fumaça na tela do token passou no simulador iOS (`:feature:token:iosSimulatorArm64Test`) e no Moto (`:feature:token:connectedAndroidDeviceTest`, 8 testes) antes de qualquer outra tela ser escrita; `testAndroidHostTest` continuou com só os 7 unitários. |
| Quatro telas em paralelo | Quatro agentes, um por feature, com o mesmo contrato: 3 a 5 testes por tela, só texto visível e `contentDescription`, só fakes de `:core:testing`, sem rede, sem Koin, sem NavHost, sem compor o player, sem tocar código de produção. Um revisor por feature conferiu os textos contra a tela e o `git status`. | 20 testes verdes no simulador iOS; nenhuma alteração fora dos arquivos `*UiTest.kt` (nem em `core/testing`). |
| Aparelho | Os quatro `connectedAndroidDeviceTest` em série no Moto G9 Play, depois as suítes completas da JVM e do simulador iOS. | Moto: token 12, Home 12, câmera 8, dispositivo 14 testes (20 de UI + 26 unitários), 0 falhas, 2 min 58 s. JVM do host: 106 unitários, 0 de UI, 0 falhas. Simulador iOS: 106 unitários + 20 de UI, 0 falhas. Java: 4. |

Decisões, e por quê:

- **Testes de UI no `commonTest`, não num source set à parte.** É o que a documentação da JetBrains recomenda e o que faz o mesmo arquivo rodar no simulador iOS e no aparelho Android. O preço é que a JVM do host, sem Activity, não os executa: a convenção exclui as classes `*UiTest` de `testAndroidHostTest` pelo nome. Por isso o sufixo é regra.
- **Sem `testTag` nas telas.** Os testes usam só o que o usuário vê (texto e `contentDescription`); nenhuma tela mudou para ser testável. Quando um comportamento não tinha semântica observável (modo lista × grade, estado "Validando token…" com fakes síncronos), ficou de fora e está anotado no KDoc do teste.
- **O player nunca é composto.** No iOS o `CameraPlayer` exige o `NativeVideoPlayer` registrado pelo Swift; no Android carrega a libVLC. O teste da câmera mantém a sessão pendente (`awaitCancellation`) ou a faz falhar, e cobre o que vem antes do vídeo: etapa, erro, "Tentar novamente", avisos de PTZ/gravação/multiview, lente fixa, voltar.
- **Comandos da fechadura só no dublê.** O teste de dispositivo segura o círculo, cancela (nenhum comando) e confirma (exatamente um comando no `FakeLockController`). A regra do projeto de nunca confirmar um comando real continua valendo: o teste nunca chega à API.
- **Tamanho do canvas.** O `runComposeUiTest` do simulador iOS desenha em 1024 × 768 (paisagem); as telas mais altas são hospedadas num `Box` de 360 × 720 dp no teste e usam `performScrollTo()` antes de ler o rodapé, para o mesmo teste valer no Moto (360 × ~750 dp).

Armadilhas encontradas:

- **`composeResources` na variante de teste em aparelho.** O Compose Multiplatform 1.12.1 registra `copyAndroidDeviceTestComposeResourcesToAndroidAssets` sem configurar `outputDirectory`, e o Gradle recusa a task. A classe é `internal`, então `configureComposeUiTests()` alcança as propriedades pelo nome (`property("outputDirectory")`) e dá valores padrão vazios: as features não têm `composeResources`.
- **`runComposeUiTest` deprecated.** No `ui-test` 1.12.1 a função da raiz do pacote está marcada como deprecated em favor de `androidx.compose.ui.test.v2.runComposeUiTest` (com `StandardTestDispatcher` por padrão). As quatro classes usam a função antiga, de propósito, para a migração ser um passo só depois; fica como pendência.
- **`onNodeWithText` enxerga o texto digitado mesmo mascarado.** Para provar que o olho oculta o token, o teste do token lê a propriedade `EditableText` (o texto já transformado) com um matcher próprio.

Pendências desta etapa: migrar as quatro classes para `androidx.compose.ui.test.v2`; teste instrumentado de ponta a ponta no `androidApp` (token → Home com Koin de fakes) e alvo XCUITest no `iosApp`, que o desenho da arquitetura ainda marca como faltando.
