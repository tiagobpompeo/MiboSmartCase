# Casa Inteligente

App Android e iOS para a plataforma **Intelbras Casa Inteligente (API GDI)**, escrito uma única vez em **Kotlin Multiplatform** com a interface em **Compose Multiplatform**.

## O que é

O usuário gera um token temporário no portal do desenvolvedor da Intelbras (`open-casainteligente.intelbras.com.br` → **Contas → Token Temporário**, válido por cerca de 2 horas), cola o token no app e passa a ver os dispositivos da conta:

- **Lista de dispositivos** na ordem da API, com tipo inferido (câmera, fechadura, hub, outro), estado online e os rótulos "via hub" e "compartilhado"; filtro por origem e paginação configurável.
- **Câmeras**: vídeo ao vivo com progresso em %, etapa da conexão, chip "AO VIVO" e o tempo até o primeiro quadro; troca de lente na câmera Dual.
- **Fechadura**: estado da porta, bateria, online, abertura remota habilitada; abrir/fechar com "pressione e segure" e confirmação; volume (Mudo/Baixo/Médio/Alto); histórico de aberturas.
- **Hub**: estado, subdispositivos e firmware.

A aparência segue o app oficial Mibo Smart. Todo controle visível faz algo real ou avisa, num Snackbar, que a API GDI não oferece aquele recurso: o app não tem dados de demonstração.

O token nunca está no repositório. Ele entra pela tela de token e só é gravado (Keystore no Android, Keychain no iOS) depois que a primeira página da listagem responde com sucesso.

---

## Como rodar

### Pré-requisitos

| Item | Versão |
|---|---|
| Mac | Apple Silicon (não há target `x86_64` de simulador) |
| Android Studio | 2026.2 + plugin **Kotlin Multiplatform** |
| Xcode | 27.0 |
| Gradle | wrapper 9.5.0; o daemon usa JDK 25, baixado sozinho pelo toolchain (foojay) |
| Android SDK | compileSdk 37 |

### JDK no terminal

O Mac de desenvolvimento não tem JDK de sistema. Antes de qualquer `./gradlew` ou `xcodebuild` no terminal, use o JBR do Android Studio:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Pela interface do Xcode, a fase "Compile Kotlin Framework" chama o Gradle no ambiente do próprio Xcode, que não herda o `export` do terminal. Feche o Xcode e abra o projeto pelo terminal já com o JDK: `open --env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" iosApp/iosApp.xcodeproj`. Como alternativa, use `launchctl setenv JAVA_HOME "/Applications/Android Studio.app/Contents/jbr/Contents/Home"` e reabra o Xcode (vale até o logout).

Rodando o iOS pelo Android Studio isso não é necessário: a IDE compila o framework e o script do Xcode pula o Gradle (`OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`).

### `local.properties` (fora do Git)

```properties
sdk.dir=/Users/<voce>/Library/Android/sdk
# Opcional: só pré-preenche a tela de token em desenvolvimento (o token vale cerca de 2 h).
gdi.token = <token temporário gerado no portal>
# Opcional; este já é o padrão:
# gdi.baseUrl=https://api-casainteligente.intelbras.com.br
```

- `gdi.token` **nunca faz login automático**: o valor aparece no campo com o aviso "Pré-preenchido pelo local.properties (desenvolvimento)" e o usuário ainda precisa tocar em "Entrar".
- A task `generateGdiConfig` lê o arquivo e gera `GdiBuildConfig` (uma biblioteca KMP não tem `BuildConfig`). O iOS usa o **mesmo** arquivo, porque o Xcode chama o Gradle na raiz. Se a IDE marcar `GdiBuildConfig` em vermelho antes do primeiro build: `./gradlew :shared:generateGdiConfig`.
- No CI não existe `local.properties`: o token gerado é `""` e os testes usam o `MockEngine` do Ktor. O token GDI nunca é segredo do CI.

### Android

- **Android Studio:** configuração de execução **androidApp**. Prefira um aparelho físico: no emulador, a decodificação H.265 por software é o gargalo do vídeo.
- **Terminal:** `./gradlew :androidApp:installDebug`

### iOS

- **Android Studio:** configuração **iosApp** (tipo Xcode Application, esquema `iosApp`, Debug), criada pelo plugin Kotlin Multiplatform.
- **Xcode:** abra `iosApp/iosApp.xcodeproj`, esquema `iosApp`. O primeiro build baixa o VLCKit pelo Swift Package Manager (pacote grande).
- **Team:** `AH3MDC3666`, já gravado em `DEVELOPMENT_TEAM` no `project.pbxproj` e em `TEAM_ID` no `Config.xcconfig`. Precisa estar no pbxproj porque a configuração do Android Studio passa `DEVELOPMENT_TEAM=""`.
- **Armadilha do Team ID:** o código entre parênteses em "Apple Development: Nome (XXXXXXXXXX)" é o **ID do certificado**, não o Team. Usá-lo como `DEVELOPMENT_TEAM` faz a assinatura falhar. O Team ID aparece na conta Apple (Xcode → Settings → Accounts) ou em Signing & Capabilities.
- **iPhone físico:** ative o Modo Desenvolvedor no aparelho. Pelo terminal:

  ```sh
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -destination id=<UDID> -allowProvisioningUpdates build
  ```

- **Simulador e CI (sem assinatura):**

  ```sh
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
  ```

### Binário sem token

O token de `local.properties` só entra em builds de debug. Um build de release (`assembleRelease`, `bundleRelease`, framework iOS em `Release`) gera `GdiBuildConfig.TOKEN = ""` sem nenhum parâmetro:

```sh
./gradlew :androidApp:assembleRelease
```

Para forçar um ou outro comportamento: `-PgdiDevToken=false` (nunca embute) ou `-PgdiDevToken=true` (sempre embute). Confira em `shared/build/generated/gdi/commonMain/kotlin/br/com/pompeo/casa/GdiBuildConfig.kt`.

---

## Stack e versões em uso

| Item | Versão |
|---|---|
| Kotlin | **2.4.20** |
| Compose Multiplatform | **1.12.1** |
| compose-material3 | **1.12.0-alpha03** |
| compose-material-icons (core + extended) | 1.7.3 |
| Android Gradle Plugin | 9.1.1 (`com.android.kotlin.multiplatform.library` no `shared`) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| activity-compose | 1.13.0 |
| lifecycle JetBrains (`org.jetbrains.androidx.lifecycle`) | 2.11.0 |
| navigation-compose JetBrains | 2.9.2 |
| Koin | 4.2.2 |
| Ktor | 3.5.1 (OkHttp no Android, Darwin no iOS, Mock nos testes) |
| kotlinx-coroutines / kotlinx-serialization | 1.11.0 / 1.9.0 |
| libVLC Android (`org.videolan.android:libvlc-all`) | 3.7.7 |
| VLCKit iOS (SPM `tylerjonesio/vlckit-spm`, produto `VLCKitSPM`) | 3.6.0 (até a próxima major) |
| iOS deployment target | 16.0 (iPhone, retrato) |

**Sobre Kotlin, Compose Multiplatform e material3.** O assistente de projeto do Android Studio gerou Kotlin 2.4.20 e Compose Multiplatform 1.12.1, e o material3 acompanha essa linha (1.12.0-alpha03): versões mais novas que a combinação validada de reserva (Kotlin **2.4.10**, Compose Multiplatform **1.11.1**, material3 1.9.0). A regra adotada foi manter o que o assistente gerou enquanto todas as dependências resolvessem e o build passasse; tudo resolveu e compilou no Android e no iOS, então as versões ficaram. Se aparecer incompatibilidade, o caminho é voltar para 2.4.10 / 1.11.1, e não procurar outra combinação. O material3 é alpha: é o primeiro suspeito se algum componente se comportar de forma estranha.

Fora de propósito: **sem Media3** (o RTSP da nuvem não é aceito por ele; ver Decisões) e sem `compose-components-resources` (o app não usa `composeResources`).

---

## Testes

**91 testes** Kotlin em 14 classes de `shared/src/commonTest/kotlin/br/com/pompeo/casa/` (meta do projeto: ≥ 70), rodando na JVM e no simulador iOS: os 85 da lista da [especificação](docs/sdd/PROMPT-CASE-KMP.md), um de regressão para um fato da API medido no aparelho (`onlineUsesCompositeNsOnlyForSubdevices`) e cinco dos comandos da fechadura no `viewModelScope`. Usam `kotlin.test`, `kotlinx-coroutines-test` e `ktor-client-mock`, sem MockK: os dublês são fakes das interfaces do domínio (`TestSupport.kt`) e todo token é obviamente falso. Mais **4 testes em Java** (JUnit 4) no `androidApp`, que chamam a regra Kotlin `TokenFormat` como método estático (`@JvmStatic`).

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:testAndroidHostTest      # JVM do host, sem emulador
./gradlew :shared:iosSimulatorArm64Test    # binário de teste no simulador iOS
./gradlew :androidApp:testDebugUnitTest    # testes em Java (JUnit 4)
```

Relatório do Android: `shared/build/reports/tests/testAndroidHostTest/index.html`. O workflow de CI roda as duas suítes a cada push; hoje o job iOS passa e o job Android falha na preparação do SDK (ver "Limitações conhecidas").

| Classe | Testes | O que cobre |
|---|---:|---|
| `TokenFormatTest` | 3 | normalização, plausibilidade e máscara do token |
| `DomainModelTest` | 4 | origem, filtro, `hasMore`, escala de volume |
| `TokenRepositoryImplTest` | 7 | sessão em memória × persistido, falha do armazenamento seguro |
| `GdiApiTest` | 19 | Bearer, corpo, `text/plain`, renovação única (inclusive em paralelo), fechadura, vídeo, online de subdispositivo, erros reais |
| `GdiDeviceParserTest` | 5 | envelopes e nomes de campo alternativos, dedup, tipos |
| `GdiRealResponseTest` | 2 | resposta real anonimizada, `ns` composto, data formatada |
| `GdiErrorMapperTest` | 5 | token ausente/inválido/expirado, rede, timeout, servidor |
| `GdiFormatTest` | 2 | data/hora e tipos de evento da fechadura |
| `DeviceRepositoryImplTest` | 17 | paginação, filtro com fallback, geração, cancelamento, logout, dois parceiros |
| `SubmitTokenUseCaseTest` | 8 | validar antes de gravar, renovação durante a validação, cancelamento |
| `ChangeLockVolumeUseCaseTest` | 3 | grava, relê e assume o pedido se a releitura falhar |
| `HomeViewModelTest` | 10 | validação em `viewModelScope`, aviso de armazenamento, cancelamento, comandos da fechadura e do volume no `viewModelScope` (um por vez, erro e volume aplicado) |
| `MiboLogicTest` | 5 | lentes, faixa da semana, rótulos |
| `CameraFormatTest` | 1 | "1º quadro em X s" |
| `TokenFormatJavaTest` (Java, `androidApp`) | 4 | a mesma regra do token chamada de Java: máscara, plausibilidade, normalização |

---

## Arquitetura

- **KMP + Compose Multiplatform:** um módulo `shared` com dados, domínio e UI em `commonMain`; `androidMain`, `iosMain` e Swift só com o que é nativo (armazenamento seguro, player de vídeo, calendário, debug/release). A mesma UI em Compose roda no Android (`MainActivity`) e no iOS (`MainViewController`).
- **MVVM:** cada tela é um `@Composable` que desenha estado imutável coletado com `collectAsStateWithLifecycle` e chama funções do `HomeViewModel` (`StateFlow`, `viewModelScope`). Comandos físicos (abrir/fechar a fechadura, mudar o volume) também rodam no `viewModelScope` e a tela só observa `lockCommand`: girar o aparelho ou sair da tela no meio do envio não cancela um pedido que talvez já tenha chegado à fechadura. A navegação usa rotas tipadas e callbacks; o ViewModel não conhece o `NavController`.
- **Clean Architecture:** `ui` → `domain` ← `data`. O `domain` é Kotlin puro (só coroutines): modelos, `DevicesState`, `AppError` selado, portas (`TokenRepository`, `DeviceRepository`, `DeviceProvider`, `LockController`, `TokenStorage`, `ErrorMapper`) e casos de uso. `di/Koin.kt` é a raiz de composição e registra cada `*Impl` pela interface.
- **Só dois casos de uso**, onde há regra de negócio: `SubmitTokenUseCase` (validar o formato, testar o token carregando a 1.ª página, gravar só em sucesso, sair em erro ou cancelamento) e `ChangeLockVolumeUseCase` (gravar, reler e assumir o valor pedido se a releitura falhar). Listar, filtrar, paginar, vídeo e leituras da fechadura seriam casos de uso de uma linha, sem regra; o ViewModel chama a interface do repositório diretamente.
- **Regra de dependência conferida** (os três comandos têm de vir vazios):

```sh
SRC=shared/src/commonMain/kotlin/br/com/pompeo/casa
grep -rnE "^import (io\.ktor|org\.koin|androidx|org\.jetbrains\.compose|kotlinx\.serialization|br\.com\.pompeo\.casa\.(data|ui|di|player|platform))" "$SRC/domain"
grep -rnE "^import (br\.com\.pompeo\.casa\.data|io\.ktor)" "$SRC/ui" "$SRC/player" "$SRC/App.kt"
grep -n "^import br\.com\.pompeo\.casa\." "$SRC/ui/HomeViewModel.kt" | grep -v "br\.com\.pompeo\.casa\.domain\."
```

---

## Decisões

- **Ktor com parse manual.** A GDI serve JSON com `content-type: text/plain`, que o `ContentNegotiation` não converte. A resposta é lida com `bodyAsText()` e `Json.parseToJsonElement`; o parser tolera corpo de erro que é uma string JSON, envelope `"status":"erro"` dentro de HTTP 200 e nomes de campo alternativos. O corpo do pedido é montado com `buildJsonObject`, porque os três campos da listagem são obrigatórios (sem `origem` a API responde 500).
- **libVLC (Android) e VLCKit (iOS), não Media3 nem AVPlayer.** A API devolve um RTSP de um proxy da nuvem cujo SDP anuncia H.265 e AAC **sem nenhuma linha `a=fmtp`** (os parâmetros do H.265 vêm dentro do fluxo). O RTSP do Media3 recusa esse SDP ("missing attribute fmtp") e o AVPlayer não toca RTSP. O VLC aceita. Ajustes medidos: decodificação por software desde o 1.º pacote (o decoder de hardware precisaria da resolução que viria do `fmtp`), áudio desligado, buffer de 800 ms no Android (`LIVE_NETWORK_CACHING_MS`) e todas as chamadas bloqueantes do libVLC (`stop`, `play`, `release`) numa única thread de controle (`vlc-control`), na ordem em que foram pedidas: nem sair da tela nem ir para background trava a main thread (evita ANR). No iOS o VLCKit fica em Swift, implementando uma interface Kotlin (`NativeVideoPlayer`): inversão de dependência, sem cinterop com o VLCKit.
- **Android Keystore com AES-GCM, direto.** Chave AES-256 que nunca sai do Keystore; `iv:ciphertext` em SharedPreferences privadas. A `security-crypto` (EncryptedSharedPreferences) foi descontinuada pela Google; o Keystore direto não traz dependência extra. Sem `setUserAuthenticationRequired` (invalidaria a chave ao trocar o bloqueio de tela) e com `allowBackup="false"`.
- **Keychain via `platform.Security`.** Acesso pelo cinterop que o Kotlin/Native já traz, com `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: o token não migra para outro aparelho por backup, como o `allowBackup="false"` no Android. Os testes iOS usam armazenamento em memória, porque o executável de teste não tem o entitlement do Keychain.
- **Koin, não Hilt.** O Hilt só existe no Android; o Koin roda em `commonMain` e entrega o ViewModel com `koinViewModel`.
- **Lista de `DeviceProvider`.** Cada parceiro implementa `DeviceProvider` (e `LockController`, se tiver fechaduras); o repositório recebe **todos** pelo Koin (`getAll()`), junta as páginas e delega cada operação ao parceiro dono do dispositivo (`Device.providerId`).
- **Renovação do token serializada com `Mutex`.** Em 401/403 o app renova uma única vez e repete a chamada. Chamadas paralelas fazem uma só renovação ("o token já mudou? então só repete"), porque o token antigo deixa de valer e uma segunda renovação seria recusada. Se a renovação falhar, vale o status original (403 → expirado) e o app pede um token novo.
- **Concorrência na listagem.** `Mutex` para escritas, contador de geração para que a resposta de um filtro antigo nunca apareça, e `Job`s separados para que trocar o filtro cancele em vez de esperar. `CancellationException` é sempre relançada (`resultOf` no lugar de `runCatching`).
- **Nativo por `expect`/`actual` de funções e vals de topo**, sem `expect class`, que exigiria flag de compilador.
- **Java na borda Android** (ver "Interoperabilidade com Java"): a regra de negócio fica em Kotlin, no `commonMain`, e o `androidApp` tem código e teste em Java chamando essa regra.

---

## Limitações conhecidas

- **Comandos físicos nunca executados no equipamento real.** Abrir/fechar a fechadura e mudar o volume agem num aparelho de terceiros; foram validados com a resposta documentada (`{"status":"sucesso"}` sem `data`) nos testes. No app, todo comando passa por um diálogo de confirmação.
- **Leitura de volume dá HTTP 500** na fechadura de teste (medido em 03, 05 e 06/10/2026). Em 06/10 foram testadas variações do pedido, só de leitura: com o par certo (`ns` composto + `idProduto` da fechadura, o mesmo que funciona para estado, bateria e online) a API reconhece o aparelho e responde 500 "Erro desconhecido" em ~1,4 s, em todas as tentativas; com um par errado (`ns` puro + `idProduto` da fechadura, ou `ns` composto + `idProduto` do hub) responde 404 "Dispositivo não encontrado". O formato do pedido está certo, e a falha é da GDI para esta fechadura. A tela mostra "Volume indisponível (a API respondeu HTTP 500)" com "Tentar de novo" e mantém a escrita habilitada; depois de gravar, se a releitura falhar, assume o valor pedido.
- **Mesma imagem nas duas lentes** da câmera Dual: a GDI devolve o mesmo RTSP para `canalVideo` 0 e 1. O app envia o canal certo e avisa na tela.
- **Partida a frio do proxy RTSP da nuvem:** cerca de 21 s até o 1.º quadro no Android (igual em qualquer cliente; a quente, cerca de 3 s). No iOS houve uma única medição a frio de 37,8 s. O iOS mantém buffer de 300 ms + `clock-jitter 0`; alinhar com os 800 ms do Android é **hipótese não testada**.
- **Sem eventos em tempo real.** Movimento, campainha e aberturas chegam só por webhook, que exige backend; as abas de mensagens dizem isso.
- **Token em `const val` é extraível.** Um valor de `local.properties` compilado num APK ou binário pode ser lido por quem tiver o arquivo. Aceitável só em demo; por isso só os builds de debug embutem o valor, e o release sai sem ele. Em produção, o token fica num backend e o app recebe só URLs de vídeo de curta duração.
- **Áudio desativado:** o AAC vem sem `config` no SDP e vira ruído. O botão de mudo alterna, mas não tem efeito audível.
- **URL RTSP expira** (`expire`): depois de muito tempo em background a reconexão pode falhar; aparece o erro com "Tentar novamente", que pede uma sessão nova.
- **iOS só para o player no dispose da tela.** O Android para em `ON_STOP` e reconecta em `ON_START`. No iOS o `stop()` do VLCKit roda na main thread (risco de travar ao sair, não observado).
- **APK de cerca de 165 MB** em debug com 3 ABIs, porque o libVLC traz um `.so` grande por ABI.
- **CI:** o workflow roda a cada push; o job iOS passa, e o job Android falha hoje na preparação do SDK no runner (as suítes rodam verdes localmente e nos aparelhos).
- **Licença LGPL 2.1+** do libVLC e do VLCKit: falta tela de licenças, e a distribuição pede revisão jurídica, sobretudo no iOS (linkagem com o app).
- **Tipo de dispositivo inferido** pelo prefixo de modelo/nome (a API não informa categoria). Medido para câmera (`iM`), fechadura (`MFR`) e hub (`MCA`/`IOT-ZG`); os demais prefixos e parte dos tipos de evento do histórico são hipótese.
- **`criar-fluxo-video` pode responder HTTP 500 transitório:** em 05/10/2026 a primeira chamada a frio voltou 500; a tela mostrou o erro amigável e "Tentar novamente" (que pede uma sessão nova) abriu o vídeo.
- Rotas de cota e sessões de streaming respondem 403 na conta de teste (sem plano) e não são usadas; sem `session_id` na resposta, não há sessão a encerrar (a URL expira sozinha).

---

## Verificação em aparelho

**Estado: executado em 05 e 06/10/2026 no Moto G9 Play (Android 11) e no iPhone 13 Pro com a API real e a conta de teste** (iPhone 13 Pro físico em 06/10/2026, com a API real: token vencido, Home, vídeo ao vivo, fechadura e hub). Na linha 8, o caso (a), sem rede, foi conferido no iPhone físico; o caso (b), erro ao carregar mais, só contra um servidor local que imita o contrato da seção "Contrato real × Swagger". Nenhum comando de fechadura ou de volume foi confirmado.

Preparação: apagar os dados do app (`adb shell pm clear br.com.pompeo.casa`; no iPhone, apagar e reinstalar), gerar um token novo no portal e colocá-lo em `local.properties` ou colá-lo na tela. Comandos de fechadura e de volume só são confirmados com autorização explícita do dono da conta.

| # | RF | Passos | Deve aparecer | Data/hora | Aparelho | Resultado |
|---|---|---|---|---|---|---|
| 1 | RF01 | Abrir o app sem dados | "Conectar à conta Intelbras", escudo verde, campo "Token de acesso" mascarado; com `local.properties`, "Pré-preenchido pelo local.properties (desenvolvimento)" | 05/10 23:27 | Moto G9 Play | OK: igual a `app-01`; sem `gdi.token`, "Entrar" desabilitado; com ele, o aviso de pré-preenchimento |
| 2 | RF04 | "Entrar" com um token expirado | "Seu token expirou (ele vale cerca de 2 horas)…", sem sair da tela | 06/10 11:27 | iPhone 13 Pro | OK com a API real: o token salvo venceu e a Home mostrou "Seu token expirou (ele vale cerca de 2 horas)…" (403; a renovação respondeu 400). Na tela de token, conferido só contra o mock |
| 3 | RF04 | "Entrar" com um texto qualquer de 8 ou mais caracteres | "Token inválido ou expirado. Gere um novo token…" | 05/10 23:27 | Moto G9 Play | OK: "Token inválido ou expirado…" (a API real respondeu 401 "Não autorizado, verifique os seus limites disponíveis") |
| 4 | RF01/RF02 | "Entrar" com o token válido | "Validando token…" no botão → Home com a câmera iM4 Dual (badge 2), a fechadura MFR 2030 (via hub) e o hub MCA 1002 | 05/10 23:33 | Moto G9 Play; simulador iOS | OK: iM4 Dual (badge 2), MFR 2030 (via hub), MCA 1002, igual a `app-03` |
| 5 | RF01 | Fechar e reabrir o app | Vai direto para a Home (token do Keystore/Keychain) | 05/10 23:52 | Moto G9 Play | OK: abriu direto na Home |
| 6 | RF07 | Chips Compartilhados → Vinculados → Todos | Compartilhados: "Nenhum dispositivo encontrado" + "Nenhum dispositivo compartilhado com esta conta."; os outros: 3 dispositivos | 05/10 23:53 | Moto G9 Play | OK: Compartilhados vazio com o texto do filtro (igual a `app-05`); Vinculados e Todos com 3 |
| 7 | RF08 | Definições → Itens por página 2 → Início → "Carregar mais" | "1 página(s) · 2 dispositivo(s) · 2 por página" + "Carregar mais" → "2 página(s) · 3 dispositivo(s) · 2 por página" + "Sem mais informações" | 05/10 23:54 | Moto G9 Play | OK: "1 página(s) · 2 dispositivo(s) · 2 por página" → "2 página(s) · 3 dispositivo(s) · 2 por página" + "Sem mais informações" |
| 8 | RF04/RF08 | (a) Modo avião → Definições → Atualizar → Início. (b) Sem modo avião, 2 por página e só a 1.ª página → ligar o modo avião → "Carregar mais" | (a) Cartão "Sem conexão com a internet…" com "Tentar novamente"; a lista anterior não some das telas de detalhe. (b) Os 2 itens continuam; abaixo, "Sem conexão…" + "Tentar novamente" (que, sem modo avião, carrega a página 2) | 06/10 12:58 | iPhone 13 Pro | (a) OK no iPhone físico, sem rede real: Definições → Atualizar → Início mostrou "Sem conexão com a internet. Verifique a rede e tente novamente." com "Tentar novamente" (o detalhe técnico do iOS só aparece porque o build é de debug). (b) conferido só contra o mock no simulador |
| 9 | RF03 | Abrir a iM4 Dual (a frio) | % e etapa sobem; depois "AO VIVO" e "1º quadro em ~21 s" | 05/10 23:34 | Moto G9 Play; simulador iOS | OK: % e etapa, "AO VIVO", "1º quadro em 22,6 s" a frio no Android (a 1.ª sessão voltou HTTP 500 transitório; "Tentar novamente" resolveu); iOS com VLCKit: 2,4 s com o proxy já aquecido |
| 10 | RF03 | Voltar e abrir de novo | "1º quadro em ~3 s" | 05/10 23:35 | Moto G9 Play | OK: "1º quadro em 3,6 s"; sair da tela sem ANR e sem "late video" no logcat |
| 11 | RF03 | "Lente fixa" | Reinicia (~3 s) e mostra "A API GDI devolve o mesmo vídeo para as duas lentes desta câmera." | 05/10 23:54 | Moto G9 Play | OK: reiniciou em 2,3 s e mostrou a nota das lentes |
| 12 | RF03 | Tela cheia, mudo, PTZ, gravar, microfone, foto | Tela cheia não reinicia o vídeo; mudo alterna (sem efeito audível: áudio desativado); os demais mostram o Snackbar "Indisponível na API GDI"/"PTZ não disponível na API GDI" | 05/10 23:55 | Moto G9 Play | OK: tela cheia sem reiniciar o vídeo; PTZ mostra "PTZ não disponível na API GDI" |
| 13 | RF05 | Abrir a MFR 2030 | "Porta fechada", bateria %, Online, "Pressione e segure para abrir a porta." | 05/10 23:52 | Moto G9 Play | OK após correção: 26 %, Online; a porta estava aberta ("Porta aberta" + "Pressione e segure para trancar a porta."). Antes da correção aparecia "Offline" (ver "Contrato real × Swagger") |
| 14 | RF05 | Segurar o círculo 600 ms | Diálogo "Destrancar a fechadura?" → **Cancelar** | 05/10 23:56 | Moto G9 Play | OK: "Trancar a fechadura?" (porta aberta); fechado com Voltar, nenhum comando enviado |
| 15 | RF06 | Cartão Volume | "Volume indisponível (a API respondeu HTTP 500)" + "Tentar de novo"; Mudo/Baixo/Médio/Alto habilitados (não tocar sem autorização) | 05/10 23:37 | Moto G9 Play | OK: "Volume indisponível (a API respondeu HTTP 500)" + "Tentar de novo"; níveis habilitados e não tocados |
| 16 | RF09 | Histórico | Eventos "Remoto (APP)"/"Por dentro (manual)" com data e hora; "Ver mais" se houver 10 | 05/10 23:37 | Moto G9 Play | OK: 10 eventos reais "Remoto (APP)" com data e hora |
| 17 | — | Hub MCA 1002 | Online, faixa da semana com hoje em verde, 1 subdispositivo, "Firmware 2.4.628243" com "Atualizado" | 05/10 23:37 | Moto G9 Play; simulador iOS | OK: Online, faixa da semana com hoje em verde, 1 subdispositivo, "Firmware 2.4.628243" "Atualizado" (igual a `app-18`) |
| 18 | — | Definições → Sair | Volta à tela de token; o "voltar" do sistema não retorna à Home | 05/10 23:56 | Moto G9 Play | OK: voltou ao token; o "voltar" saiu do app; reabrir pediu o token de novo |
| 19 | — | Repetir 4, 9, 13 e 17 no iPhone | Mesmo comportamento; vídeo via VLCKit (1.º quadro a frio pode passar de 30 s) | 06/10 12:53 | iPhone 13 Pro | OK no iPhone físico. 4: iM4 Dual (badge 2), MFR 2030 (via hub), MCA 1002; banner e nomes inteiros na tela de 390 pt. 9: "AO VIVO", "1º quadro em 39,2 s" a frio via VLCKit (o relógio da câmera avançava, e o Moto G9 mostrou a mesma cena no mesmo momento). 13: bateria 26 %, Online, "Porta aberta", volume com o HTTP 500 real e níveis habilitados (não tocados). 17: Online, faixa da semana com hoje em verde, 1 subdispositivo, "Firmware 2.4.628243" "Atualizado" |

Passo final: capturar cada tela no Android (`adb exec-out screencap -p > tela-<nome>.png`) e no iPhone e comparar lado a lado com as capturas de referência do Mibo Smart. Só são aceitas diferenças de recurso que a GDI não oferece ou de conteúdo de outra conta. Depois, salvar as telas finais em `docs/telas/` (`adb exec-out screencap -p > docs/telas/android-<tela>.png`; no iPhone, botões laterais, salvando como `docs/telas/ios-<tela>.png`) e referenciá-las neste README.

---

## Produto e arquitetura

### Problema e público

Quem tem câmeras, fechadura e hub Intelbras quer, num só app, ver a câmera ao vivo e saber se a porta está fechada, sem depender do app do fabricante para cada integração. A API GDI abre a plataforma Casa Inteligente para parceiros; este app é um cliente de parceiro: autentica com o token temporário da conta, lista os dispositivos e opera câmera e fechadura com a mesma aparência do app oficial. O público é o morador (uso diário, telas claras, retorno em cada estado) e, do lado técnico, o integrador que precisa acrescentar novos parceiros sem reescrever a UI.

### Requisitos e estado

| RF | Entrega | Estado |
|---|---|---|
| RF01 | Tela de token: campo mascarado com mostrar/ocultar; "Entrar" só com token plausível; gravado cifrado só depois que a 1.ª página responde; reabrir vai direto à Home | Feito |
| RF02 | Lista na ordem da API com nome, tipo inferido, online, "via hub" e "compartilhado" | Feito |
| RF03 | Vídeo ao vivo com %, etapa, "AO VIVO" e tempo do 1.º quadro; libera player e sessão ao sair; lentes da Dual; Android e iOS | Feito |
| RF04 | Mensagens distintas para token ausente, inválido (401), expirado (403), sem rede, timeout, erro do servidor e lista vazia por filtro; detalhe técnico só em debug | Feito |
| RF05 | Fechadura: estado, bateria, online, abertura remota; segurar 600 ms → confirmação → comando → releitura | Feito sem execução física (o comando nunca foi enviado ao equipamento real) |
| RF06 | Volume Mudo/Baixo/Médio/Alto; erro de leitura honesto com escrita habilitada; grava e relê | Feito, com limitação da API: a GDI responde 500 à leitura do volume desta fechadura (pedido conferido, ver "Contrato real × Swagger"); a escrita nunca foi enviada ao equipamento real |
| RF07 | Filtro Todos/Vinculados/Compartilhados no servidor; recomeça na página 1; resposta de filtro antigo descartada | Feito |
| RF08 | Itens por página 2/5/10/50, "Carregar mais", "Sem mais informações", contagem; erro ao carregar mais mantém a lista | Feito |
| RF09 | Histórico com 10 eventos, "Ver mais" com 30; vazio e erro com textos diferentes | Feito |

O roteiro de 19 passos (RF01–RF09) foi executado no Android físico com a API real em 05/10/2026, com comparação lado a lado com as capturas de referência; no iPhone físico, token vencido, Home, vídeo ao vivo, fechadura e hub foram conferidos com a API real em 06/10/2026. Pendente: o erro ao "Carregar mais" sem rede (linha 8b), conferido só contra o servidor local (detalhes na seção "Verificação em aparelho" do README do repositório).

### Camadas

```
   apresentação (pacote ui/ + App.kt + player/)          dados (pacote data/)
   Composables ── eventos ──► HomeViewModel               TokenRepositoryImpl · DeviceRepositoryImpl
        ▲                        │  StateFlow (estado)     GdiApi (Ktor) · GdiDeviceProvider · GdiErrorMapper
        └──── estado imutável ───┘                         KeystoreTokenStorage / KeychainTokenStorage
                                 │ depende de                      │ implementa
                                 ▼                                 ▼
               ┌──────────────────── domain/ (Kotlin puro) ────────────────────┐
               │ modelos: Device, DeviceQuery, DevicePage, LockDetails, …       │
               │ estados/erros: DevicesState, AppError                          │
               │ portas: TokenRepository, DeviceRepository, DeviceProvider,     │
               │         LockController, TokenStorage, ErrorMapper              │
               │ casos de uso: SubmitTokenUseCase, ChangeLockVolumeUseCase      │
               └────────────────────────────────────────────────────────────────┘
   di/Koin.kt (raiz de composição) é o único lugar que conhece as três camadas e liga interface → implementação.
```

Só `data/gdi` conhece a GDI (rotas, nomes de campo, códigos de erro, formato de data); o que sai de lá já é modelo do domínio, com textos prontos para exibir. Toda falha vira `AppError` (selado, `when` exaustivo) num único lugar, o `GdiErrorMapper`. A UI recebe só interfaces do domínio por injeção.

### Múltiplos parceiros

`DeviceProvider` é a porta de um parceiro (listar página, vídeo, firmware, online e, opcionalmente, um `LockController`). A GDI é um provider (`GdiDeviceProvider`). Um parceiro novo é um `single<DeviceProvider>(named("..."))` a mais no Koin: o `DeviceRepositoryImpl` recebe a lista com `getAll()`, pede a mesma página a todos, junta o resultado e encaminha cada operação ao parceiro dono do dispositivo pelo `providerId`. UI, ViewModel e repositório não mudam.

### Modularização

Hoje: um módulo Gradle `shared` (mais `androidApp` e `iosApp`), com fronteiras por pacote (`ui` → `domain` ← `data`) conferidas por três buscas de `import` que têm de vir vazias: `domain` sem Ktor, Koin, Compose, `androidx`, `kotlinx.serialization` nem outras camadas; `ui`, `player` e `App.kt` sem `data` nem Ktor; `HomeViewModel` só com imports de `domain`. Para o tamanho do case isso mantém o build simples. Quando o app crescer, a divisão prevista é:

| Módulo | Conteúdo |
|---|---|
| `:core:domain` | Kotlin puro: modelos, estados, `AppError`, portas e casos de uso |
| `:data:gdi` | cliente, parser e mapeador de erro da GDI; registra seu `DeviceProvider` no Koin (um módulo por parceiro) |
| `:feature:home` | token, Home e Definições |
| `:feature:camera` | tela de câmera e player |
| `:feature:lock` | fechadura e hub |

`androidApp` e `iosApp` passam a depender só das features, e cada feature ganha o seu ViewModel no lugar do `HomeViewModel` único.

### Interoperabilidade com Java

`commonMain` não compila Java, e uma regra escrita em Java teria de ser duplicada para o iOS; por isso as regras de negócio são Kotlin. A interop com Java acontece na borda Android, nos dois sentidos:

- **Java chamando Kotlin** (`androidApp`): `CasaApplication.java` inicia o Koin chamando a função de topo `initKoin` (no bytecode, o estático `KoinKt.initKoin`), passa o tipo-função `KoinApplication.() -> Unit` como `Function1` que devolve `Unit.INSTANCE` e chama a extensão `androidContext` como estático que recebe o receptor no primeiro argumento. `TokenFormatJavaTest.java` (JUnit 4) chama a regra do token como `TokenFormat.mask(...)`, graças ao `@JvmStatic` no `object` Kotlin.
- **Kotlin chamando Java** (`androidMain`): `java.security.KeyStore` e `javax.crypto` (armazenamento do token), `java.time` (faixa da semana), `java.util.concurrent` (thread de controle do libVLC), a API Java do libVLC (`org.videolan.libvlc`, JNI por baixo) e `android.util.Base64`. Em `commonMain`, `kotlinx.io.IOException` é `typealias` de `java.io.IOException` na JVM, o que define a ordem dos ramos no mapeador de erro.

Do lado iOS, a interop é Kotlin/Native ↔ Swift (interface Kotlin implementada em Swift) e cinterop com `platform.Security`.

### Segurança do token

- Nunca em arquivo versionado; `local.properties` é ignorado pelo Git e só pré-preenche a tela, sem login automático. `git grep -n "Ot_"` mostra só tokens falsos de teste.
- Gravado só depois de validado pela 1.ª página; erro ou cancelamento na validação apaga o token da sessão.
- Persistência cifrada: Keystore (AES-GCM) no Android, com `allowBackup="false"`; Keychain no iOS, com acesso `ThisDeviceOnly`. Nos dois casos o token não vai para outro aparelho por backup.
- Fora de logs: nenhum `println`/`Log`/`NSLog`, nenhum plugin `Logging` do Ktor, token fora de mensagens de exceção. Na tela, só mascarado (`Ot_ab…wxyz`).
- Só builds de debug embutem o token de desenvolvimento; o release sai com `GdiBuildConfig.TOKEN = ""` sem parâmetro. Em produção o token ficaria num backend, e o app receberia só URLs de vídeo de curta duração.

### Contrato real × Swagger

Medido na conta de teste entre 02 e 05/10/2026; onde diverge do Swagger, vale o medido:

- Toda rota é `POST`, e **toda resposta é JSON com `content-type: text/plain`**. Um HTTP 200 pode trazer `"status":"erro"`; um corpo de erro pode ser uma string JSON (401 `"Não autorizado"`).
- Token inválido → 401; expirado → 403 com mensagem própria; renovar token já expirado → 400. A renovação invalida o token antigo.
- Listagem: `pagina`, `tamanhoPagina` e `origem` são obrigatórios; `pagina` começa em 1; `origem` no pedido é plural (`todos`/`vinculados`/`compartilhados`) e no item é singular. Não há total: fim da lista = página com menos itens que o tamanho pedido.
- A API não informa a categoria do dispositivo: é inferida pelo prefixo de modelo/nome.
- Fechadura e bateria exigem `ns` composto `NsDispositivo_NsHub_IdProdutoHub`, com o `idProduto` da própria fechadura no corpo.
- Vídeo: o Swagger promete MP4 por HTTPS, `session_id`, `monitor_url` e cota; a resposta real é **só uma URL RTSP** que expira sozinha. `canalVideo` 0 e 1 devolvem o mesmo RTSP; `streamId 0` → 500.
- Volume da fechadura → 500 na fechadura de teste, mesmo com o pedido que a API reconhece (um par `ns`/`idProduto` errado dá 404, o certo dá 500); estado, bateria e online da mesma fechadura respondem normalmente. Rotas de cota e sessões de streaming → 403 sem plano.
- **Online de subdispositivo** (medido em 05/10/2026 com este app): `/produtos/online/v1` com o `ns` puro da fechadura Zigbee responde `online:false` mesmo com a listagem dizendo `"status":"online"`; com o `ns` composto responde `true`. Câmera e hub respondem certo com o `ns` puro. O app consulta com o `ns` composto só nos subdispositivos.
- `criar-fluxo-video` pode voltar HTTP 500 transitório (1.ª chamada a frio em 05/10/2026); uma nova chamada funciona.

### Métricas medidas

| Métrica | Valor |
|---|---|
| `criar-fluxo-video` (pedido da sessão) | ~2,4 s |
| 1.º quadro a quente, Android | ~3 s |
| 1.º quadro a frio, Android | ~21 s (proxy da nuvem; igual em qualquer cliente) |
| 1.º quadro a frio, iOS | 37,8 s (uma única medição, VLCKit por software) |
| Com este app no Moto G9 Play (05/10/2026) | 22,6 s a frio; 3,6 s a quente; 2,3 s ao trocar de lente |
| Com este app no iPhone 13 Pro (06/10/2026) | 39,2 s a frio (VLCKit por software) |
| Com este app no simulador iOS (05/10/2026) | 2,4 s com o proxy já aquecido |
| APK debug com 3 ABIs | ~165 MB (libVLC) |

### Próximos passos

- iOS: parar o player em background observando `LocalLifecycleOwner`, como no Android, e testar o buffer de 800 ms sem `clock-jitter`.
- Distribuição Android: AAB com ABI splits e sem `x86_64` no release.
- Tela de licenças (LGPL 2.1+ do libVLC/VLCKit) e revisão jurídica antes de publicar, sobretudo no iOS.
- Media3 para o caso HTTPS/MP4, se um dia a API devolver o que o Swagger promete.
- Backend próprio: guardar a credencial, entregar só URLs de vídeo curtas e receber webhooks (eventos em tempo real nas abas de mensagens).
- Divisão em módulos (`:core:domain`, `:data:gdi`, `:feature:home`, `:feature:camera`, `:feature:lock`) quando entrar um segundo parceiro ou uma nova feature.
- `detekt` e `ktlint` no CI.

---

## Uso de IA

O app foi construído com um agente de IA de código (Claude Code), em **desenvolvimento orientado por especificação**. Antes do código foi escrita uma especificação com:

- marcos M1–M12 (do esqueleto do projeto à verificação em aparelho), cada um com objetivo, arquivos e um critério de pronto verificável por comando;
- o contrato da API GDI **medido** na conta de teste, com as divergências do Swagger e as armadilhas de plataforma já encontradas;
- a lista dos testes esperados e as capturas de referência do app Mibo Smart para cada tela.

A implementação foi dividida entre agentes por área (domínio e dados, telas, vídeo, testes, documentação), presos a um contrato de nomes e assinaturas, com um agente de build compilando e corrigindo no fim. A verificação não depende da palavra do agente: testes unitários em `commonTest` (JVM e simulador iOS), builds Android e iOS, os `grep` da regra de dependência, o CI no GitHub Actions (a cada push), o roteiro em aparelho real com a API real (no Android físico e parcialmente no iOS) e a comparação lado a lado com as capturas de referência. Comandos físicos na fechadura não são disparados pelo agente: exigem autorização explícita do dono da conta.

A especificação usada está versionada em [`docs/sdd/PROMPT-CASE-KMP.md`](docs/sdd/PROMPT-CASE-KMP.md) (só o ID do certificado Apple foi ocultado; as capturas de referência, de terceiros, não estão no repositório). O registro de como ela foi executada e verificada, etapa por etapa, com o que só o aparelho real revelou, está em [`docs/sdd/LOG.md`](docs/sdd/LOG.md).
