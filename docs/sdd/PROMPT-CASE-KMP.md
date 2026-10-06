> Cópia versionada da especificação usada no desenvolvimento orientado por especificação (SDD) deste app. Mudanças em relação ao original: o ID do certificado Apple foi ocultado (`XXXXXXXXXX`), e o exemplo `gdi.token = …` e o comando de higiene (com `gdi\.token=`) foram escritos para que esta cópia não conte na checagem `git log -p | grep -c` do próprio token. As imagens citadas (`design/*.jpg`, `design/app-*.png`) não estão no repositório: as `*.jpg` são capturas do app oficial Mibo Smart (terceiros) e os prints mostram dados da conta de teste. O registro de como a especificação foi executada e verificada está em [`LOG.md`](LOG.md).

# PROMPT — Casa Inteligente (case Intelbras) em Kotlin Multiplatform + Compose Multiplatform (Jetpack Compose), MVVM e Clean Code, do zero

> Arquivo local em `design/` (pasta fora do Git). Cole este documento inteiro num agente de IA de código aberto na pasta do projeto **`MiboSmart Case`** (projeto KMP já gerado pelo assistente oficial; seção 2.3), ou siga-o à mão. Ele é autocontido: tudo o que é preciso saber sobre a API, as versões, as armadilhas e a aparência está aqui.
>
> Na mesma pasta `design/` estão **seis capturas de tela do app oficial Mibo Smart** (`home.jpg`, `camera-carregando.jpg`, `camera-ao-vivo.jpg`, `camera-ptz.jpg`, `fechadura-mfr2030.jpg`, `hub-mca1002.jpg`) e os **prints do resultado esperado** das telas que o Mibo não mostra (`app-*.png`). Elas são a referência visual obrigatória das telas (regra 0.2.7 e seção 6).

---

## 0. Seu papel e as regras do jogo

Você é um engenheiro sênior de Android/KMP. Sua tarefa é implementar, do zero, **apenas** o app do case "Casa Inteligente" da Intelbras em **Kotlin Multiplatform com Compose Multiplatform**: um único código de UI em Compose rodando no **Android e no iOS**, com dados, domínio e UI em `commonMain` e só o que é realmente nativo em `androidMain`/`iosMain`/Swift.

**Stack e arquitetura (obrigatórias):** Kotlin Multiplatform; UI 100 % em **Compose Multiplatform** (a API do Jetpack Compose, a mesma no Android e no iOS; nada de XML/Views no Android nem SwiftUI de tela no iOS); arquitetura **MVVM + Clean Architecture** (seção 4.1); código seguindo **Clean Code** (seção 4.1, "Clean Code").

### 0.1 Fora de escopo (não implemente, nem "de bônus")

- Gravações em nuvem, webhooks, backend, publicação em loja.
- Qualquer funcionalidade que não esteja nos RF01–RF09 (seção 1.2), inclusive dados simulados ou telas extras.

O case pede, junto com o link do repositório, um documento de produto/arquitetura em PDF: o conteúdo dele é a seção "Produto e arquitetura" do `README.md` (M10), que o usuário exporta para PDF. O `README.md` é o único documento que você escreve.

Se aparecer vontade de adicionar algo fora do case, **não adicione**: registre a ideia em "Próximos passos" no `README.md`.

### 0.2 Regras invioláveis

1. **Passos verificáveis.** Trabalhe marco a marco (seção 5). Ao fim de cada marco: o projeto compila para Android **e** iOS, todos os testes estão verdes e o critério de pronto do marco foi conferido com o comando indicado. Não comece o marco seguinte com teste vermelho.
2. **Token nunca em arquivo versionado, nunca em log.** O token GDI só pode estar em `local.properties` (ignorado pelo Git) e serve só para **pré-preencher** a tela de token em desenvolvimento, nunca para login automático. Nada de `println`, `Log.d`, plugin `Logging` do Ktor com headers, nem token em mensagens de exceção. Na tela, só mascarado (`Ot_ab…wxyz`). Antes de cada commit rode `git grep -n "Ot_"` e confira que só aparecem tokens **falsos** de teste (`Ot_abc`, `Ot_old…`, `Ot_session_000001`…).
3. **Comentários de porquê.** Comentários curtos em português, explicando **por que** a linha é assim (fato da API, armadilha de plataforma, decisão de arquitetura). Nada de comentário que repete o código. Toda a UI em português do Brasil.
4. **Teste unitário para toda regra de negócio** (validação do token, mapeamento de erro, parser, filtro, paginação, concorrência, volume, histórico, formatação). Meta final: **≥ 70 testes** em `commonTest`, todos verdes em `./gradlew :shared:testAndroidHostTest` e `./gradlew :shared:iosSimulatorArm64Test`.
5. **Ação física na fechadura = perguntar antes.** Destrancar, trancar e mudar volume agem num equipamento real de terceiros. Você **nunca** dispara esses comandos contra a API real por conta própria (nem em script, nem em teste, nem pelo app). Na UI, todo comando passa por um diálogo de confirmação. Se precisar validar o comando no aparelho, **pare e peça autorização explícita ao usuário**, dizendo exatamente o que vai acontecer.
6. **Aparelho real no fim de cada marco de UI** (M6, M7, M8, M9, M10): instale no Android físico (`./gradlew :androidApp:installDebug`) e no iPhone/simulador, e confira a tela contra a captura de referência e a especificação visual (regra 0.2.7, seção 6). Emulador não substitui aparelho real para vídeo (decodificação H.265 por software é o gargalo).
7. **Fidelidade visual.** Cada tela reproduz a sua captura de referência em `design/` (mapa na seção 6.0). **Abra a imagem com a ferramenta de leitura de arquivos antes de escrever a tela** e compare lado a lado com uma captura do aparelho ao terminar. Diferenças só são aceitas em dois casos: (a) a API GDI não oferece o recurso — o controle aparece e mostra o Snackbar honesto da regra 0.2.8, ou é trocado por um recurso do case (ex.: Volume e Histórico na fechadura); (b) a captura mostra conteúdo de outra conta (nomes, bateria, imagem da câmera, datas). Toda diferença aceita está listada na seção 6; qualquer outra é defeito. A tela de token e as Definições não têm captura: seguem as cores da seção 6.1 e o layout das seções 6.3 e 6.2.
8. **Sem dados falsos na UI.** Nada de dispositivos de demonstração, números inventados ou botões mudos. Todo controle visível faz algo real **ou** mostra um Snackbar dizendo que a API GDI não oferece aquilo ("regra de honestidade").
9. **Não confie no Swagger.** A seção 3 tem o contrato **medido** na API real. Onde o Swagger diverge, vale a seção 3.
10. Commits pequenos, um por marco no mínimo, mensagem em inglês no formato `feat: …`/`test: …`/`build: …`.
11. **MVVM.** Toda tela é um `@Composable` que só desenha estado imutável coletado de `StateFlow` do ViewModel com `collectAsStateWithLifecycle` e envia eventos chamando funções do ViewModel; nenhuma tela chama repositório, `GdiApi` ou mapeador de erro diretamente. O ViewModel não guarda `Context`, `View` nem Composable, roda trabalho em `viewModelScope` e navega só por callbacks recebidos pela tela.
12. **Clean Architecture.** Dependências só apontam para dentro: `ui` → `domain` ← `data`. `domain` é Kotlin puro (sem Ktor, Koin, Compose, `androidx`); `ui` não importa nada de `data`; o ViewModel recebe só interfaces do domínio e casos de uso, injetados pelo Koin. Conferido pelos `grep` do M2 e da seção 9 (têm que vir vazios).
13. **Clean Code.** Nomes que revelam intenção, funções pequenas com uma responsabilidade, constantes nomeadas no lugar de números/strings mágicos, imutabilidade por padrão, erros explícitos (`AppError` selado, nenhuma exceção engolida em silêncio), sem código morto ou comentado, testes legíveis (Arrange/Act/Assert, um comportamento por teste). Regras completas na seção 4.1.

---

## 1. O case, nas minhas palavras

### 1.1 Objetivo

App mobile para a plataforma **Intelbras Casa Inteligente (API GDI)**. O usuário gera um **token temporário** no portal do desenvolvedor da Intelbras (`open-casainteligente.intelbras.com.br` → menu **Contas → Token Temporário**; o token vale ~2 h), cola o token no app e passa a ver os dispositivos da conta (câmeras, fechadura, hub). Das câmeras, assiste ao **vídeo ao vivo**; da fechadura, vê o estado, comanda abrir/fechar, ajusta o volume e lê o histórico de aberturas. A stack deve ser predominantemente Kotlin/KMP; testes e regras de negócio contam pontos; o repositório no GitHub conta pontos. Não pode haver credencial no repositório.

### 1.2 Requisitos funcionais e critério de aceite objetivo

| RF | Requisito | Critério de aceite (verificável) |
|---|---|---|
| **RF01** | Tela inicial com campo para o token | Sem token salvo, o app abre em "Conectar à conta Intelbras". Campo mascarado com botão mostrar/ocultar; "Entrar" só habilita com token plausível (≥ 8 caracteres após `trim`, sem espaço interno). O token só é gravado (Keystore/Keychain) **depois** que a 1.ª página da listagem responde com sucesso. Reabrir o app com token válido salvo vai direto para a Home. |
| **RF02** | Após enviar, listar os dispositivos | Depois de "Entrar", a Home mostra os dispositivos da conta na ordem da API, cada um com nome, tipo inferido (câmera/fechadura/hub/outro), estado online e rótulos "via hub"/"compartilhado". |
| **RF03** | Acessar câmeras e ver vídeo ao vivo | Tocar numa câmera abre a tela de vídeo; o app pede a sessão à GDI, mostra progresso em % e etapa, e exibe vídeo ao vivo com o chip "AO VIVO" e o texto "1º quadro em X s". Sair da tela libera player e sessão. Câmera "Dual" oferece "Lente móvel"/"Lente fixa". Funciona no Android e no iOS. |
| **RF04** | Erros amigáveis | Mensagens próprias e diferentes para: token ausente, token **inválido** (401), token **expirado** (403), sem rede, timeout, erro do servidor (5xx ou envelope de erro), lista vazia (com texto conforme o filtro). Nenhuma tela trava, nenhum stack trace aparece; detalhe técnico só em build de debug. |
| **RF05** | Fechadura: abrir, fechar, status | Tela da fechadura mostra "Porta fechada"/"Porta aberta"/"Estado indisponível", bateria, online, se a abertura remota está habilitada. "Pressione e segure" (600 ms) no círculo abre confirmação; só após confirmar o comando é enviado; depois o estado é relido. |
| **RF06** | Volume da fechadura: ver e mudar | Cartão Volume com Mudo/Baixo/Médio/Alto. Mostra o volume atual ou "Volume indisponível (a API respondeu HTTP 500)" + "Tentar de novo"; a escrita continua habilitada mesmo se a leitura falhar; após gravar, relê e informa "Volume alterado para …". |
| **RF07** | Filtrar por origem | Chips "Todos · Vinculados · Compartilhados" enviam `origem` = `todos`/`vinculados`/`compartilhados`; a lista recomeça na página 1; vazio por filtro tem texto próprio; resposta de filtro antigo nunca aparece depois de trocar o filtro. |
| **RF08** | Paginação com `pagina` e `tamanhoPagina` | "Itens por página" 2·5·10·50 (Definições); rodapé com "Carregar mais" quando a página veio cheia e "Sem mais informações" no fim; contagem "N página(s) · M dispositivo(s) · K por página"; erro ao carregar mais não apaga a lista. |
| **RF09** | Histórico de abertura | Cartão de histórico com 10 eventos (tipo traduzido + data/hora "dd/MM/aaaa HH:mm:ss"); "Ver mais" pede 30; histórico **vazio** e **erro de leitura** têm textos diferentes. |

RF01–RF04 são mínimos; RF05–RF09 são desejáveis. Entregue todos.

### 1.3 Critérios de avaliação e como o código deve evidenciá-los

| Critério (peso) | O que o avaliador procura | Onde isso tem que estar visível no código |
|---|---|---|
| Qualidade de código (25 %) | código bem organizado e fácil de ler; falhas tratadas | **Clean Code** (regras da seção 4.1: nomes de intenção, funções pequenas, constantes nomeadas, sem código morto, testes Arrange/Act/Assert); pacotes `domain`/`data`/`ui`; `sealed` para estados e erros; `when` exaustivo; nenhum `!!` sem justificativa; `CancellationException` sempre relançada; erros convertidos num só lugar (`GdiErrorMapper`) |
| Conhecimento técnico (25 %) | Android/Kotlin/KMP, **ciclo de vida, concorrência, consumo de API, persistência local, segurança, interoperabilidade com Java** | ciclo de vida: `ViewModel` + `viewModelScope`, `collectAsStateWithLifecycle`, `DisposableEffect` liberando player/sessão, stop em background; concorrência: `Mutex`, contador de geração, cancelamento de `Job`, `async` paralelo, renovação de token serializada, `CancellationException` relançada (`resultOf`); API: Ktor com parse manual de `text/plain`; persistência: Keystore AES-GCM / Keychain; segurança: token mascarado, fora de log e de backup, `-PgdiDevToken=false`; **interop com Java** (ver nota abaixo); interop nativa: `expect`/`actual`, Kotlin/Native ↔ Swift (interface Kotlin implementada em Swift), cinterop com `platform.Security` |
| Arquitetura (20 %) | separação UI/domínio/dados, **modularização**, baixo acoplamento, **múltiplos parceiros**, escalabilidade | **MVVM + Clean Architecture** (seção 4.1): telas Compose → `HomeViewModel` (`StateFlow`) → interfaces `TokenRepository`/`DeviceRepository` e casos de uso no `domain` ← implementações `TokenRepositoryImpl`/`DeviceRepositoryImpl` em `data`; `domain` sem Ktor/Koin/Compose/`androidx` e `ui` sem import de `data` (conferido por `grep` no M2 e na seção 9); interface `DeviceProvider` (parceiro) + `LockController`; repositório recebe **lista** de providers via Koin `getAll()`; cada `Device` tem `providerId`; modularização: um módulo Gradle `shared` com fronteiras por pacote (`ui` → `domain` ← `data`) e o caminho de divisão documentado no README (ver nota abaixo); escalabilidade: paginação incremental, filtro no servidor, parceiro novo sem tocar em UI/repositório |
| Usabilidade (15 %) | telas claras, navegação simples, retorno ao usuário em cada estado (carregando, conteúdo, vazio, erro) | aparência do app oficial Mibo Smart reproduzida a partir das seis capturas de referência (seção 6); `DevicesState` com `Loading/Content/Empty/Error/NoToken`; progresso do vídeo em %; confirmação antes de ação física; Snackbars honestos |
| Comunicação (15 %) | explicar com clareza, documentar, justificar cada decisão | `README.md` (como rodar, decisões, limitações, seção "Produto e arquitetura" que vira o PDF do case), seção "Uso de IA" do README (especificação que guiou o desenvolvimento e como foi verificado, que o case valoriza), comentários de porquê, mensagens de commit claras |

**Nota sobre interoperabilidade com Java (o case pontua "uso de Java").** `commonMain` não compila Java, e por isso as regras de negócio são Kotlin (ex.: `TokenFormat` é `object` Kotlin, não classe Java). A interop com Java existe e fica no `androidMain`, onde o Kotlin consome APIs Java diretamente: `java.security.KeyStore` e `javax.crypto.*` (Keystore), `java.time` (faixa da semana), `java.lang.Thread` (liberar o libVLC fora da main), a API Java do libVLC (`org.videolan.libvlc`, que por baixo é JNI) e `android.util.Base64`; no `commonMain`, `kotlinx.io.IOException` é `typealias` de `java.io.IOException` na JVM (por isso a ordem dos ramos no `GdiErrorMapper`). Não crie arquivos `.java` (o plugin `com.android.kotlin.multiplatform.library` é orientado a Kotlin e a regra teria de ser duplicada); registre essa decisão no README: num app Android puro um utilitário Java legado seria mantido e chamado do Kotlin; no KMP a regra precisa estar em `commonMain`, então a interop com Java fica na borda Android.

**Nota sobre modularização.** Um único módulo `shared` mantém o build simples para o tamanho do case; as fronteiras são de pacote e a regra de dependência é verificada. O README descreve a divisão quando o app crescer: `:core:domain` (Kotlin puro), `:data:gdi` (um módulo por parceiro, cada um registrando seu `DeviceProvider` no Koin), `:feature:home`, `:feature:camera`, `:feature:lock`, com `androidApp`/`iosApp` dependendo só das features. Não crie esses módulos agora.

### 1.4 Rastreabilidade RF → marco → testes → conferência no aparelho

| RF | Marco que implementa | Testes (números da seção 8) | Roteiro M12 (linhas) |
|---|---|---|---|
| RF01 | M4 (armazenamento), M5 (`SubmitTokenUseCase`), M6 (tela, ViewModel, rotas) | 1–3, 8–14, 64–71, 75–78 | 1, 4, 5, 18 |
| RF02 | M3 (cliente/parser), M5 (repositório), M7 (grade/lista) | 15, 29, 33–39, 47–48 | 4 |
| RF03 | M3 (`criar-fluxo-video`), M8 (player e tela) | 20, 27–28, 63, 79, 80, 85 | 9–12, 19 |
| RF04 | M2 (`AppError`), M3 (`GdiErrorMapper`), M7 (cartões de erro/vazio) | 19, 24, 30–32, 40–44, 50–51, 65, 66 | 2, 3, 6, 8 (+ M10 item 8: timeout) |
| RF05 | M3 (`GdiLockController`), M9 (`LockScreen`) | 21, 24 | 13, 14 |
| RF06 | M3, M5 (`ChangeLockVolumeUseCase`: grava e relê), M9 (cartão Volume) | 7, 22, 23, 26, 62, 72–74 | 15 |
| RF07 | M2 (`OriginFilter`), M5 (filtro + fallback + geração), M7 (chips) | 4, 5, 16, 52, 53, 56–58 | 6 |
| RF08 | M2 (`DevicePage.hasMore`), M5 (`loadMore`), M7 (rodapé, Definições) | 6, 48–50, 54–55, 57–60 | 7, 8 |
| RF09 | M3 (`lockHistory`), M9 (`HistoryCard`) | 25, 26, 45, 46, 62 | 16 |

---

## 2. Stack, versões e criação do projeto

### 2.1 Ambiente e versões exatas (combinação validada em out/2026)

| Item | Versão |
|---|---|
| Mac | Apple Silicon (não há target `x86_64`) |
| Android Studio | 2026.2 + plugin **Kotlin Multiplatform** (cria a configuração de execução `iosApp`) |
| Xcode | 27.0 |
| Gradle (wrapper) | **9.5.0** |
| JDK do daemon Gradle | **25** (fixado em `gradle/gradle-daemon-jvm.properties`) |
| Kotlin | **2.4.10** |
| AGP | **9.1.1** (plugin `com.android.kotlin.multiplatform.library` no `shared`) |
| compileSdk / targetSdk / minSdk | **37 / 37 / 26** |
| Compose Multiplatform | **1.11.1** |
| compose-material3 | **1.9.0** (versão própria) |
| compose-material-icons (core + **extended**) | **1.7.3** (versão própria, congelada) |
| androidx-activity (activity-compose) | 1.13.0 |
| lifecycle JetBrains (`org.jetbrains.androidx.lifecycle`) | 2.11.0 |
| navigation-compose JetBrains | 2.9.2 |
| Koin | 4.2.2 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization | 1.9.0 |
| Ktor | 3.5.1 (OkHttp no Android, Darwin no iOS, Mock nos testes) |
| libVLC Android (`org.videolan.android:libvlc-all`) | 3.7.7 |
| VLCKit iOS (SPM `tylerjonesio/vlckit-spm`, produto `VLCKitSPM`) | 3.6.0 (up to next major) |
| iOS deployment target | 16.0 |

**JDK:** o Mac de desenvolvimento **não tem JDK no sistema** (`/usr/libexec/java_home` falha). No terminal, antes de `./gradlew`, exporte:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

O mesmo vale para builds iniciados pela interface do **Xcode** (a fase "Compile Kotlin Framework" chama `./gradlew`). Rodando o iOS pelo **Android Studio** não precisa: a IDE compila o framework e define `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`, e o script do Xcode pula o Gradle.

### 2.2 Identidade do projeto (use exatamente estes nomes)

| Item | Valor |
|---|---|
| Pasta | `~/Documents/GitHub/MiboSmart Case/` (já existe, gerada pelo assistente KMP; repositório Git próprio). Atenção ao espaço no nome: use aspas nos comandos de terminal. |
| `rootProject.name` | `MobiSmartCase` (como o assistente gerou) |
| Pacote Kotlin | `br.com.pompeo.casa` |
| Namespace da lib `shared` | `br.com.pompeo.casa.shared` |
| `applicationId` Android | `br.com.pompeo.casa` |
| Bundle id iOS | `br.com.pompeo.casa.ios` |
| Nome exibido | `Casa Inteligente` |
| Framework iOS | `Shared` (`import Shared` no Swift) |

### 2.3 Ponto de partida: o projeto que já existe

O projeto **já foi gerado** pelo assistente oficial (Android Studio → New Project → Kotlin Multiplatform, Android + iOS, UI compartilhada) em `~/Documents/GitHub/MiboSmart Case/`. Estado encontrado:

| Item | Como o assistente gerou | O que fazer |
|---|---|---|
| `rootProject.name` | `MobiSmartCase` | manter |
| Módulos | `shared`, `androidApp`, `iosApp` (com `shared/src/androidHostTest` e `iosTest`) | manter |
| Pacote / namespace | `org.example.project` / `org.example.project.shared` | **renomear** para `br.com.pompeo.casa` / `br.com.pompeo.casa.shared` (pastas, `package`, imports) |
| `applicationId` Android | `org.example.project` | **renomear** para `br.com.pompeo.casa` |
| Bundle id iOS | definido em `iosApp/Configuration/Config.xcconfig` | **trocar** para `br.com.pompeo.casa.ios` |
| `DEVELOPMENT_TEAM` | já `AH3MDC3666` numa configuração; `${TEAM_ID}` na outra (vem do `Config.xcconfig`) | conferir que `TEAM_ID=AH3MDC3666` no `Config.xcconfig` |
| Kotlin / Compose Multiplatform / AGP | **2.4.20 / 1.12.1 / 9.1.1** (mais novos que a combinação validada 2.4.10 / 1.11.1 da seção 2.1) | ver passo 2 |
| `local.properties` | só `sdk.dir` | acrescentar `gdi.token` (seção 2.11) |
| `design/` | capturas de referência + este prompt | pôr no `.gitignore` |

Passos (este é o começo do M1):

1. Acrescente `design/` ao `.gitignore` e faça o commit inicial do esqueleto.
2. **Versões:** mantenha Kotlin, Compose Multiplatform e AGP que o assistente gerou e acrescente ao `gradle/libs.versions.toml` as bibliotecas da tabela 2.1 (Koin, Ktor, kotlinx, lifecycle e navigation JetBrains, libVLC, material3 e ícones). Se alguma dependência não resolver ou o build quebrar por incompatibilidade, volte Kotlin/Compose Multiplatform para a combinação validada da seção 2.6 (2.4.10 / 1.11.1) em vez de procurar outra combinação.
3. Renomeie o pacote e os identificadores da tabela acima (Android Studio: Refactor → Rename no pacote; depois confira `namespace`, `applicationId` e o `Config.xcconfig`).
4. Ajuste os arquivos Gradle ao que as seções 2.5–2.8 pedem (plugin `com.android.kotlin.multiplatform.library` com o bloco `android {}` **dentro** de `kotlin {}`, `withHostTest {}`, a task `generateGdiConfig`, dependências por source set). Mantenha o que o assistente já gerou certo.
5. Apague o código de exemplo (Greeting, Platform etc.) e escreva os arquivos mínimos do M1 (`App.kt`, `di/Koin.kt`, actuals do `platformModule`, `MainViewController.kt`, `CasaApplication`/`MainActivity`, Swift).
6. Rode `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64` — é o critério do M1 e tem que passar antes de qualquer feature.

### 2.4 Estrutura final de pastas esperada

```
MiboSmart Case/
├─ README.md                      como rodar, decisões, limitações, Produto e arquitetura, Uso de IA (sem token)
├─ .gitignore
├─ .github/workflows/ci.yml       jobs Android (Linux) e iOS (macOS)
├─ settings.gradle.kts · build.gradle.kts · gradle.properties · gradlew · gradlew.bat
├─ gradle/libs.versions.toml · gradle/gradle-daemon-jvm.properties · gradle/wrapper/*
├─ local.properties               FORA DO GIT: sdk.dir, gdi.token (opcional), gdi.baseUrl (opcional)
├─ design/               FORA DO GIT: este prompt, as 6 capturas do app Mibo e os prints app-*.png (seção 6)
├─ shared/
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ commonMain/kotlin/br/com/pompeo/casa/
│     │  ├─ App.kt                               tema + rotas tipadas + NavHost
│     │  ├─ di/Koin.kt                           appModule, expect platformModule, initKoin
│     │  ├─ domain/AppError.kt · DevicesState.kt · ErrorMapper.kt · TokenFormat.kt · TokenStorage.kt · ResultOf.kt
│     │  ├─ domain/model/Device.kt · DeviceQuery.kt
│     │  ├─ domain/provider/DeviceProvider.kt    DeviceProvider + LockController
│     │  ├─ domain/repository/TokenRepository.kt · DeviceRepository.kt   interfaces (o ViewModel só vê estas)
│     │  ├─ domain/usecase/SubmitTokenUseCase.kt · ChangeLockVolumeUseCase.kt
│     │  ├─ data/TokenRepositoryImpl.kt · DeviceRepositoryImpl.kt
│     │  ├─ data/gdi/GdiApi.kt · GdiDeviceParser.kt · GdiDeviceProvider.kt · GdiErrorMapper.kt · GdiFormat.kt
│     │  ├─ player/CameraPlayer.kt               expect + PlayerProgress + constante de buffer
│     │  ├─ platform/BuildInfo.kt · WeekStrip.kt expect + lógica pura
│     │  └─ ui/Theme.kt · MiboLogic.kt · Components.kt · HomeViewModel.kt · TokenScreen.kt   (apresentação)
│     │     · HomeShell.kt · CameraScreen.kt · DeviceScreen.kt
│     ├─ commonTest/kotlin/br/com/pompeo/casa/   TestSupport.kt + 14 classes de teste
│     ├─ androidMain/kotlin/br/com/pompeo/casa/  data/security/KeystoreTokenStorage.kt, di/PlatformModule.android.kt,
│     │                                          platform/{BuildInfo,WeekStrip}.android.kt, player/CameraPlayer.android.kt
│     └─ iosMain/kotlin/br/com/pompeo/casa/      MainViewController.kt, data/security/KeychainTokenStorage.kt,
│                                                di/PlatformModule.ios.kt, platform/{BuildInfo,WeekStrip}.ios.kt,
│                                                player/{CameraPlayer.ios.kt, NativeVideoPlayer.kt}
├─ androidApp/
│  ├─ build.gradle.kts
│  └─ src/main/{AndroidManifest.xml, res/values/strings.xml, kotlin/br/com/pompeo/casa/{CasaApplication,MainActivity}.kt}
└─ iosApp/
   ├─ Info.plist                                 FORA da pasta sincronizada iosApp/iosApp
   ├─ iosApp.xcodeproj/                          + xcshareddata/xcschemes/iosApp.xcscheme (esquema COMPARTILHADO)
   └─ iosApp/ iOSApp.swift · ContentView.swift · VLCVideoPlayer.swift · Assets.xcassets/
```

`.gitignore`:

```
*.iml
.gradle/
.idea/
local.properties
**/build/
.DS_Store
**/.kotlin/
xcuserdata/
*.xcuserstate
DerivedData/
*.local.md
design/
```

### 2.5 Gradle raiz

`settings.gradle.kts`:

```kotlin
rootProject.name = "MobiSmartCase"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS") // permite implementation(projects.shared)

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral() // org.jetbrains.* (Compose MP, lifecycle e navigation JetBrains) vêm daqui
    }
}

include(":shared")
include(":androidApp")
```

`build.gradle.kts` (raiz) — só declara os plugins, para todos os módulos usarem a mesma versão:

```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}
```

`gradle.properties`:

```properties
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx3072M

org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true

android.nonTransitiveRClass=true
android.useAndroidX=true
```

`gradle/wrapper/gradle-wrapper.properties`: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip` (demais linhas padrão).

`gradle/gradle-daemon-jvm.properties`: gere com `./gradlew updateDaemonJvm --jvm-version=25`. O resultado tem `toolchainVersion=25` e URLs do foojay por SO/arquitetura; o daemon baixa o JDK 25 se faltar (por isso o CI só precisa de um JDK qualquer para iniciar o wrapper).

### 2.6 `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.1.1"
android-compileSdk = "37"
android-minSdk = "26"
android-targetSdk = "37"
androidx-activity = "1.13.0"
androidx-lifecycle = "2.11.0"
androidx-navigation = "2.9.2"
compose-multiplatform = "1.11.1"
compose-material3 = "1.9.0"
compose-material-icons = "1.7.3"
koin = "4.2.2"
kotlin = "2.4.10"
kotlinx-coroutines = "1.11.0"
kotlinx-serialization = "1.9.0"
ktor = "3.5.1"
libvlc = "3.7.7"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-multiplatform" }
compose-material-icons-core = { module = "org.jetbrains.compose.material:material-icons-core", version.ref = "compose-material-icons" }
compose-material-icons-extended = { module = "org.jetbrains.compose.material:material-icons-extended", version.ref = "compose-material-icons" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose-material3" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose-multiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose-multiplatform" }
compose-uiTooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "compose-multiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "compose-multiplatform" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
libvlc = { module = "org.videolan.android:libvlc-all", version.ref = "libvlc" }
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidMultiplatformLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinxSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Não acrescente `media3` (`media3-exoplayer`, `media3-exoplayer-rtsp`, `media3-ui`) nem `compose-components-resources`: foram **omitidos de propósito** (não há Media3 nem `composeResources` neste app).

Por quê: lifecycle, ViewModel e navigation são os artefatos **JetBrains** (`org.jetbrains.androidx.*`), multiplataforma; os do Google (`androidx.*`) são só Android. Não há Media3: no Android o libVLC toca tanto RTSP quanto HTTP(S), e o Media3 RTSP **recusa** o SDP da nuvem Intelbras (seção 7). Se um dia a API devolver HTTPS/MP4 como o Swagger promete, adicionar Media3 para esse caso é uma melhoria opcional.

### 2.7 `shared/build.gradle.kts`

```kotlin
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization) // rotas tipadas da navegação são @Serializable
}

kotlin {
    // iosArm64 = iPhone físico; iosSimulatorArm64 = simulador no Mac Apple Silicon. Sem iosX64.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"  // nome do módulo Swift: import Shared
            isStatic = true      // linkado dentro do executável: não precisa embarcar framework dinâmico
        }
    }

    android {
        namespace = "br.com.pompeo.casa.shared" // diferente do namespace do app
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        // Roda commonTest na JVM do host, sem emulador: ./gradlew :shared:testAndroidHostTest
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            // Engine do Ktor escolhida pelo classpath: HttpClient {} sem engine explícita usa OkHttp aqui.
            implementation(libs.ktor.client.okhttp)
            // androidContext() para o KeystoreTokenStorage no platformModule.
            implementation(libs.koin.android)
            // RTSP da nuvem Intelbras: H.265 sem "fmtp" no SDP, que o Media3 não aceita; o libVLC aceita.
            implementation(libs.libvlc)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin) // ... e Darwin (NSURLSession) aqui
        }
    }
}

// Lê o token GDI de local.properties (ignorado pelo Git) e gera GdiBuildConfig em commonMain.
// O valor só pré-preenche a tela de token (desenvolvimento), nunca é login automático; para gerar
// um binário sem ele: ./gradlew ... -PgdiDevToken=false. Em produção o token ficaria num backend.
val gdiConfig = tasks.register("generateGdiConfig") {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    val devToken = (project.findProperty("gdiDevToken")?.toString() ?: "true") != "false"
    val token = if (devToken) props.getProperty("gdi.token", "") else ""
    val baseUrl = props.getProperty("gdi.baseUrl", "https://api-casainteligente.intelbras.com.br")
    val outDir = layout.buildDirectory.dir("generated/gdi/commonMain/kotlin")
    inputs.property("token", token)     // muda o valor => a task roda de novo
    inputs.property("baseUrl", baseUrl)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("br/com/pompeo/casa/GdiBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package br.com.pompeo.casa

            /** Gerado pelo Gradle a partir de local.properties. Não editar. */
            object GdiBuildConfig {
                const val TOKEN: String = "${token.replace("\\", "\\\\").replace("\"", "\\\"")}"
                const val BASE_URL: String = "$baseUrl"
            }
            """.trimIndent()
        )
    }
}
// Passar o TaskProvider (e não um caminho) cria a dependência de build sozinho, para Android e iOS.
kotlin.sourceSets.commonMain { kotlin.srcDir(gdiConfig) }

dependencies {
    // Nome da configuração no plugin KMP-library que põe o ui-tooling no runtime Android (Previews na IDE).
    androidRuntimeClasspath(libs.compose.uiTooling)
}
```

Até o primeiro build a IDE pode marcar `GdiBuildConfig` em vermelho: `./gradlew :shared:generateGdiConfig` resolve. Uma biblioteca KMP **não tem `BuildConfig`**; por isso a task gera o objeto e `isDebugBuild` vem de `expect`/`actual` (M6).

### 2.8 `androidApp`

`androidApp/build.gradle.kts`:

```kotlin
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
```

`androidApp/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <!-- allowBackup=false: o token cifrado não vai para backup em nuvem nem para outro aparelho
         (a chave do Keystore não migra; o dado restaurado seria lixo). -->
    <application
        android:name=".CasaApplication"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <!-- adjustResize: o teclado redimensiona a tela, importante no campo de token. -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`res/values/strings.xml`: `<string name="app_name">Casa Inteligente</string>`.

`CasaApplication.kt` e `MainActivity.kt` (pacote `br.com.pompeo.casa`, o mesmo de `App()`):

```kotlin
// CasaApplication.kt
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

// MainActivity.kt
package br.com.pompeo.casa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema claro (Mibo): barras transparentes com ícones escuros, independentemente do modo do sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { App() }
    }
}
```

### 2.9 `iosApp` (projeto Xcode)

Crie no Xcode um app iOS **SwiftUI** chamado `iosApp` dentro de `iosApp/` (ou use o gerado pelo assistente KMP) e aplique:

1. **Fase "Compile Kotlin Framework"** (Run Script, `/bin/sh`, marque "Based on dependency analysis" **desligado** = `alwaysOutOfDate = 1`), arrastada para ser a **primeira** fase, antes de `Sources` (o Swift faz `import Shared`):

   ```sh
   if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
     echo "Skipping Gradle build: OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES"
     exit 0
   fi
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

   `embedAndSignAppleFrameworkForXcode` lê `CONFIGURATION`, `SDK_NAME` e `ARCHS` do Xcode, compila o target certo e põe o framework em `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.

2. **Build settings do projeto:** `ENABLE_USER_SCRIPT_SANDBOXING = NO` (o sandbox de scripts impediria o Gradle de escrever em `shared/build` e `~/.gradle`), `IPHONEOS_DEPLOYMENT_TARGET = 16.0`.

3. **Build settings do target** (Debug e Release):

   ```
   FRAMEWORK_SEARCH_PATHS = "$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"
   OTHER_LDFLAGS = ("$(inherited)", "-framework", Shared)     // framework estático: não entra na fase Frameworks
   "EXCLUDED_ARCHS[sdk=iphonesimulator*]" = x86_64            // só existe iosSimulatorArm64
   GENERATE_INFOPLIST_FILE = YES
   INFOPLIST_FILE = Info.plist                                // mesclado com o gerado
   INFOPLIST_KEY_CFBundleDisplayName = "Casa Inteligente"
   INFOPLIST_KEY_UILaunchScreen_Generation = YES              // sem launch screen o iOS roda em modo compatibilidade
   INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES
   INFOPLIST_KEY_UISupportedInterfaceOrientations = UIInterfaceOrientationPortrait
   PRODUCT_BUNDLE_IDENTIFIER = br.com.pompeo.casa.ios
   PRODUCT_NAME = CasaInteligente
   TARGETED_DEVICE_FAMILY = 1                                 // só iPhone
   CODE_SIGN_STYLE = Automatic
   DEVELOPMENT_TEAM = <seu Team ID>                           // ver 2.10
   SWIFT_VERSION = 5.0
   ```

4. **`iosApp/Info.plist`** — fora da pasta sincronizada `iosApp/iosApp` (dentro dela o Xcode 16+ trata o arquivo como recurso e o build quebra com "Multiple commands produce …/Info.plist"):

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
   	<!-- Exigido pelo Compose Multiplatform: sem esta chave a PlistSanityCheck aborta o app ao abrir.
   	     Também libera 120 Hz (ProMotion). -->
   	<key>CADisableMinimumFrameDurationOnPhone</key>
   	<true/>
   </dict>
   </plist>
   ```

5. **SPM:** File → Add Package Dependencies → `https://github.com/tylerjonesio/vlckit-spm`, regra **Up to Next Major** a partir de **3.6.0**, produto **VLCKitSPM** no target `iosApp`. Versione o `Package.resolved` (em `iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/`). É um empacotamento de terceiros do VLCKit; o primeiro build baixa um pacote grande.

6. **Esquema compartilhado:** Product → Scheme → Manage Schemes → marque **Shared** no `iosApp`. Ele vai para `xcshareddata/xcschemes/iosApp.xcscheme`; o CI (`xcodebuild -scheme iosApp`) e o Android Studio dependem dele (`xcuserdata/` é ignorado).

7. **Android Studio:** com o plugin Kotlin Multiplatform, a configuração **iosApp** (tipo "Xcode Application", esquema `iosApp`, Debug) aparece sozinha; se não aparecer, crie em Run → Edit Configurations → + → iOS/Xcode Application.

### 2.10 Assinatura no iPhone físico

- No Xcode → target → Signing & Capabilities: escolha o **Team** (com Apple ID gratuito, "Personal Team"). Ative o **Modo Desenvolvedor** no iPhone.
- **Team ID:** use o Team ID da conta Apple (para esta conta, **AH3MDC3666**), nunca o ID do certificado. O código entre parênteses em "Apple Development: Nome (XXXXXXXXXX)" é o **ID do certificado**, não o Team — usá-lo como `DEVELOPMENT_TEAM` faz a assinatura falhar.
- A configuração do Android Studio passa `DEVELOPMENT_TEAM=""`, então o Team **precisa** estar no pbxproj.
- Terminal: `xcodebuild … -destination id=<UDID> -allowProvisioningUpdates`. Simulador e CI: `CODE_SIGNING_ALLOWED=NO`.

### 2.11 `local.properties` (nunca versionado)

```properties
sdk.dir=/Users/<voce>/Library/Android/sdk
# Opcional, só para pré-preencher a tela de token em desenvolvimento (vale ~2 h):
gdi.token = Ot_...
# Opcional; este já é o padrão:
# gdi.baseUrl=https://api-casainteligente.intelbras.com.br
```

O `xcodebuild` chama o Gradle na raiz, então o iOS usa o **mesmo** `local.properties`. No CI não existe `local.properties`: `TOKEN = ""` e os testes usam `MockEngine` — **o token GDI nunca é segredo do CI**. Para um binário de distribuição: `./gradlew :androidApp:assembleRelease -PgdiDevToken=false`. Diga no README, sem rodeios: um `const val` num APK/binário pode ser extraído; isso só é aceitável em demo; em produção o token fica num backend e o app recebe só URLs de vídeo de curta duração.

---

## 3. Contrato real da API GDI — **fatos medidos; não confie no Swagger**

Observado na conta de teste entre 02 e 05/10/2026 (câmera iM4 Dual, fechadura MFR 2030 ligada a um hub MCA 1002). Números de série nos exemplos são fictícios; a **forma** é a real.

### 3.1 Transporte e envelope

1. Base `https://api-casainteligente.intelbras.com.br`. **Todas** as rotas são `POST`, `Content-Type: application/json`, `Authorization: Bearer Ot_…`.
2. **Toda resposta, sucesso ou erro, é JSON servido com `content-type: text/plain`.** O `ContentNegotiation` do Ktor não converte isso: leia `bodyAsText()` e faça `Json.parseToJsonElement` à mão.
3. Envelope: `{"status": "sucesso" | "erro", "msg": "…", "data": …}`. Gateway pode responder `{"message": "Forbidden"}` ou `{"message":"Internal Server Error"}`. Mensagem: procure `msg`, depois `message`, `erro`, e por fim o texto cru.
4. **Um HTTP 200 pode trazer `"status":"erro"`** → trate como erro (vira `AppError.Server`).
5. Comandos (`controle-fechadura`, `mudar-volume`) **nunca foram executados** no equipamento real; a resposta esperada (documentação/fixture) é `{"status":"sucesso"}` **sem `data`** → sucesso não pode exigir `data` (o teste usa essa fixture).
6. Corpo de erro pode ser **uma string JSON**, não objeto: `"Não autorizado"` (com aspas). O parser não pode assumir objeto.

### 3.2 Autenticação

| Situação | HTTP | Corpo real | Tradução no app |
|---|---|---|---|
| Token inexistente/inválido | **401** | `"Não autorizado"` (string JSON) | `AppError.TokenInvalid` (detalhe `HTTP 401: Não autorizado`) |
| Token **expirado** (~2 h) | **403** | `{"status":"erro","msg":"Token expirado, por favor gere um novo token"}` | `AppError.TokenExpired` |
| Sem header `Authorization` | 401 | `"Token não está presente na requisição"` | nunca acontece: o app falha antes com `GdiNoTokenException` |
| Renovar token já expirado | **400** | `{"status":"erro","msg":"Não foi possível renovar o token, por favor gere um novo"}` | `TokenInvalid` (pelo fragmento "renovar o token") |

Renovação: `POST /autenticacao/renovar-token/v1` com `Authorization: Bearer <atual>` **e** corpo `{"token": "<atual>"}` → `{"status":"sucesso","data":{"token":"Ot_…"}}`. **O token antigo deixa de valer**: persista o novo. O app renova **uma única vez** ao receber 401/403 e repete a chamada; se a renovação falhar, pede token novo ao usuário. Na prática um token expirado não renova, então o caminho real de 403 é "pedir token novo".

### 3.3 Listagem (RF02, RF07, RF08)

`POST /produtos/listar-dispositivos/v1` com `{"tamanhoPagina": 5, "pagina": 1, "origem": "todos"}`.

- Os três campos são **obrigatórios**: sem `origem` → **HTTP 500**.
- `origem` aceita **exatamente** `todos`, `vinculados`, `compartilhados` (plural). Qualquer outro valor → HTTP 400 `"Parâmetro inválido para 'origem'"`.
- `pagina` começa em **1**: `pagina = 0` → HTTP 400 `"Parâmetro inválido ou não declarado"`.
- `data` é uma **lista plana, sem total nem número de páginas**. Fim da lista = página com menos itens que `tamanhoPagina` (confirmado: 3 dispositivos, `tamanhoPagina = 2` → 2, depois 1, depois `[]`). Logo `hasMore = itens >= tamanhoPagina`.
- A conta de teste não tem compartilhados: `compartilhados` devolve `[]`.

Item real (anonimizado):

```json
{"atualizacaoDisponivel": false, "ns": "LOCK000000000002", "modelo": "MFR 2030", "nome": "MFR 2030-0002",
 "status": "online", "versao": "1.0.0", "subdispositivo": true, "idProduto": "PRODLCK1",
 "ultimaVezOnline": "20261002T192905Z", "origem": "vinculado",
 "dispositivoPai": "HUB0000000003", "idProdutoDispositivoPai": "PRODHUB1"}
```

- `status` = `"online"`/`"offline"` (string).
- `origem` no item vem no **singular** (`"vinculado"`/`"compartilhado"`); no pedido, plural.
- `ultimaVezOnline` cru: `AAAAMMDDTHHMMSS[Z]`.
- A API **não devolve categoria**: infira pelo prefixo de `modelo`/`nome`. **Medido** na conta de teste: `iM` → câmera; `MFR` → fechadura; `MCA`/`IOT-ZG` → hub (o hub MCA 1002 vem com **modelo `IOT-ZG2-IB`** e nome `MCA 1002-…`: consulte modelo **e** nome). **Hipótese** (linhas Mibo sem exemplar na conta): `MFV`/`MFD` → fechadura; `MSM`/`MSA`/`MTU` → sensor; `MLS`/`ELW` → lâmpada. Diga isso num comentário no parser.
- O Swagger não documenta este corpo: o parser deve tolerar envelopes e nomes de campo alternativos.

### 3.4 Subdispositivos (Zigbee)

Todas as rotas de **fechadura** e a de **bateria** exigem `ns` **composto**: `NsDispositivo_NsHub_IdProdutoHub`, ex. `LOCK000000000002_HUB0000000003_PRODHUB1`. Com o `ns` puro a API responde erro (volume: 404 `"Dispositivo não encontrado"`). O `idProduto` no **corpo** é o da **própria fechadura**; o do hub vai só dentro do `ns` composto. `online` e `criar-fluxo-video` usam o `ns` puro.

### 3.5 Produtos

| Rota | Corpo | Resposta real |
|---|---|---|
| `/produtos/online/v1` | `{"ns"}` | `{"data":{"online":true}}` |
| `/produtos/versao/v1` | `{"ns"}` (medido com o `ns` puro do hub; para subdispositivo o app manda o composto, por coerência com a bateria — não medido) | `{"data":{"versao":"2.4.628243","atualizacaoDisponivel":false}}` |
| `/produtos/bateria/v1` | `{"ns": composto, "idProduto"}` | `{"data":{"bateria":26}}` (número) |

### 3.6 Câmeras e streaming (RF03)

`POST /cameras/criar-fluxo-video/v1` com `{"ns": "<ns>", "stream_gb": 0.2, "canalVideo": 0, "streamId": 1}`.

- O **Swagger promete** `url` (MP4 fragmentado por HTTPS), `monitor_url`, `session_id`, `quota_gb`. A **resposta real** é **só** `{"data":{"url":"rtsp://liveopenrtspproxy…imoulife.com:8554/…?expire=…"}}` — RTSP de um proxy da nuvem Imou. Modele `sessionId`, `monitorUrl` e `quotaGb` como **anuláveis**.
- A URL **expira sozinha** (`expire`). Sem `session_id` não há o que encerrar: `/streaming/encerrar-sessao/v1` só é chamado se houver `session_id`.
- O SDP desse RTSP traz `a=rtpmap:<pt> H265/90000` e `a=rtpmap:<pt> MPEG4-GENERIC/16000` **sem nenhuma linha `a=fmtp`**. Os parâmetros do H.265 (VPS/SPS/PPS) vêm dentro do fluxo (RFC 7798 padrão); o AAC vem sem `config`. Consequências na seção 7.
- `canalVideo: 0` e `canalVideo: 1` na iM4 Dual devolvem **o mesmo caminho RTSP** (muda só o `eventLiveID`): a GDI não separa as lentes. O app envia o canal certo e avisa.
- `canalVideo: 0, streamId: 0` → **HTTP 500**. Use sempre `streamId = 1`.
- `stream_gb` limita a banda cobrada da cota de vídeo (segundo o Swagger; o app manda 0,2 como no exemplo, e a chamada funciona com esse valor).
- `/streaming/cota-disponivel/v1`, `minhas-sessoes`, `sessao-info` → **HTTP 403** na conta de teste (sem plano). Não use no fluxo.
- Tempos medidos: `criar-fluxo-video` ~2,4 s; 1.º quadro a quente ~3 s; **a frio ~21 s** (primeira abertura depois de minutos sem ninguém assistindo; igual em qualquer app — é o proxy); iOS a frio 37,8 s (uma única medição, relatada por quem estava com o iPhone; VLCKit por software; não investigado).

### 3.7 Fechaduras (RF05, RF06, RF09)

| Rota | Corpo | Resposta real |
|---|---|---|
| `/fechaduras/status-abertura/v1` | `{"ns": composto, "idProduto"}` | `{"data":{"aberto":false}}` |
| `/fechaduras/status-abrir-remoto/v1` | idem | `{"data":{"habilitado":true}}` |
| `/fechaduras/historico-abertura/v1` | `{"ns": composto, "quantidade": 10}` (**sem** `idProduto`) | `{"data":[{"tempoLocal":"20261002T162932","nome":"APP","tipo":"usuarioRemoto"}, …]}` |
| `/fechaduras/controle-fechadura/v1` | `{"ns": composto, "aberto": true|false, "idProduto"}` | esperado `{"status":"sucesso"}` sem `data` (fixture; **nunca executado no equipamento real**) |
| `/fechaduras/volume/v1` | `{"ns": composto, "idProduto"}` | **HTTP 500** `{"msg":"Erro desconhecido, por favor tente novamente mais tarde"}` na fechadura de teste (03 e 05/10/2026) |
| `/fechaduras/mudar-volume/v1` | `{"ns": composto, "idProduto", "volume": 0..3}` | não executado; escala documentada (não verificada no aparelho): 0 = Mudo, 1 = Baixo, 2 = Médio, 3 = Alto |

- Histórico pagina **só por quantidade**: o app pede 10 e, em "Ver mais", 30. Tipos vistos: `usuarioRemoto`, `interno`; `senha`, `digital`, `cartao`/`tag`, `chave` mapeados por hipótese.
- Como a leitura de volume dá 500, a UI mostra o erro e **mantém a escrita habilitada**; depois de gravar, relê; se a releitura falhar, assume o valor pedido (a escrita foi aceita).

### 3.8 Eventos

Movimento, campainha e aberturas em tempo real chegam **só por webhook** configurado no portal, o que exige backend próprio. O app **não** recebe eventos: a aba Mensagens e a aba Mensagens do hub dizem isso com honestidade.

---

## 4. Arquitetura alvo

### 4.1 MVVM + Clean Architecture

**Camadas e regra de dependência.** Três camadas em `commonMain`, separadas por pacote; as setas de dependência só apontam para o `domain`:

```
   apresentação (pacote ui/ + App.kt + player/)          dados (pacote data/)
   Composables ── eventos ──► HomeViewModel               TokenRepositoryImpl · DeviceRepositoryImpl
        ▲                        │  StateFlow<UiState>     GdiApi (Ktor) · GdiDeviceProvider · GdiErrorMapper
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

- **`domain`** — modelos, `DevicesState`, `AppError`, as interfaces de repositório (`domain/repository/TokenRepository`, `domain/repository/DeviceRepository`), de parceiro (`DeviceProvider`, `LockController`), de armazenamento (`TokenStorage`), de erro (`ErrorMapper`) e os casos de uso. Kotlin puro: **não importa Ktor, Koin, Compose, `androidx`, `kotlinx.serialization` nem `data`/`ui`**. A única biblioteca permitida é `kotlinx.coroutines` (os repositórios expõem `StateFlow`; os casos de uso são `suspend`).
- **`data`** — implementações: `TokenRepositoryImpl`, `DeviceRepositoryImpl` (paginação, filtro, geração, `Mutex`), `data/gdi/*` (Ktor, parser, `GdiErrorMapper : ErrorMapper`) e os `TokenStorage` nativos. Depende só de `domain`. **Só `data/gdi` conhece a GDI** (rotas, nomes de campos, códigos de erro, formato de data): o que sai de lá já é modelo do domínio, com textos prontos para exibir (ex.: `LockEvent.time`/`description`, `Device.lastOnline` formatados).
- **apresentação (`ui`, `App.kt`, `player/`)** — Composables e o `HomeViewModel`. Depende só de `domain`; **não importa nada de `data`**. O ViewModel recebe por injeção (Koin) apenas `DeviceRepository`, `TokenRepository`, `SubmitTokenUseCase`, `ChangeLockVolumeUseCase` e `ErrorMapper`, nunca uma classe `*Impl`, `GdiApi` ou `GdiErrorMapper`.
- O que é nativo entra por `expect`/`actual` **de funções e vals de topo** (não use `expect class`: dispensa flag de compilador) ou por interface Kotlin implementada em Swift (vídeo no iOS).

**Casos de uso: só onde há regra de negócio.** Há exatamente dois, em `domain/usecase/`, ambos `class` com `suspend operator fun invoke` (código no M5):

| Caso de uso | Regra que justifica existir |
|---|---|
| `SubmitTokenUseCase(tokens, devices)` | normalizar e validar o formato (`TokenFormat`) sem chamar a API; guardar o token só na sessão; validar carregando a 1.ª página; persistir **só em sucesso** (inclusive lista vazia) o valor atual (a GDI pode ter renovado); em erro ou cancelamento, `logout()` para não sobrar token não validado. Junta dois repositórios numa regra do RF01. |
| `ChangeLockVolumeUseCase(devices)` | gravar o volume, reler (a GDI não devolve o valor novo) e, se a releitura falhar (HTTP 500 real) ou vier vazia, assumir o volume pedido porque a escrita foi aceita (RF06). |

Todo o resto (listar, `refresh`, `loadMore`, `setOrigin`, `setPageSize`, `logout`, vídeo, estado/comando da fechadura, histórico, firmware, online) o ViewModel chama direto na interface `DeviceRepository`/`TokenRepository`. Decisão consciente: caso de uso que só repassa uma chamada (`GetDevicesUseCase` que devolve `repository.state`) é código sem regra, dobra os arquivos e os testes e não protege nada; a fronteira que importa (ViewModel enxerga só interfaces do domínio) já existe sem ele. Se uma operação ganhar regra, ela vira caso de uso nesse momento.

**MVVM (fluxo unidirecional).**

- A tela (`@Composable`) só desenha estado **imutável** e envia eventos chamando funções do ViewModel. Estado imutável aqui = `sealed interface`/`data class`/`data object`: `DevicesState` (Home: `NoToken/Loading/Content/Empty/Error`), `TokenValidation` (`Idle/Validating/Failed/Done`), `DeviceQuery` (filtro e tamanho), `List<Device>` e `notice`. Toda coleta é `collectAsStateWithLifecycle()`.
- `DevicesState` mora no `domain` porque é o estado da listagem que o repositório publica e não tem nenhum tipo de UI; o ViewModel o expõe como o `UiState` da Home sem mapeamento 1:1 (seria só cópia de campos).
- O `HomeViewModel` expõe só `StateFlow` (`state`, `query`, `devices`, `token`, `tokenValidation`, `notice`), roda todo trabalho em `viewModelScope` (a validação do token sobrevive à rotação), não guarda `Context`, `View` nem Composable, e traduz falhas para texto com `messageOf`/`shortMessageOf` (via `ErrorMapper`), para a tela nunca importar o mapeador de `data/gdi`.
- Navegação por callbacks: o `NavHost` (`App.kt`) passa `onConnected`, `onCamera`, `onDevice`, `onBack`, `onLogout` às telas; o ViewModel nunca conhece o `NavController`.
- Telas de detalhe (câmera, fechadura, hub) chamam funções `suspend` do ViewModel que devolvem `Result` (`startLive`, `lockDetails`, `setLock`, `setLockVolume`, `lockVolume`, `lockHistory`, `firmware`, `isOnline`) e guardam o resultado em estado local da tela (`remember`), porque ele vale só enquanto a tela está aberta e é relido ao entrar. Mesmo aí a tela não fala com repositório nem converte erro sozinha.
- Um único `HomeViewModel` para o app inteiro: uma fonte de verdade para lista, token e detalhes. Se o app crescer, cada feature ganha o seu ViewModel (nota de modularização da seção 1.3).

**Clean Code (como fica no código).**

- Nomes que revelam intenção (`loadFirstPage`, `TokenValidation.Failed`, `FILTER_ON_DEVICE_NOTE`); abreviação (`m`, `pid`) só em escopo de poucas linhas, nunca em API pública.
- Funções pequenas com uma responsabilidade; uma função que precisa de comentário de bloco para cada trecho vira funções privadas com nome (ex.: `loadFirstPageOrLogout` no `SubmitTokenUseCase`).
- Sem números/strings mágicos: constantes nomeadas (`HISTORY_FIRST = 10`, `HISTORY_MORE = 30`, `DEFAULT_PAGE_SIZE`, `MIN_LENGTH`, `LIVE_NETWORK_CACHING_MS`); textos de regra num só lugar.
- Imutabilidade por padrão: `val`, `data class` + `copy`, `List` (não `MutableList`) em toda API pública; `MutableStateFlow` sempre privado, exposto como `StateFlow` com `asStateFlow()`.
- Erros explícitos: falha vira `AppError` (selado, `when` exaustivo) num só lugar (`GdiErrorMapper`); nenhuma exceção engolida em silêncio (todo `catch`/`runCatching`/`resultOf` cujo resultado é descartado tem comentário de porquê; casos aceitos na armadilha 28); `CancellationException` sempre relançada (`resultOf`); nada de `!!` sem justificativa.
- Sem código morto, comentado ou `TODO` em `commonMain`/`androidMain`/`iosMain`; nenhum aviso de parâmetro, variável ou import sem uso.
- Comentários só de **porquê** (regra 0.2.3).
- Testes legíveis: nome que diz o comportamento, blocos Arrange/Act/Assert, um comportamento por teste (ou uma tabela de casos do mesmo comportamento), fakes das interfaces do domínio em vez de mocks.
- Opcional: `detekt` e `ktlint` no CI (M11), se não atrasarem o build; o critério continua sendo as regras acima.

**Visão por plataforma** (onde cada peça roda):

```
            ┌──────────────────────────── commonMain ─────────────────────────────┐
  Android   │  ui/  (Compose: telas, componentes, tema)                            │   iOS
  MainActivity ─►  App() ── koinViewModel ──► HomeViewModel (StateFlow, viewModelScope)  ◄── MainViewController
            │                                     │ interfaces do domain           │      (ComposeUIViewController)
            │  domain/                            ▼                                │
            │   TokenRepository · DeviceRepository · SubmitTokenUseCase · ChangeLockVolumeUseCase · ErrorMapper
            │   (+ Device, DeviceQuery, DevicesState, AppError, TokenFormat, TokenStorage, DeviceProvider, LockController)
            │                                     ▲ implementam                    │
            │  data/                              │                                │
            │   TokenRepositoryImpl ◄──── DeviceRepositoryImpl (Mutex, geração, Jobs)
            │        │                      │ providers: List<DeviceProvider>      │
            │        │                      ▼                                      │
            │        │              data/gdi/GdiDeviceProvider ─► GdiLockController │
            │        │                      │                                      │
            │        └────────────► data/gdi/GdiApi (Ktor, renovação com Mutex)   │
            │                       GdiDeviceParser · GdiErrorMapper · GdiFormat   │
            │  player/CameraPlayer (expect)   platform/* (expect)   di/platformModule (expect)
            └──────────────┬───────────────────────────────────────────┬───────────┘
              androidMain  │                                           │ iosMain + Swift
   KeystoreTokenStorage (AES-GCM)                              KeychainTokenStorage (platform.Security)
   CameraPlayer = libVLC (AndroidView)                         CameraPlayer = UIKitView(NativePlayers.factory)
   Ktor OkHttp                                                 Ktor Darwin       VLCVideoPlayer.swift (VLCKit)
```

### 4.2 Contratos centrais (escreva exatamente assim)

`domain/TokenStorage.kt` — síncrono de propósito (Keystore/Keychain são leituras locais baratas):

```kotlin
/** Persistência segura do token entre execuções (RF01 + critério "persistência local/segurança"). */
interface TokenStorage {
    fun read(): String?
    /** write(null) apaga. */
    fun write(token: String?)
}
```

`domain/provider/DeviceProvider.kt` — o ponto de extensão "múltiplos parceiros":

```kotlin
/** Um parceiro de casa inteligente. Hoje só a GDI (Intelbras); outro parceiro = outra implementação registrada na DI. */
interface DeviceProvider {
    val id: String            // "gdi"
    val displayName: String   // "Intelbras Casa Inteligente"
    suspend fun listDevices(query: DeviceQuery): DevicePage
    /** [channel] = canalVideo: 0 na câmera de uma lente; 0 (móvel) ou 1 (fixa) nas "Dual". */
    suspend fun startLive(camera: Device, channel: Int = 0): StreamSession
    suspend fun stopLive(session: StreamSession)
    suspend fun firmware(device: Device): Firmware
    suspend fun isOnline(device: Device): Boolean?
    /** null quando o parceiro não tem fechaduras. */
    val locks: LockController?
}

interface LockController {
    /** Leituras em paralelo, cada uma isolada: um endpoint fora do ar não derruba os outros. */
    suspend fun details(lock: Device): LockDetails
    suspend fun setOpen(lock: Device, open: Boolean)
    /** Lança em erro de API (o chamador decide). */
    suspend fun volume(lock: Device): LockVolume?
    suspend fun setVolume(lock: Device, volume: LockVolume)
    /** 10 na abertura da tela; 30 em "Ver mais" (RF09). */
    suspend fun history(lock: Device, count: Int): List<LockEvent>
}
```

`domain/repository/TokenRepository.kt` — a porta do token que o ViewModel, os casos de uso e o `GdiApi` enxergam (implementação: `data/TokenRepositoryImpl`, M4):

```kotlin
package br.com.pompeo.casa.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Token da conta: em memória durante a validação, no armazenamento seguro depois dela (RF01). */
interface TokenRepository {
    /** Pré-preenchimento vindo de local.properties (desenvolvimento); nunca login automático. */
    val suggested: String?
    val token: StateFlow<String?>
    /** O token em memória está gravado no armazenamento seguro? false = vale só nesta sessão. */
    val persisted: Boolean
    /** Só memória: usado enquanto a primeira página ainda está validando o token. */
    fun setSession(token: String)
    /** Memória + armazenamento seguro. false = a gravação falhou e o token vale só nesta sessão. */
    fun persist(token: String): Boolean
    fun clear()
    /** "Ot_ab…wxyz" para exibir em tela; nunca o token inteiro. */
    fun masked(): String?
}
```

`domain/repository/DeviceRepository.kt` — fonte única da listagem e porta das operações por dispositivo (implementação: `data/DeviceRepositoryImpl`, M5):

```kotlin
package br.com.pompeo.casa.domain.repository

import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.model.StreamSession
import kotlinx.coroutines.flow.StateFlow

/** Dispositivos de todos os parceiros registrados (RF02, RF07, RF08) e as operações sobre cada um. */
interface DeviceRepository {
    val query: StateFlow<DeviceQuery>
    val state: StateFlow<DevicesState>
    /** Acumulado de todas as páginas carregadas (sem duplicatas), na ordem da API. As telas de detalhe leem daqui. */
    val devices: StateFlow<List<Device>>
    val providerNames: List<String>
    fun providerName(device: Device): String?

    /** Primeira página com a query atual. Devolve o estado calculado mesmo quando ele ficou velho e não foi publicado. */
    suspend fun loadFirstPage(): DevicesState
    fun refresh()
    /** Próxima página; erro aqui vira [DevicesState.Content.loadMoreError] e não apaga a lista. */
    fun loadMore()
    fun setOrigin(filter: OriginFilter)
    fun setPageSize(size: Int)
    /** Cancela consultas em voo, apaga o token e volta a [DevicesState.NoToken]. */
    fun logout()

    /** [channel] = lente (0/1), repassada ao parceiro sem interpretação. */
    suspend fun startLive(camera: Device, channel: Int = 0): StreamSession
    suspend fun stopLive(session: StreamSession)
    suspend fun lockDetails(lock: Device): LockDetails
    suspend fun setLock(lock: Device, open: Boolean)
    /** Só grava; reler e decidir o que mostrar é do [br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase]. */
    suspend fun setLockVolume(lock: Device, volume: LockVolume)
    suspend fun lockVolume(lock: Device): LockVolume?
    suspend fun lockHistory(lock: Device, quantity: Int): List<LockEvent>
    suspend fun firmware(device: Device): Firmware
    suspend fun isOnline(device: Device): Boolean?
}
```

A validação do token (`submitToken`) não está na interface: é regra de negócio que junta os dois repositórios e mora no `SubmitTokenUseCase` (seção 4.1, M5).

`domain/ErrorMapper.kt` — a apresentação traduz falhas sem importar `data/gdi` (implementação: `GdiErrorMapper`, M3):

```kotlin
package br.com.pompeo.casa.domain

/** Converte falhas técnicas em erros que a UI sabe explicar (RF04). */
interface ErrorMapper {
    fun toAppError(error: Throwable): AppError
    /** Texto curto para caber entre parênteses na UI: "Volume indisponível (a API respondeu HTTP 500)". */
    fun toShortMessage(error: Throwable): String
}
```

`domain/DevicesState.kt` e `domain/AppError.kt`: ver M2.

### 4.3 Injeção (Koin) e `expect`/`actual`

`di/Koin.kt`:

```kotlin
package br.com.pompeo.casa.di

import br.com.pompeo.casa.GdiBuildConfig
import br.com.pompeo.casa.data.DeviceRepositoryImpl
import br.com.pompeo.casa.data.TokenRepositoryImpl
import br.com.pompeo.casa.data.gdi.GdiApi
import br.com.pompeo.casa.data.gdi.GdiDeviceProvider
import br.com.pompeo.casa.data.gdi.GdiErrorMapper
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.provider.DeviceProvider
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import br.com.pompeo.casa.ui.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Armazenamento seguro do token é nativo: Keystore no Android, Keychain no iOS. */
expect val platformModule: Module

// Hilt só existe no Android; em código multiplataforma o Koin cumpre o papel de injeção.
// Raiz de composição: único arquivo que conhece domain, data e ui. Cada implementação é registrada pela
// INTERFACE do domain (single<TokenRepository> { …Impl }), então quem injeta nunca vê a classe concreta.
val appModule = module {
    // ---------- data ----------
    // GdiBuildConfig.TOKEN (local.properties) é só sugestão para a tela de token, nunca login automático.
    single<TokenRepository> { TokenRepositoryImpl(storage = get(), suggested = GdiBuildConfig.TOKEN) }
    single { GdiApi(tokens = get(), baseUrl = GdiBuildConfig.BASE_URL) }
    // Outro parceiro = outro single<DeviceProvider>(named("...")); o repositório recebe todos via getAll.
    single<DeviceProvider>(named("gdi")) { GdiDeviceProvider(get()) }
    single<ErrorMapper> { GdiErrorMapper }
    single<DeviceRepository> {
        DeviceRepositoryImpl(
            tokens = get(),
            providers = getAll<DeviceProvider>(),
            // Vive enquanto o app vive; SupervisorJob: a falha de um job não cancela os outros.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    // ---------- domain: casos de uso sem estado, uma instância por injeção ----------
    factoryOf(::SubmitTokenUseCase)
    factoryOf(::ChangeLockVolumeUseCase)
    // ---------- apresentação ----------
    viewModelOf(::HomeViewModel)
}

/** Chamado uma vez pela Application no Android. */
fun initKoin(config: KoinAppDeclaration): KoinApplication = startKoin {
    config()
    modules(appModule, platformModule)
}

/** Sem parâmetro para o Swift (`KoinKt.doInitKoin()`): valores padrão não são exportados ao Obj-C. */
fun initKoin(): KoinApplication = initKoin {}
```

No M1 este arquivo tem só `expect val platformModule`, `appModule = module { }` vazio e as duas `initKoin`; as linhas do `appModule` entram conforme as classes existem (M4: `TokenRepository` → `TokenRepositoryImpl`, `GdiApi`; M5: provider, `DeviceRepository` → `DeviceRepositoryImpl` e os dois `factoryOf` dos casos de uso; M6: `ErrorMapper` → `GdiErrorMapper` e `viewModelOf`).

`androidMain/.../di/PlatformModule.android.kt` (imports: `br.com.pompeo.casa.data.security.KeystoreTokenStorage`, `br.com.pompeo.casa.domain.TokenStorage`, `org.koin.android.ext.koin.androidContext`, `org.koin.core.module.Module`, `org.koin.dsl.module`; no M1, `module { }` vazio):

```kotlin
actual val platformModule: Module = module {
    single<TokenStorage> { KeystoreTokenStorage(androidContext()) }
}
```

`iosMain/.../di/PlatformModule.ios.kt`:

```kotlin
actual val platformModule: Module = module {
    single<TokenStorage> { KeychainTokenStorage() }
}
```

---

## 5. Marcos de implementação (nesta ordem)

Formato de cada marco: **Objetivo · Arquivos · Código das partes difíceis · Testes · Pronto quando · Verificar com**. Os marcos de tela (M6–M9) começam com a linha **Referência visual** (regra 0.2.7). Ao terminar um marco, faça commit.

---

### M1 — Projeto compila nas três frentes (Android, iOS simulador, iOS aparelho)

**Objetivo.** Esqueleto vazio, mas completo: Gradle, Koin, `App()` mínimo, entrada Android, entrada iOS com o framework Kotlin e o pacote VLCKit resolvido, CI ainda não.

**Arquivos.** Tudo da seção 2; `App.kt` provisório (`MaterialTheme { Text("Casa Inteligente") }`); `di/Koin.kt` com `appModule` vazio por enquanto + `platformModule` actual com módulo vazio; `iosMain/MainViewController.kt`; `iosApp/iosApp/iOSApp.swift` e `ContentView.swift`; `README.md` inicial.

```kotlin
// iosMain/kotlin/br/com/pompeo/casa/MainViewController.kt
package br.com.pompeo.casa

import androidx.compose.ui.window.ComposeUIViewController

/** Exportado para o Swift como MainViewControllerKt.MainViewController(). */
fun MainViewController() = ComposeUIViewController { App() }
```

```swift
// iosApp/iosApp/ContentView.swift
import SwiftUI
import UIKit
import Shared

/// Hospeda a árvore Compose Multiplatform inteira; não há telas SwiftUI além desta.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // O Compose ocupa a tela inteira e trata os insets por conta própria.
        ComposeView().ignoresSafeArea()
    }
}
```

```swift
// iosApp/iosApp/iOSApp.swift (versão M1; o registro do player entra no M8)
import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Mesmo grafo Koin do Android: repositórios, token store e ViewModel.
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

**Testes.** Um teste trivial em `commonTest` (`assertTrue(true)`) só para provar que a task `testAndroidHostTest` existe e roda; apague no M2.

**Pronto quando.** APK instala e abre no aparelho Android mostrando o texto; o app abre no simulador iOS **sem crash** (se crashar ao abrir, faltou `CADisableMinimumFrameDurationOnPhone`); o build para iPhone físico assina com o Team correto.

**Verificar com.**

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' -resolvePackageDependencies
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

---

### M2 — Domínio puro + `TokenFormat`

**Objetivo.** Todos os modelos e regras sem dependência de framework.

**Arquivos.** `domain/model/Device.kt`, `domain/model/DeviceQuery.kt`, `domain/AppError.kt`, `domain/DevicesState.kt`, `domain/TokenFormat.kt`, `domain/TokenStorage.kt`, `domain/ResultOf.kt`, `domain/ErrorMapper.kt`, `domain/provider/DeviceProvider.kt`, `domain/repository/TokenRepository.kt`, `domain/repository/DeviceRepository.kt` (as interfaces da seção 4.2; as implementações vêm no M4/M5 e os casos de uso no M5).

`domain/model/Device.kt`:

```kotlin
package br.com.pompeo.casa.domain.model

/** Linhas Mibo suportadas pela GDI (câmeras iM, fechaduras, hubs Zigbee, sensores, lâmpadas). */
enum class DeviceKind(val label: String) {
    CAMERA("Câmera"), LOCK("Fechadura"), HUB("Hub"), SENSOR("Sensor"), LAMP("Lâmpada"), OTHER("Dispositivo"),
}

/** Origem do vínculo com a conta, como a GDI devolve por dispositivo ("vinculado" / "compartilhado"). */
enum class DeviceOrigin(val label: String) {
    LINKED("Vinculado"), SHARED("Compartilhado"), UNKNOWN("—");

    companion object {
        // Singular no item da listagem, plural no filtro do pedido: aceita os dois.
        fun parse(raw: String?): DeviceOrigin =
            when (raw?.trim()?.lowercase()) {
                "vinculado", "vinculados" -> LINKED
                "compartilhado", "compartilhados" -> SHARED
                else -> UNKNOWN
            }
    }
}

/** Dispositivo de qualquer parceiro. [providerId] identifica o parceiro ("gdi"). */
data class Device(
    val providerId: String,
    /** Identificador no parceiro (número de série na GDI). */
    val ns: String,
    val name: String,
    val kind: DeviceKind,
    val model: String?,
    val productId: String?,
    /** Três estados: null = a API não informou (não é "offline"). */
    val online: Boolean?,
    val origin: DeviceOrigin,
    val subdevice: Boolean = false,
    val parentNs: String? = null,
    val parentProductId: String? = null,
    val version: String? = null,
    /** Último contato já formatado pelo parceiro ("dd/MM/aaaa HH:mm:ss", " UTC" quando a origem marca). */
    val lastOnline: String? = null,
    val updateAvailable: Boolean? = null,
) {
    val isCamera: Boolean get() = kind == DeviceKind.CAMERA
    val isLock: Boolean get() = kind == DeviceKind.LOCK
}

/**
 * Sessão ao vivo. O Swagger documenta url (MP4 por HTTPS), monitor_url e session_id, mas a API real
 * devolve só `url` = rtsp:// de um proxy da nuvem; por isso os demais campos são opcionais.
 */
data class StreamSession(val sessionId: String?, val streamUrl: String, val monitorUrl: String?, val quotaGb: Double?)

/**
 * Um registro do histórico de aberturas (RF09), já pronto para exibir: o parceiro converte o formato cru dele
 * ([time] = "dd/MM/aaaa HH:mm:ss"; [description] = "Remoto (APP)", "Por dentro (manual)"…), e a UI não conhece a GDI.
 */
data class LockEvent(val time: String, val description: String)

data class Firmware(val version: String?, val updateAvailable: Boolean?)

/** Volume da fechadura MFR: 0 = mudo … 3 = alto (contrato da GDI: 0 a 3). */
enum class LockVolume(val level: Int, val label: String) {
    MUTE(0, "Mudo"), LOW(1, "Baixo"), MEDIUM(2, "Médio"), HIGH(3, "Alto");

    companion object {
        fun fromLevel(level: Int?): LockVolume? = entries.firstOrNull { it.level == level }
    }
}

/**
 * Leitura completa da fechadura. Campos nulos = endpoint falhou ou não respondeu (cada um é
 * independente). [volumeError]/[historyError] separam "vazio" de "erro" na UI.
 */
data class LockDetails(
    val open: Boolean?,
    val remoteEnabled: Boolean?,
    val battery: Int?,
    val volume: LockVolume?,
    val volumeError: String?,
    val history: List<LockEvent>,
    val historyError: String? = null,
)
```

`domain/model/DeviceQuery.kt`:

```kotlin
/** Filtro de origem enviado no campo "origem" de /produtos/listar-dispositivos/v1. */
enum class OriginFilter(val apiValue: String, val label: String) {
    ALL("todos", "Todos"),
    LINKED("vinculados", "Vinculados"),
    SHARED("compartilhados", "Compartilhados");

    /** Filtro defensivo no cliente: só exclui quando a origem é conhecida e diferente da pedida. */
    fun accepts(origin: DeviceOrigin): Boolean =
        when (this) {
            ALL -> true
            LINKED -> origin != DeviceOrigin.SHARED
            SHARED -> origin != DeviceOrigin.LINKED
        }
}

data class DeviceQuery(val origin: OriginFilter = OriginFilter.ALL, val page: Int = 1, val pageSize: Int = DEFAULT_PAGE_SIZE) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 5
        /** 2 existe para o avaliador ver "Carregar mais" funcionando na conta de teste (3 dispositivos). */
        val PAGE_SIZES = listOf(2, 5, 10, 50)
    }
}

/** Uma página da listagem. A GDI não devolve total: [hasMore] = veio página cheia. */
data class DevicePage(val devices: List<Device>, val page: Int, val pageSize: Int) {
    val hasMore: Boolean get() = devices.size >= pageSize
}
```

`domain/AppError.kt`:

```kotlin
/** Erros que a UI sabe explicar (RF04). [userMessage] é o texto exibido; [detail] é técnico (diagnóstico). */
sealed class AppError(val userMessage: String, val detail: String? = null) {
    data object TokenMissing : AppError("Informe o token de acesso gerado no portal Casa Inteligente.")

    class TokenInvalid(detail: String?) :
        AppError("Token inválido ou expirado. Gere um novo token no portal (Contas → Token Temporário) e tente de novo.", detail)

    /** A GDI distingue expirado (403) de inválido (401); a UI oferece "Trocar token" nos dois. */
    class TokenExpired(detail: String?) :
        AppError("Seu token expirou (ele vale cerca de 2 horas). Gere um novo no portal (Contas → Token Temporário) e tente de novo.", detail)

    class Network(detail: String?) : AppError("Sem conexão com a internet. Verifique a rede e tente novamente.", detail)

    class Timeout(detail: String?) : AppError("A plataforma Intelbras demorou a responder. Tente novamente.", detail)

    class Server(val httpStatus: Int, detail: String?) :
        AppError("A plataforma Intelbras respondeu com erro (HTTP $httpStatus). Tente novamente em instantes.", detail)

    class Unexpected(detail: String?) : AppError("Algo deu errado. Tente novamente.", detail)

    override fun toString(): String = "${this::class.simpleName}(${detail ?: ""})"
}
```

`domain/DevicesState.kt`:

```kotlin
/** Estado único da listagem de dispositivos (RF02, RF04, RF07, RF08). */
sealed interface DevicesState {
    data object NoToken : DevicesState
    /** Primeira página em andamento, sem conteúdo anterior. */
    data object Loading : DevicesState

    data class Content(
        val devices: List<Device>,
        val query: DeviceQuery,
        val hasMore: Boolean,
        val pagesLoaded: Int,
        /** refresh() com conteúdo já na tela: barra fina no topo, a lista continua visível. */
        val refreshing: Boolean = false,
        /** "Carregar mais" em andamento. */
        val loadingMore: Boolean = false,
        /** Erro ao carregar mais não derruba a lista. */
        val loadMoreError: AppError? = null,
        /** Ex.: "Filtro aplicado no aparelho". */
        val note: String? = null,
    ) : DevicesState

    /** 200 com lista vazia na página 1. */
    data class Empty(val query: DeviceQuery) : DevicesState
    data class Error(val error: AppError, val query: DeviceQuery) : DevicesState
}
```

`domain/TokenFormat.kt`:

```kotlin
/** Regras de forma do token. Kotlin puro: commonMain não compila Java. */
object TokenFormat {
    private const val MIN_LENGTH = 8
    private const val MASK_MIN_LENGTH = 12
    private const val HIDDEN = "••••"

    /** `trim`; `""` para null. */
    fun normalize(raw: String?): String = raw?.trim().orEmpty()

    /** Normalizado com pelo menos 8 caracteres e sem espaço em branco interno. */
    fun isPlausible(raw: String?): Boolean {
        val value = normalize(raw)
        return value.length >= MIN_LENGTH && value.none { it.isWhitespace() }
    }

    /** "Ot_ab…wxyz": 5 primeiros + "…" + 4 últimos; tokens curtos (< 12) viram "••••" para não vazar. */
    fun mask(token: String?): String {
        val value = normalize(token)
        if (value.length < MASK_MIN_LENGTH) return HIDDEN
        return value.take(5) + "…" + value.takeLast(4)
    }
}
```

`domain/ResultOf.kt` — o `runCatching` que **não** engole cancelamento (armadilha 28); use-o em toda chamada suspensa que vira `Result` (ViewModel, `DeviceRepository`):

```kotlin
package br.com.pompeo.casa.domain

import kotlin.coroutines.cancellation.CancellationException

/** Como runCatching, mas relança CancellationException: cancelar a tela/escopo nunca vira "erro" na UI. */
inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
```

`inline` permite chamar funções `suspend` dentro do bloco quando o chamador é `suspend`. `kotlin.coroutines.cancellation.CancellationException` é da stdlib (o `kotlinx.coroutines.CancellationException` é `typealias` dela), então o `domain` continua sem dependência externa.

`DeviceProvider`/`LockController`/`TokenStorage`/`ErrorMapper` e as interfaces `TokenRepository`/`DeviceRepository` (em `domain/repository/`): seção 4.2, exatamente como lá.

**Testes.** `TokenFormatTest` (3): `normalizeTrimsAndHandlesNull`, `plausibleNeedsEightCharsWithoutInnerSpaces`, `maskShowsOnlyEdgesAndHidesShortTokens`. `DomainModelTest` (4): `originParseAcceptsSingularPluralCaseAndSpaces`, `originFilterAcceptsUnknownOrigin` (ALL aceita tudo; LINKED rejeita só SHARED; SHARED rejeita só LINKED; UNKNOWN sempre passa), `devicePageHasMoreWhenFull` (5 de 5 → true; 2 de 5 → false), `lockVolumeFromLevel` (0..3 e `null`/4 → null).

**Pronto quando.** 7 testes verdes; `domain/` não importa nada fora de `kotlin.*`, `kotlinx.coroutines` (só os `StateFlow` das interfaces de repositório) e do próprio pacote.

**Verificar com.** `./gradlew :shared:testAndroidHostTest` e a regra de dependência do `domain` (seção 4.1; tem que vir vazio):

```sh
grep -rnE "^import (io\.ktor|org\.koin|androidx|org\.jetbrains\.compose|kotlinx\.serialization|br\.com\.pompeo\.casa\.(data|ui|di|player|platform))" \
  shared/src/commonMain/kotlin/br/com/pompeo/casa/domain
```

---

### M3 — Cliente GDI (Ktor), parser, formatação e mapeamento de erro

**Objetivo.** Falar com a API real de forma robusta: `text/plain`, corpo-string, envelope de erro em 200, renovação de token **serializada** com `Mutex`, `ns` composto, leituras paralelas da fechadura.

**Arquivos.** `data/gdi/GdiApi.kt`, `data/gdi/GdiDeviceParser.kt`, `data/gdi/GdiDeviceProvider.kt`, `data/gdi/GdiErrorMapper.kt`, `data/gdi/GdiFormat.kt`. Para compilar, crie já o `data/TokenRepositoryImpl.kt` do M4: o `GdiApi` recebe a **interface** `TokenRepository` (domain) e os testes deste marco passam `TokenRepositoryImpl(InMemoryTokenStorage(token))`.

#### `GdiApi.kt` (o núcleo; escreva assim)

```kotlin
package br.com.pompeo.casa.data.gdi

/** Erro devolvido pela GDI (HTTP fora de 2xx ou envelope `{"status":"erro"}`). */
open class GdiException(val httpStatus: Int, message: String) : Exception(message)

/** Chamada sem token configurado: a UI leva para a tela de token em vez de "token inválido". */
class GdiNoTokenException : GdiException(401, "Nenhum token GDI configurado")

/**
 * Cliente da API GDI. Todas as rotas são POST + JSON com `Authorization: Bearer <token>`.
 * Kotlin puro + Ktor: o mesmo código roda no Android (OkHttp) e no iOS (Darwin).
 * [client] é injetável para os testes usarem MockEngine.
 */
class GdiApi(
    private val tokens: TokenRepository,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultClient(),
) {
    // Uma renovação por vez: as 5 leituras paralelas da fechadura não podem renovar o mesmo token 5 vezes.
    private val renewLock = Mutex()

    suspend fun listDevices(page: Int = 1, pageSize: Int = 50, origin: String = "todos"): JsonObject =
        post("/produtos/listar-dispositivos/v1") {
            // Os três campos são obrigatórios: sem "origem" a API devolve HTTP 500.
            put("tamanhoPagina", pageSize)
            put("pagina", page)
            put("origem", origin)
        }

    suspend fun isOnline(ns: String): Boolean? = post("/produtos/online/v1") { put("ns", ns) }.findBoolean("online")

    /** Abre uma sessão ao vivo. [streamGb] limita a banda cobrada da cota. streamId 0 + canal 0 dá HTTP 500. */
    suspend fun createVideoStream(ns: String, streamGb: Double = 0.2, channel: Int = 0, streamId: Int = 1): StreamSession {
        val json = post("/cameras/criar-fluxo-video/v1") {
            put("ns", ns)
            put("stream_gb", streamGb)
            put("canalVideo", channel)
            put("streamId", streamId)
        }
        val data = json["data"]?.jsonObject ?: throw GdiException(200, json.message() ?: "Resposta sem data")
        return StreamSession(
            sessionId = data.string("session_id"),
            streamUrl = data.string("url") ?: throw GdiException(200, "Sessão sem URL de vídeo"),
            monitorUrl = data.string("monitor_url"),
            quotaGb = data["quota_gb"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
        )
    }

    /** Sem session_id (resposta RTSP real) não há o que encerrar: a URL expira sozinha (parâmetro expire). */
    suspend fun endSession(sessionId: String?) {
        if (sessionId == null) return
        resultOf { post("/streaming/encerrar-sessao/v1") { put("session_id", sessionId) } } // falha ao encerrar não é erro de tela
    }

    // ---------- Fechadura (subdispositivo Zigbee: ns = NsFechadura_NsHub_IdProdutoHub) ----------

    suspend fun lockIsOpen(ns: String, productId: String): Boolean? =
        post("/fechaduras/status-abertura/v1") { put("ns", ns); put("idProduto", productId) }.findBoolean("aberto")

    suspend fun lockRemoteOpenEnabled(ns: String, productId: String): Boolean? =
        post("/fechaduras/status-abrir-remoto/v1") { put("ns", ns); put("idProduto", productId) }.findBoolean("habilitado")

    suspend fun battery(ns: String, productId: String): Int? =
        post("/produtos/bateria/v1") { put("ns", ns); put("idProduto", productId) }.findString("bateria")?.toIntOrNull()

    /** Sem idProduto no corpo (contrato real). */
    suspend fun lockHistory(ns: String, count: Int = 10): List<LockEvent> {
        val data = post("/fechaduras/historico-abertura/v1") { put("ns", ns); put("quantidade", count) }["data"]
        return (data as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            // Formatado aqui: só data/gdi conhece o formato cru da GDI; a UI recebe o texto pronto.
            LockEvent(
                time = GdiFormat.dateTime(obj.string("tempoLocal")),
                description = GdiFormat.lockEventType(obj.string("tipo").orEmpty(), obj.string("nome").orEmpty()),
            )
        }
    }

    /** Aciona a fechadura REAL. [open] = true destranca; false tranca. A UI só chama após confirmação. */
    suspend fun controlLock(ns: String, productId: String, open: Boolean) {
        post("/fechaduras/controle-fechadura/v1") { put("ns", ns); put("aberto", open); put("idProduto", productId) }
    }

    /** Volume atual (0 = mudo … 3 = alto). Fato: devolveu HTTP 500 na fechadura de teste. */
    suspend fun lockVolume(ns: String, productId: String): Int? =
        post("/fechaduras/volume/v1") { put("ns", ns); put("idProduto", productId) }.findString("volume")?.toIntOrNull()

    suspend fun setLockVolume(ns: String, productId: String, level: Int) {
        post("/fechaduras/mudar-volume/v1") { put("ns", ns); put("idProduto", productId); put("volume", level) }
    }

    suspend fun firmware(ns: String): Firmware {
        val json = post("/produtos/versao/v1") { put("ns", ns) }
        return Firmware(json.findString("versao"), json.findBoolean("atualizacaoDisponivel"))
    }

    /** Troca o token atual por um novo e o persiste: o antigo deixa de valer. */
    suspend fun renewToken(): String = renewLock.withLock { renewLocked() }

    /**
     * Serializa a renovação. Quem entra depois e encontra um token diferente do que falhou só repete a
     * chamada com ele: outra requisição paralela já renovou, e renovar de novo um token morto é recusado.
     */
    private suspend fun renewedTokenAfter(failed: String): String =
        renewLock.withLock {
            val latest = tokens.token.value ?: throw GdiNoTokenException()
            if (latest != failed) latest else renewLocked()
        }

    /** Só com [renewLock] já adquirido (o Mutex do kotlinx não é reentrante). */
    private suspend fun renewLocked(): String {
        val current = tokens.token.value ?: throw GdiNoTokenException()
        val response: HttpResponse = client.post("$baseUrl/autenticacao/renovar-token/v1") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $current")
            setBody(buildJsonObject { put("token", current) }) // a API exige o token no header E no corpo
        }
        val json = response.parseJson()
        val renewed = json.findString("token") ?: throw GdiException(response.status.value, json.message() ?: "Falha ao renovar token")
        tokens.persist(renewed)
        return renewed
    }

    private suspend fun post(path: String, body: JsonObjectBuilder.() -> Unit): JsonObject {
        // Corpo montado à mão: todos os campos vão explícitos (DTO com encodeDefaults=false mandaria "{}" e daria 500).
        val payload = buildJsonObject(body)
        val sentWith = tokens.token.value ?: throw GdiNoTokenException()
        val first = send(path, payload, sentWith)
        // 401 (inválido) ou 403 (expirado): renova uma vez (ou reaproveita a renovação de uma chamada paralela) e repete.
        if (first.status.value == 401 || first.status.value == 403) {
            val renewed =
                try {
                    renewedTokenAfter(failed = sentWith)
                } catch (noToken: GdiNoTokenException) {
                    // Subclasse de GdiException: precisa vir antes. Logout durante a chamada = "sem token".
                    throw noToken
                } catch (renewError: GdiException) {
                    // Mantém o status ORIGINAL (401 => TokenInvalid, 403 => TokenExpired); o motivo da renovação vai na mensagem.
                    // Falha de rede na renovação não é capturada: sobe como está e vira Network/Timeout.
                    throw GdiException(first.status.value, renewError.message ?: first.parseJson().message() ?: "Token inválido ou expirado")
                }
            return send(path, payload, renewed).successJson() // repete uma única vez
        }
        return first.successJson()
    }

    private suspend fun send(path: String, payload: JsonObject, token: String): HttpResponse =
        client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(payload)
        }

    /** Sucesso = HTTP 2xx e envelope sem "status":"erro". NÃO exige "data": comandos respondem só {"status":"sucesso"}. */
    private suspend fun HttpResponse.successJson(): JsonObject {
        val json = parseJson()
        if (status.value !in 200..299 || json.string("status") == "erro")
            throw GdiException(status.value, json.message() ?: "HTTP ${status.value}")
        return json
    }

    // A GDI responde JSON com "content-type: text/plain"; por isso lemos o texto e convertemos
    // manualmente em vez de depender do ContentNegotiation. Corpo que não é objeto vira {"raw": texto}:
    // o 401 real é a string JSON "Não autorizado", guardada sem as aspas para o detalhe ficar legível.
    private suspend fun HttpResponse.parseJson(): JsonObject {
        val text = bodyAsText()
        val element = runCatching { lenientJson.parseToJsonElement(text) }.getOrNull()
        return element as? JsonObject
            ?: buildJsonObject { put("raw", ((element as? JsonPrimitive)?.contentOrNull ?: text).take(500)) }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api-casainteligente.intelbras.com.br"
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        // ContentNegotiation fica instalado só para SERIALIZAR o pedido (setBody(JsonObject)).
        // Sem plugin de Logging: ele imprimiria o header Authorization (token) no logcat/console.
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        }

        internal fun JsonObject.message(): String? = string("msg") ?: string("message") ?: string("erro") ?: string("raw")
        internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        /** Procura uma chave em qualquer profundidade (as respostas da GDI variam de envelope). */
        internal fun JsonElement.findString(key: String): String? = find(key)?.let { (it as? JsonPrimitive)?.contentOrNull }
        internal fun JsonElement.findBoolean(key: String): Boolean? =
            (find(key) as? JsonPrimitive)?.contentOrNull?.let { it == "true" || it == "1" }

        private fun JsonElement.find(key: String): JsonElement? {
            if (this is JsonObject) {
                this[key]?.let { return it }
                for (value in values) value.find(key)?.let { return it }
            }
            if (this is JsonArray) for (value in this) value.find(key)?.let { return it }
            return null
        }
    }
}
```

Imports: `br.com.pompeo.casa.domain.repository.TokenRepository` (a interface; o `GdiApi` não conhece o `Impl`), `br.com.pompeo.casa.domain.resultOf`, `br.com.pompeo.casa.domain.model.{Firmware, LockEvent, StreamSession}`, `io.ktor.client.HttpClient`, `io.ktor.client.plugins.HttpTimeout`, `io.ktor.client.plugins.contentnegotiation.ContentNegotiation`, `io.ktor.client.request.{header,post,setBody}`, `io.ktor.client.statement.{HttpResponse,bodyAsText}`, `io.ktor.http.{ContentType,HttpHeaders,contentType}`, `io.ktor.serialization.kotlinx.json.json`, `kotlinx.coroutines.sync.{Mutex,withLock}`, `kotlinx.serialization.json.*` (`Json`, `JsonArray`, `JsonElement`, `JsonObject`, `JsonObjectBuilder`, `JsonPrimitive`, `buildJsonObject`, `contentOrNull`, `jsonObject`, `jsonPrimitive`, `put`).

#### `GdiDeviceParser.kt`

```kotlin
/**
 * O Swagger não documenta o corpo de /produtos/listar-dispositivos/v1: o parser aceita variações de
 * envelope e de nomes de campo e devolve [Device] com providerId = "gdi".
 */
object GdiDeviceParser {
    const val PROVIDER_ID = "gdi"

    private val nsKeys = listOf("ns", "numeroSerie", "serial", "serialNumber", "deviceSn")
    private val nameKeys = listOf("nome", "name", "deviceName", "nomeDispositivo", "apelido")
    private val productKeys = listOf("idProduto", "productId", "pid")
    private val modelKeys = listOf("modelo", "model", "productName", "nomeProduto")
    private val typeKeys = listOf("tipo", "type", "categoria", "category", "deviceType", "tipoDispositivo")
    private val onlineKeys = listOf("online", "isOnline", "status")
    private val originKeys = listOf("origem", "origin")

    fun parse(root: JsonElement): List<Device> = collectObjects(root).mapNotNull { it.toDevice() }.distinctBy { it.ns }

    /** Tipo pelo modelo ou nome (a GDI não devolve categoria). O hub MCA 1002 tem modelo "IOT-ZG2-IB". A ordem importa. */
    internal fun inferKind(type: String?, model: String?, name: String?): DeviceKind {
        val t = type?.lowercase().orEmpty()
        val tags = listOfNotNull(model, name).map { it.trim().uppercase() }
        fun any(vararg prefixes: String) = tags.any { tag -> prefixes.any { tag.startsWith(it) } }
        return when {
            "cam" in t || any("IM") -> DeviceKind.CAMERA
            "fechadura" in t || "lock" in t || any("MFR", "MFV", "MFD") -> DeviceKind.LOCK
            "hub" in t || any("MCA", "IOT-ZG") -> DeviceKind.HUB
            "sensor" in t || any("MSM", "MSA", "MTU") -> DeviceKind.SENSOR
            "lamp" in t || any("MLS", "ELW") -> DeviceKind.LAMP
            type == null && model == null -> DeviceKind.CAMERA // sem pista nenhuma: a linha original da GDI era só câmeras
            else -> DeviceKind.OTHER
        }
    }

    private fun JsonObject.toDevice(): Device? {
        val ns = first(nsKeys) ?: return null
        val model = first(modelKeys)
        val type = first(typeKeys)
        val name = first(nameKeys) ?: model ?: ns
        return Device(
            providerId = PROVIDER_ID,
            ns = ns,
            name = name,
            kind = inferKind(type, model, name),
            model = model,
            productId = first(productKeys),
            // "online": true literal, ou "status": "online"/"offline", ou "1".
            online = onlineKeys.firstNotNullOfOrNull { key -> bool(key) ?: (this[key] as? JsonPrimitive)?.contentOrNull?.let { it == "1" || it.equals("online", true) } },
            origin = DeviceOrigin.parse(first(originKeys)),
            subdevice = bool("subdispositivo") ?: false,
            parentNs = first(listOf("dispositivoPai")),
            parentProductId = first(listOf("idProdutoDispositivoPai")),
            version = first(listOf("versao", "version")),
            lastOnline = first(listOf("ultimaVezOnline"))?.let(GdiFormat::dateTime),
            updateAvailable = bool("atualizacaoDisponivel"),
        )
    }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.first(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }

    /** Todos os objetos que parecem dispositivos (têm campo de número de série), em qualquer nível; não desce dentro deles. */
    private fun collectObjects(element: JsonElement): List<JsonObject> =
        when (element) {
            is JsonObject -> if (nsKeys.any { element.containsKey(it) }) listOf(element) else element.values.flatMap { collectObjects(it) }
            is JsonArray -> element.flatMap { collectObjects(it) }
            else -> emptyList()
        }
}
```

#### `GdiFormat.kt`

```kotlin
/** Formatação dos campos crus da GDI. Sem String.format (não existe em commonMain). */
object GdiFormat {
    /** "20261002T162932" -> "02/10/2026 16:29:32"; com "Z" no fim, marca UTC. Texto que não casa volta cru. */
    fun dateTime(raw: String?): String {
        val m = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z?)""").matchEntire(raw?.trim().orEmpty()) ?: return raw.orEmpty()
        val (y, mo, d, h, mi, s, z) = m.destructured
        return "$d/$mo/$y $h:$mi:$s" + if (z == "Z") " UTC" else ""
    }

    /** Origem da abertura no histórico. Só usuarioRemoto e interno foram vistos na API; o resto é hipótese. */
    fun lockEventType(type: String, name: String): String =
        when (type) {
            "usuarioRemoto" -> "Remoto" + if (name.isNotBlank()) " ($name)" else ""
            "interno" -> "Por dentro (manual)"
            "senha" -> "Senha" + if (name.isNotBlank()) " ($name)" else ""
            "digital" -> "Digital" + if (name.isNotBlank()) " ($name)" else ""
            "cartao", "tag" -> "Cartão/Tag" + if (name.isNotBlank()) " ($name)" else ""
            "chave" -> "Chave mecânica"
            else -> listOf(type, name).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Desconhecido" }
        }
}
```

#### `GdiDeviceProvider.kt`

```kotlin
/** Subdispositivos (Zigbee) são endereçados na GDI como NsDispositivo_NsHub_IdProdutoHub. */
internal val Device.gdiApiNs: String
    get() = if (subdevice && parentNs != null && parentProductId != null) "${ns}_${parentNs}_$parentProductId" else ns

/** Parceiro Intelbras (GDI): adapta o [GdiApi] ao contrato neutro [DeviceProvider]. */
class GdiDeviceProvider(private val api: GdiApi) : DeviceProvider {
    override val id: String = GdiDeviceParser.PROVIDER_ID
    override val displayName: String = "Intelbras Casa Inteligente"

    override suspend fun listDevices(query: DeviceQuery): DevicePage {
        val json = api.listDevices(page = query.page, pageSize = query.pageSize, origin = query.origin.apiValue)
        return DevicePage(GdiDeviceParser.parse(json), page = query.page, pageSize = query.pageSize)
    }

    /** [channel] vira canalVideo; online e vídeo usam o ns puro. */
    override suspend fun startLive(camera: Device, channel: Int): StreamSession = api.createVideoStream(camera.ns, channel = channel)
    override suspend fun stopLive(session: StreamSession) = api.endSession(session.sessionId)
    override suspend fun firmware(device: Device): Firmware = api.firmware(device.gdiApiNs)
    override suspend fun isOnline(device: Device): Boolean? = api.isOnline(device.ns)
    override val locks: LockController = GdiLockController(api)
}

/** Fechadura MFR via GDI (subdispositivo Zigbee do hub). */
class GdiLockController(private val api: GdiApi) : LockController {
    /** 5 leituras independentes em paralelo; cada uma com runCatching, então uma falha não cancela as irmãs. */
    override suspend fun details(lock: Device): LockDetails = coroutineScope {
        val pid = lock.gdiProductId
        val ns = lock.gdiApiNs
        val open = async { runCatching { api.lockIsOpen(ns, pid) }.getOrNull() }
        val remote = async { runCatching { api.lockRemoteOpenEnabled(ns, pid) }.getOrNull() }
        val battery = async { runCatching { api.battery(ns, pid) }.getOrNull() }
        val volume = async { runCatching { LockVolume.fromLevel(api.lockVolume(ns, pid)) } }
        val history = async { runCatching { api.lockHistory(ns, DEFAULT_HISTORY) } }
        val volumeResult = volume.await()
        val historyResult = history.await()
        LockDetails(
            open = open.await(),
            remoteEnabled = remote.await(),
            battery = battery.await(),
            volume = volumeResult.getOrNull(),
            // Fato: /fechaduras/volume/v1 devolveu HTTP 500; a UI mostra "a API respondeu HTTP 500".
            volumeError = volumeResult.exceptionOrNull()?.toShortMessage(),
            history = historyResult.getOrDefault(emptyList()),
            historyError = historyResult.exceptionOrNull()?.toAppError()?.userMessage,
        )
    }

    override suspend fun setOpen(lock: Device, open: Boolean) = api.controlLock(lock.gdiApiNs, lock.gdiProductId, open)
    override suspend fun volume(lock: Device): LockVolume? = LockVolume.fromLevel(api.lockVolume(lock.gdiApiNs, lock.gdiProductId))
    override suspend fun setVolume(lock: Device, volume: LockVolume) = api.setLockVolume(lock.gdiApiNs, lock.gdiProductId, volume.level)
    override suspend fun history(lock: Device, count: Int): List<LockEvent> = api.lockHistory(lock.gdiApiNs, count)

    // O corpo leva o idProduto da PRÓPRIA fechadura; o do hub vai só dentro do ns composto.
    private val Device.gdiProductId: String get() = requireNotNull(productId) { "Fechadura sem idProduto" }

    companion object {
        const val DEFAULT_HISTORY = 10
    }
}
```

Por quê: `runCatching` também captura `CancellationException`; aqui não causa dano porque o `coroutineScope` cancelado relança no `await`. Deixe isso num comentário curto acima do `async`.

#### `GdiErrorMapper.kt`

```kotlin
/** Traduz falhas técnicas (Ktor, GDI) em [AppError] que a UI sabe explicar (RF04). A ordem dos ramos importa. */
object GdiErrorMapper : ErrorMapper {
    /** Texto real (05/10/2026) quando o token já não pode ser renovado (HTTP 400). */
    private const val RENEW_FAILED_FRAGMENT = "renovar o token"
    /** Fato: token expirado = HTTP 403 {"status":"erro","msg":"Token expirado, por favor gere um novo token"}. */
    private const val EXPIRED_FRAGMENT = "expirado"

    override fun toAppError(error: Throwable): AppError =
        when (error) {
            is GdiNoTokenException -> AppError.TokenMissing // subclasse de GdiException: vem antes
            is GdiException -> mapGdi(error)
            // Timeout antes de IOException: no JVM SocketTimeoutException também é IOException.
            is HttpRequestTimeoutException, is SocketTimeoutException -> AppError.Timeout(error.message)
            is ConnectTimeoutException, is IOException -> AppError.Network(error.message)
            else ->
                // UnresolvedAddressException (DNS) não é IOException e não existe em todos os alvos: reconhecida pelo nome.
                if (error::class.simpleName == "UnresolvedAddressException") AppError.Network(error.message)
                else AppError.Unexpected(error.message ?: error::class.simpleName)
        }

    /** Erro da GDI cita só o status ("a API respondeu HTTP 500"); o resto usa a mensagem amigável. */
    override fun toShortMessage(error: Throwable): String =
        if (error is GdiException && error !is GdiNoTokenException) "a API respondeu HTTP ${error.httpStatus}"
        else toAppError(error).userMessage

    private fun mapGdi(error: GdiException): AppError {
        val detail = "HTTP ${error.httpStatus}: ${error.message}"
        return when {
            error.httpStatus == 403 || error.message?.contains(EXPIRED_FRAGMENT, ignoreCase = true) == true -> AppError.TokenExpired(detail)
            error.httpStatus == 401 -> AppError.TokenInvalid(detail)
            error.httpStatus == 400 && error.message?.contains(RENEW_FAILED_FRAGMENT, ignoreCase = true) == true -> AppError.TokenInvalid(detail)
            // 2xx com envelope {"status":"erro"} também é a plataforma respondendo com erro.
            error.httpStatus >= 500 || error.httpStatus in 200..299 -> AppError.Server(error.httpStatus, detail)
            else -> AppError.Unexpected(detail)
        }
    }
}

// Atalhos para a própria camada data (DeviceRepositoryImpl, GdiLockController). A apresentação usa a interface
// ErrorMapper do domain, injetada no ViewModel, e nunca importa estas funções.
fun Throwable.toAppError(): AppError = GdiErrorMapper.toAppError(this)
fun Throwable.toShortMessage(): String = GdiErrorMapper.toShortMessage(this)
```

Import extra: `br.com.pompeo.casa.domain.ErrorMapper`.

Imports dos tipos de rede: `io.ktor.client.network.sockets.ConnectTimeoutException`, `io.ktor.client.network.sockets.SocketTimeoutException`, `io.ktor.client.plugins.HttpRequestTimeoutException`, `kotlinx.io.IOException` (tipo multiplataforma; no JVM é typealias de `java.io.IOException`).

#### Testes do M3

Primeiro o `commonTest/kotlin/br/com/pompeo/casa/TestSupport.kt` (mesmo pacote de `commonMain` para enxergar `internal`):

```kotlin
/** Substitui Keystore/Keychain nos testes; conta as escritas para provar "persiste só em sucesso". */
class InMemoryTokenStorage(initial: String? = null) : TokenStorage {
    var value: String? = initial
    val writes = mutableListOf<String?>()
    override fun read(): String? = value
    override fun write(token: String?) { writes += token; value = token }
}

/** Keystore/Keychain quebrado: lê nada (opcionalmente lança) e lança em toda gravação. */
class ThrowingTokenStorage(private val initial: String? = null, private val readFails: Boolean = false) : TokenStorage {
    var writeAttempts = 0
    override fun read(): String? = if (readFails) throw IllegalStateException("Keystore indisponível") else initial
    override fun write(token: String?) { writeAttempts++; throw IllegalStateException("Keystore indisponível") }
}

fun device(ns: String, kind: DeviceKind = DeviceKind.CAMERA, origin: DeviceOrigin = DeviceOrigin.UNKNOWN, providerId: String = "fake"): Device =
    Device(providerId = providerId, ns = ns, name = "Dispositivo $ns", kind = kind, model = null, productId = "P$ns", online = true, origin = origin)

/**
 * Parceiro falso: pagina uma lista fixa como a GDI (sem total). [failWhen] injeta erro por página/origem;
 * [delayFor] simula lentidão em tempo VIRTUAL do runTest; [queries]/[liveChannels] registram os pedidos.
 */
class FakeProvider(
    private val all: List<Device>,
    private val failWhen: (DeviceQuery) -> Throwable? = { null },
    override val id: String = "fake",
    private val delayFor: (DeviceQuery) -> Long = { 0 },
    override val locks: LockController? = null,
) : DeviceProvider {
    override val displayName: String = "Parceiro de teste"
    val queries = mutableListOf<DeviceQuery>()
    val liveChannels = mutableListOf<Int>()

    override suspend fun listDevices(query: DeviceQuery): DevicePage {
        queries += query
        delayFor(query).takeIf { it > 0 }?.let { delay(it) }
        failWhen(query)?.let { throw it }
        val from = (query.page - 1) * query.pageSize
        return DevicePage(all.drop(from).take(query.pageSize), query.page, query.pageSize)
    }
    override suspend fun startLive(camera: Device, channel: Int): StreamSession {
        liveChannels += channel
        return StreamSession(null, "rtsp://fake/${camera.ns}", null, null)
    }
    override suspend fun stopLive(session: StreamSession) = Unit
    override suspend fun firmware(device: Device): Firmware = Firmware("1.0", false)
    override suspend fun isOnline(device: Device): Boolean? = device.online
}

/** Fechadura falsa: volume em memória; readError/writeError simulam o HTTP 500 real da leitura. */
class FakeLockController : LockController {
    var volume: LockVolume? = LockVolume.MEDIUM
    var readError: Throwable? = null
    var writeError: Throwable? = null
    val historyRequests = mutableListOf<Int>()
    override suspend fun details(lock: Device): LockDetails =
        LockDetails(open = false, remoteEnabled = true, battery = 50, volume = volume, volumeError = null, history = emptyList())
    override suspend fun setOpen(lock: Device, open: Boolean) = Unit
    override suspend fun volume(lock: Device): LockVolume? { readError?.let { throw it }; return volume }
    override suspend fun setVolume(lock: Device, volume: LockVolume) { writeError?.let { throw it }; this.volume = volume }
    override suspend fun history(lock: Device, count: Int): List<LockEvent> {
        historyRequests += count
        return List(count) { LockEvent(time = "02/10/2026 16:29:${(it % 60).toString().padStart(2, '0')}", description = "Remoto (APP)") }
    }
}
```

Fábrica de cliente falso no `GdiApiTest`:

```kotlin
/** Cliente GDI testado com MockEngine: sem rede, roda na JVM e no simulador iOS. Tokens SEMPRE falsos. */
class GdiApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun tokens(token: String?) = TokenRepositoryImpl(InMemoryTokenStorage(token))

    private fun api(tokens: TokenRepository, handler: suspend (HttpRequestData, Int) -> Pair<HttpStatusCode, String>): GdiApi {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            val (status, body) = handler(request, calls)
            respond(body, status, jsonHeaders)
        }
        // ContentNegotiation serializa o JsonObject do setBody em TextContent (por isso o cast abaixo).
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return GdiApi(tokens, baseUrl = "https://gdi.test", client = client)
    }

    private val HttpRequestData.bodyText: String get() = (body as io.ktor.http.content.TextContent).text
    // Asserções de corpo por substring (não dependem da ordem das chaves):
    // assertTrue(""""pagina":2""" in seenBody && """"origem":"todos"""" in seenBody, seenBody)
}
```

Teste da corrida de renovação (escreva assim — prova que chamadas paralelas com token vencido renovam uma única vez):

```kotlin
@Test
fun parallelExpiredCallsRenewTheTokenOnlyOnce() = runTest {
    val tokens = tokens("Ot_old_token_0001")
    // Contadores atômicos: o MockEngine pode atender em threads do pool de IO; commonTest não tem AtomicInteger.
    val renewCalls = MutableStateFlow(0)
    val oldTokenHits = MutableStateFlow(0)
    val bothSent = CompletableDeferred<Unit>()
    val engine = MockEngine { req ->
        when {
            req.url.encodedPath.endsWith("renovar-token/v1") -> {
                renewCalls.update { it + 1 }
                respond("""{"status":"sucesso","data":{"token":"Ot_new_token_0001"}}""", HttpStatusCode.OK, jsonHeaders)
            }
            req.headers[HttpHeaders.Authorization] == "Bearer Ot_old_token_0001" -> {
                // Barreira: as duas chamadas saem com o token antigo antes de qualquer uma receber o 401.
                if (oldTokenHits.updateAndGet { it + 1 } == 2) bothSent.complete(Unit)
                bothSent.await()
                respond("""{"status":"erro","msg":"Não autorizado"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }
            else -> respond("""{"status":"sucesso","data":{"online":true}}""", HttpStatusCode.OK, jsonHeaders)
        }
    }
    val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    val api = GdiApi(tokens, baseUrl = "https://gdi.test", client = client)
    val results = listOf(async { api.isOnline("NS1") }, async { api.isOnline("NS2") }).awaitAll()
    assertEquals(listOf<Boolean?>(true, true), results, "as duas chamadas repetidas com o token novo")
    assertEquals(2, oldTokenHits.value)
    assertEquals(1, renewCalls.value, "exatamente uma renovação para dois 401 paralelos")
    assertEquals("Ot_new_token_0001", tokens.token.value)
}
```

Lista dos testes deste marco (nomes na seção 8): `GdiApiTest` (18), `GdiDeviceParserTest` (5), `GdiRealResponseTest` (2), `GdiErrorMapperTest` (5), `GdiFormatTest` (2).

Fixture "real" para `GdiRealResponseTest` (forma real, ns fictícios):

```json
{"status":"sucesso","data":[
 {"ns":"CAM000000000001","modelo":"iM4 Dual","nome":"iM4 Dual-0001","status":"online","versao":"2.8.0","subdispositivo":false,"idProduto":"PRODCAM1","ultimaVezOnline":"20261005T120000Z","origem":"vinculado","atualizacaoDisponivel":false},
 {"ns":"LOCK000000000002","modelo":"MFR 2030","nome":"MFR 2030-0002","status":"online","versao":"1.0.0","subdispositivo":true,"idProduto":"PRODLCK1","ultimaVezOnline":"20261002T192905Z","origem":"vinculado","dispositivoPai":"HUB0000000003","idProdutoDispositivoPai":"PRODHUB1","atualizacaoDisponivel":false},
 {"ns":"HUB0000000003","modelo":"IOT-ZG2-IB","nome":"MCA 1002-0003","status":"online","versao":"2.4.628243","subdispositivo":false,"idProduto":"PRODHUB1","origem":"vinculado","atualizacaoDisponivel":false},
 {"ns":"CAM000000000004","modelo":"iM5","nome":"Garagem","status":"offline","subdispositivo":false,"idProduto":"PRODCAM4","origem":"compartilhado"}
]}
```

**Pronto quando.** Todos os testes do marco verdes, inclusive os de `text/plain`, corpo-string 401, 403 expirado, renovação 400, envelope de erro com 200, comandos sem `data`, volume 500 → `volumeError`, histórico com 30, `canalVideo`, resposta só-RTSP, renovação paralela única.

**Verificar com.** `./gradlew :shared:testAndroidHostTest` e `./gradlew :shared:iosSimulatorArm64Test` (o mesmo `GdiApi` com engine Darwin compila; os testes usam Mock).

---

### M4 — Persistência segura do token (Keystore / Keychain) + `TokenRepositoryImpl`

**Objetivo.** Token em memória durante a validação; cifrado no aparelho depois dela; falha do armazenamento seguro nunca derruba o app.

**Arquivos.** `data/TokenRepositoryImpl.kt` (implementa a interface `domain/repository/TokenRepository` da seção 4.2); `androidMain/.../data/security/KeystoreTokenStorage.kt`; `iosMain/.../data/security/KeychainTokenStorage.kt`; `di/PlatformModule.android.kt` e `.ios.kt` (seção 4.3); `appModule` com `single<TokenRepository> { TokenRepositoryImpl(...) }` e `GdiApi`.

`data/TokenRepositoryImpl.kt`:

```kotlin
package br.com.pompeo.casa.data

/**
 * Token da conta Intelbras: em memória durante a validação, no armazenamento seguro depois dela.
 * [suggested] é só o pré-preenchimento vindo de local.properties (desenvolvimento), nunca login.
 */
class TokenRepositoryImpl(private val storage: TokenStorage, suggested: String? = null) : TokenRepository {
    override val suggested: String? = TokenFormat.normalize(suggested).takeIf { it.isNotEmpty() }

    // Lido uma vez na criação: a leitura do Keystore/Keychain é síncrona e barata. Falha na leitura = sem token.
    private val mutable = MutableStateFlow(TokenFormat.normalize(runCatching { storage.read() }.getOrNull()).takeIf { it.isNotEmpty() })
    override val token: StateFlow<String?> = mutable.asStateFlow()

    @Volatile // kotlin.concurrent.Volatile: a anotação multiplataforma (kotlin.jvm.Volatile não compila no iOS)
    override var persisted: Boolean = mutable.value != null
        private set

    override fun setSession(token: String) {
        mutable.value = TokenFormat.normalize(token).takeIf { it.isNotEmpty() }
        persisted = false
    }

    /**
     * Devolve false se a gravação falhou: o Keystore/Keychain pode lançar e isso não pode derrubar o app
     * depois de a GDI já ter aceitado o token — ele vale só nesta sessão.
     */
    override fun persist(token: String): Boolean {
        setSession(token)
        persisted = mutable.value != null && runCatching { storage.write(mutable.value) }.isSuccess
        return persisted
    }

    override fun clear() {
        mutable.value = null
        persisted = false
        // Falha ao apagar não pode impedir o logout: o token já saiu da memória.
        runCatching { storage.write(null) }
    }

    override fun masked(): String? = mutable.value?.let(TokenFormat::mask)
}
```

Imports: `br.com.pompeo.casa.domain.TokenFormat`, `br.com.pompeo.casa.domain.TokenStorage`, `br.com.pompeo.casa.domain.repository.TokenRepository`, `kotlin.concurrent.Volatile`, `kotlinx.coroutines.flow.{MutableStateFlow, StateFlow, asStateFlow}`.

`androidMain/.../data/security/KeystoreTokenStorage.kt`:

```kotlin
package br.com.pompeo.casa.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.pompeo.casa.domain.TokenStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token cifrado com AES-256/GCM por uma chave que nunca sai do Android Keystore; o par
 * `iv:ciphertext` (Base64) fica em SharedPreferences privadas. Não usamos a security-crypto
 * (EncryptedSharedPreferences) porque a Google a descontinuou em 2024; o Keystore direto é o
 * caminho recomendado e não traz dependência extra.
 */
class KeystoreTokenStorage(context: Context) : TokenStorage {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): String? =
        runCatching {
            val stored = prefs.getString(KEY, null) ?: return null // retorno não local: runCatching é inline
            val (iv, cipherText) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrElse {
            // Chave perdida (restauração, reset) ou dado corrompido: apaga e trata como "sem token".
            prefs.edit().remove(KEY).apply()
            null
        }

    override fun write(token: String?) {
        if (token == null) {
            prefs.edit().remove(KEY).apply()
            return
        }
        // Sem IV aqui: o Keystore exige IV aleatório (randomizedEncryptionRequired) e gera um; lemos de cipher.iv.
        // Falha do Keystore sobe para o TokenRepositoryImpl, que mantém a sessão só em memória (persisted = false).
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY, encode(cipher.iv) + ":" + encode(cipherText)).apply() // Base64 não contém ':'
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } // load(null) é obrigatório
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Sem setUserAuthenticationRequired: exigiria biometria a cada leitura e invalidaria a chave ao trocar o bloqueio.
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "casa.token"
        const val PREFS = "casa.secure"
        const val KEY = "gdi-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
```

`iosMain/.../data/security/KeychainTokenStorage.kt`:

```kotlin
package br.com.pompeo.casa.data.security

import br.com.pompeo.casa.domain.TokenStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Token no Keychain do iOS (item genérico, service `br.com.pompeo.casa`, account `gdi-token`),
 * acessível depois do primeiro desbloqueio. Bindings platform.* já vêm no Kotlin/Native: sem .def de cinterop.
 * Defensivo: qualquer status diferente de errSecSuccess na leitura vira "sem token".
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainTokenStorage : TokenStorage {
    override fun read(): String? = memScoped {
        val service = CFBridgingRetain(SERVICE) // +1: o dicionário sem callbacks não segura os valores
        val account = CFBridgingRetain(ACCOUNT)
        val query = CFDictionaryCreateMutable(null, 5, null, null)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            // O item copiado chega com +1: CFBridgingRelease transfere a posse para o Kotlin/Native.
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            // toString() e não `as String`: NSString e String são tipos distintos para o compilador K/N.
            NSString.create(data, NSUTF8StringEncoding)?.toString()
        } finally {
            release(query, service, account)
        }
    }

    override fun write(token: String?) {
        delete() // delete + add: mais simples que SecItemUpdate e evita errSecDuplicateItem
        if (token == null) return
        val data = NSString.create(string = token).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw IllegalStateException("Token não codificável em UTF-8")
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val value = CFBridgingRetain(data)
        val attributes = CFDictionaryCreateMutable(null, 5, null, null)
        try {
            CFDictionaryAddValue(attributes, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(attributes, kSecAttrService, service)
            CFDictionaryAddValue(attributes, kSecAttrAccount, account)
            CFDictionaryAddValue(attributes, kSecValueData, value)
            CFDictionaryAddValue(attributes, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            // Falha é relatada ao TokenRepositoryImpl (sessão só em memória); o OSStatus vai na mensagem, nunca o token.
            val status = SecItemAdd(attributes, null)
            if (status != errSecSuccess) throw IllegalStateException("Keychain recusou a gravação (OSStatus $status)")
        } finally {
            release(attributes, service, account, value)
        }
    }

    private fun delete() {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val query = CFDictionaryCreateMutable(null, 3, null, null)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            SecItemDelete(query)
        } finally {
            release(query, service, account)
        }
    }

    /** Libera só o que criamos/retivemos; as constantes kSec… e kCFBooleanTrue são globais e não se liberam. */
    private fun release(dictionary: CFDictionaryRef?, vararg values: CFTypeRef?) {
        CFRelease(dictionary)
        values.forEach { CFRelease(it) }
    }

    private companion object {
        const val SERVICE = "br.com.pompeo.casa"
        const val ACCOUNT = "gdi-token"
    }
}
```

Cuidado: `CFRelease(NULL)` derruba o processo; aqui nunca acontece porque `CFDictionaryCreateMutable` e `CFBridgingRetain` de valores não nulos não devolvem nulo. Se adaptar o código, mantenha essa garantia.

**Testes.** `TokenRepositoryImplTest` (7; testa a implementação: `TokenRepositoryImpl(InMemoryTokenStorage(...))` ou `ThrowingTokenStorage`): `loadsPersistedTokenAndIgnoresBlankSuggestion`, `suggestionIsNotALogin`, `sessionStaysInMemoryUntilPersist`, `persistedTellsWhetherTheSessionTokenIsStored`, `storageFailureKeepsTheTokenInMemoryOnly`, `unreadableStorageStartsWithoutToken`, `inMemoryStorageBehavesLikeRealOne`. **Não** teste o Keychain em `commonTest`/`iosSimulatorArm64Test`: o executável de teste não tem entitlement de Keychain e `SecItemAdd` devolve `errSecMissingEntitlement` (-34018). Keystore e Keychain são validados no aparelho (M10).

**Pronto quando.** Testes verdes; no aparelho Android, depois do M6, o token sobrevive a fechar e reabrir o app; `adb shell run-as br.com.pompeo.casa cat shared_prefs/casa.secure.xml` mostra só `iv:ciphertext` em Base64 (nunca o token em claro).

**Verificar com.** `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosArm64`.

---

### M5 — `DeviceRepositoryImpl` + casos de uso: paginação, filtro, geração, cancelamento, validação do token

**Objetivo.** Uma única fonte de verdade da listagem, segura contra corridas, atrás da interface `DeviceRepository` (seção 4.2); as duas regras de negócio que juntam chamadas viram casos de uso (seção 4.1).

**Arquivos.** `data/DeviceRepositoryImpl.kt`, `domain/usecase/SubmitTokenUseCase.kt`, `domain/usecase/ChangeLockVolumeUseCase.kt`; no `commonTest/TestSupport.kt`, os fakes `FakeTokenRepository` e `FakeDeviceRepository`; `appModule` com `single<DeviceRepository> { DeviceRepositoryImpl(...) }` e os dois `factoryOf`.

`data/DeviceRepositoryImpl.kt` (escreva assim):

```kotlin
package br.com.pompeo.casa.data

/**
 * Lista paginada e filtrada dos dispositivos de todos os parceiros registrados na DI. Três mecanismos de
 * concorrência, cada um para um problema: Mutex (escritas serializadas), geração (resposta velha não publica),
 * Jobs separados (trocar filtro cancela em vez de esperar).
 */
class DeviceRepositoryImpl(
    private val tokens: TokenRepository,
    private val providers: List<DeviceProvider>,
    private val scope: CoroutineScope,
) : DeviceRepository {
    private val mutableQuery = MutableStateFlow(DeviceQuery())
    override val query: StateFlow<DeviceQuery> = mutableQuery.asStateFlow()

    private val mutableState = MutableStateFlow<DevicesState>(if (tokens.token.value == null) DevicesState.NoToken else DevicesState.Loading)
    override val state: StateFlow<DevicesState> = mutableState.asStateFlow()

    private val mutableDevices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = mutableDevices.asStateFlow()

    // Carregamentos serializados: refresh e "carregar mais" nunca escrevem o estado ao mesmo tempo.
    private val loading = Mutex()

    // Cada refresh/filtro/tamanho abre uma geração; resposta de geração antiga nunca é publicada.
    // MutableStateFlow faz papel de contador atômico (update/updateAndGet são CAS): commonMain não tem AtomicInteger.
    private val generation = MutableStateFlow(0)

    // Trocar filtro/tamanho cancela em vez de esperar o lock (uma chamada lenta levaria até 20 s).
    // Referências SEPARADAS: um "carregar mais" disparado logo depois de um refresh não pode sobrescrever a do
    // refresh — ele ficaria órfão, impossível de cancelar, segurando o lock durante toda a chamada lenta.
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        // Token restaurado do armazenamento seguro: carrega sem o usuário tocar em nada.
        if (tokens.token.value != null) refresh()
    }

    override val providerNames: List<String> get() = providers.map { it.displayName }
    override fun providerName(device: Device): String? = providers.firstOrNull { it.id == device.providerId }?.displayName

    override fun logout() {
        cancelLoads()                 // consulta em voo não pode republicar a lista depois de a conta sair
        generation.update { it + 1 }
        tokens.clear()
        publish(DevicesState.NoToken)
    }

    override suspend fun loadFirstPage(): DevicesState {
        // Geração marcada ANTES de esperar o lock: a consulta anterior, se ainda estiver rodando, já fica velha.
        val gen = generation.updateAndGet { it + 1 }
        return loading.withLock {
            if (tokens.token.value == null) return@withLock publish(DevicesState.NoToken)
            val query = mutableQuery.updateAndGet { it.copy(page = 1) }
            val current = mutableState.value
            // Com conteúdo na tela, só a barra fina (refreshing) para a lista não piscar; e um "carregar mais"
            // cancelado por este refresh não pode deixar loadingMore preso em true.
            mutableState.value =
                if (current is DevicesState.Content) current.copy(refreshing = true, loadingMore = false, loadMoreError = null)
                else DevicesState.Loading
            val next =
                try {
                    val fetched = fetchPage(query)
                    if (fetched.devices.isEmpty()) DevicesState.Empty(query)
                    else DevicesState.Content(fetched.devices, query, hasMore = fetched.hasMore, pagesLoaded = 1, note = fetched.note)
                } catch (e: CancellationException) {
                    throw e // nunca transformar cancelamento em erro de tela
                } catch (e: Throwable) {
                    DevicesState.Error(e.toAppError(), query)
                }
            if (gen == generation.value) publish(next) else next
        }
    }

    override fun refresh() {
        // Um refresh substitui qualquer consulta em voo, inclusive um "carregar mais".
        cancelLoads()
        refreshJob = scope.launch { loadFirstPage() }
    }

    private fun cancelLoads() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
    }

    override fun loadMore() {
        val current = mutableState.value as? DevicesState.Content ?: return
        // refreshJob ativo cobre a janela entre o launch do refresh e ele marcar refreshing = true no estado.
        if (!current.hasMore || current.loadingMore || current.refreshing || refreshJob?.isActive == true) return
        val gen = generation.value
        // Marcado FORA do lock e de forma síncrona: um segundo toque não pede a mesma página duas vezes.
        mutableState.value = current.copy(loadingMore = true, loadMoreError = null)
        loadMoreJob = scope.launch { loadNextPage(gen) }
    }

    internal suspend fun loadNextPage(gen: Int = generation.value): DevicesState = loading.withLock {
        val current = mutableState.value as? DevicesState.Content ?: return@withLock mutableState.value
        // Filtro/tamanho trocado enquanto esperava o lock: a página pedida pertence à consulta antiga.
        if (gen != generation.value) return@withLock current
        val query = current.query.copy(page = current.pagesLoaded + 1)
        val next =
            try {
                val fetched = fetchPage(query)
                current.copy(
                    devices = (current.devices + fetched.devices).distinctBy { it.providerId + it.ns },
                    hasMore = fetched.hasMore,
                    pagesLoaded = current.pagesLoaded + 1,
                    loadingMore = false,
                    loadMoreError = null,
                    note = current.note ?: fetched.note,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                current.copy(loadingMore = false, loadMoreError = e.toAppError())
            }
        if (gen == generation.value) publish(next) else current
    }

    override fun setOrigin(filter: OriginFilter) {
        mutableQuery.update { it.copy(origin = filter, page = 1) }
        refresh()
    }

    override fun setPageSize(size: Int) {
        require(size > 0) { "Itens por página deve ser positivo" }
        mutableQuery.update { it.copy(pageSize = size, page = 1) }
        refresh()
    }

    private class Fetched(val devices: List<Device>, val hasMore: Boolean, val note: String?)

    /** Chama cada parceiro com a mesma página; junta e aplica o filtro defensivo de origem. */
    private suspend fun fetchPage(query: DeviceQuery): Fetched {
        var note: String? = null
        val pages = providers.map { provider ->
            try {
                provider.listDevices(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Defesa: se a API recusar o valor de "origem", repete com "todos", filtra no aparelho e avisa.
                // Se o fallback também falhar, prevalece o erro ORIGINAL.
                if (query.origin == OriginFilter.ALL) throw e
                val fallback = try {
                    provider.listDevices(query.copy(origin = OriginFilter.ALL))
                } catch (ignored: CancellationException) { throw ignored } catch (ignored: Throwable) { throw e }
                note = FILTER_ON_DEVICE_NOTE
                fallback
            }
        }
        // Se a API já filtrou, nada muda; origem desconhecida nunca é descartada.
        val devices = pages.flatMap { it.devices }.filter { query.origin.accepts(it.origin) }
        // hasMore vem das páginas CRUAS: filtrar no aparelho não encerra a paginação.
        return Fetched(devices, hasMore = pages.any { it.hasMore }, note = note)
    }

    private fun publish(state: DevicesState): DevicesState {
        mutableState.value = state
        when (state) {
            is DevicesState.Content -> mutableDevices.value = state.devices
            is DevicesState.Empty, DevicesState.NoToken -> mutableDevices.value = emptyList()
            // Erro de refresh mantém a última lista: a tela de detalhe aberta não fecha sozinha.
            is DevicesState.Error, DevicesState.Loading -> Unit
        }
        return state
    }

    // ---------- Operações por dispositivo: delegam ao parceiro dono dele ----------

    private fun providerOf(device: Device): DeviceProvider =
        providers.firstOrNull { it.id == device.providerId } ?: throw IllegalStateException("Parceiro ${device.providerId} não registrado")

    private fun locksOf(lock: Device): LockController =
        providerOf(lock).locks ?: throw IllegalStateException("${providerOf(lock).displayName} não controla fechaduras")

    // Override não repete o valor padrão (channel = 0): ele vem da interface.
    override suspend fun startLive(camera: Device, channel: Int): StreamSession = providerOf(camera).startLive(camera, channel)

    /** A sessão não carrega o providerId: pede o encerramento a todos, cada um isolado. */
    override suspend fun stopLive(session: StreamSession) = providers.forEach { resultOf { it.stopLive(session) } }

    override suspend fun lockDetails(lock: Device): LockDetails = locksOf(lock).details(lock)
    override suspend fun setLock(lock: Device, open: Boolean) = locksOf(lock).setOpen(lock, open)
    override suspend fun setLockVolume(lock: Device, volume: LockVolume) = locksOf(lock).setVolume(lock, volume)
    override suspend fun lockVolume(lock: Device): LockVolume? = locksOf(lock).volume(lock)
    override suspend fun lockHistory(lock: Device, quantity: Int): List<LockEvent> = locksOf(lock).history(lock, quantity)
    override suspend fun firmware(device: Device): Firmware = providerOf(device).firmware(device)
    override suspend fun isOnline(device: Device): Boolean? = providerOf(device).isOnline(device)

    companion object {
        const val FILTER_ON_DEVICE_NOTE = "Filtro aplicado no aparelho"
    }
}
```

Imports que costumam faltar: `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.{CoroutineScope, Job, launch}`, `kotlinx.coroutines.flow.{MutableStateFlow, StateFlow, asStateFlow, update, updateAndGet}`, `kotlinx.coroutines.sync.{Mutex, withLock}`, `br.com.pompeo.casa.data.gdi.toAppError`, `br.com.pompeo.casa.domain.resultOf`, `br.com.pompeo.casa.domain.repository.{DeviceRepository, TokenRepository}`. (Aqui `data` importa um utilitário de `data/gdi`; se um segundo parceiro chegar, mova o mapeamento genérico de rede para `data/` e deixe em `data/gdi` só o que é da GDI.)

`domain/usecase/SubmitTokenUseCase.kt` (RF01; a regra que junta `TokenRepository` e `DeviceRepository`):

```kotlin
package br.com.pompeo.casa.domain.usecase

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.TokenFormat
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Valida o token carregando a primeira página e só então o persiste (inclusive com a conta vazia).
 * Em falha ou cancelamento o token sai da memória e o estado volta a NoToken.
 */
class SubmitTokenUseCase(private val tokens: TokenRepository, private val devices: DeviceRepository) {
    /** null = token aceito; senão, o erro que a tela de token mostra. */
    suspend operator fun invoke(raw: String): AppError? {
        val token = TokenFormat.normalize(raw)
        if (!TokenFormat.isPlausible(token)) return AppError.TokenMissing // nem chama a API
        tokens.setSession(token)
        return when (val firstPage = loadFirstPageOrLogout()) {
            is DevicesState.Error -> reject(firstPage.error)
            DevicesState.NoToken -> reject(AppError.TokenMissing)
            else -> {
                // Persiste o valor ATUAL: a GDI pode ter renovado o token durante a validação.
                tokens.persist(tokens.token.value ?: token)
                null
            }
        }
    }

    private suspend fun loadFirstPageOrLogout(): DevicesState =
        try {
            devices.loadFirstPage()
        } catch (e: CancellationException) {
            // Validação interrompida (ViewModel encerrado): token não validado não pode ficar na sessão.
            devices.logout()
            throw e
        }

    private fun reject(error: AppError): AppError {
        devices.logout()
        return error
    }
}
```

`domain/usecase/ChangeLockVolumeUseCase.kt` (RF06):

```kotlin
package br.com.pompeo.casa.domain.usecase

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.resultOf

/** Grava o volume da fechadura e devolve o volume a exibir. */
class ChangeLockVolumeUseCase(private val devices: DeviceRepository) {
    /**
     * Relê porque a GDI não devolve o valor novo. Releitura que falha (HTTP 500 real) ou vem vazia assume o
     * pedido: a escrita foi aceita. Erro na ESCRITA sobe para o chamador, que mostra a falha.
     */
    suspend operator fun invoke(lock: Device, volume: LockVolume): LockVolume {
        devices.setLockVolume(lock, volume)
        return resultOf { devices.lockVolume(lock) }.getOrNull() ?: volume
    }
}
```

Fakes das interfaces do domínio para os testes de caso de uso e de ViewModel (acrescente ao `TestSupport.kt`; o `Impl` real tem teste próprio):

```kotlin
/** TokenRepository em memória. [canPersist] = false simula Keystore/Keychain quebrado. */
class FakeTokenRepository(initial: String? = null, private val canPersist: Boolean = true) : TokenRepository {
    private val mutableToken = MutableStateFlow(initial)
    override val token: StateFlow<String?> = mutableToken.asStateFlow()
    override val suggested: String? = null
    override var persisted: Boolean = initial != null
        private set
    /** Valores que chegaram ao "armazenamento seguro", na ordem. */
    val persistedValues = mutableListOf<String>()

    override fun setSession(token: String) {
        mutableToken.value = token
        persisted = false
    }

    override fun persist(token: String): Boolean {
        setSession(token)
        if (canPersist) persistedValues += token
        persisted = canPersist
        return persisted
    }

    override fun clear() {
        mutableToken.value = null
        persisted = false
    }

    override fun masked(): String? = mutableToken.value?.let(TokenFormat::mask)
}

/**
 * DeviceRepository de mentira: [firstPage] decide o resultado da validação e roda depois de [firstPageDelayMs]
 * (tempo VIRTUAL do runTest); fechadura delegada a [locks]; [startLiveDelayMs] permite cancelar o vídeo no meio.
 */
class FakeDeviceRepository(
    private val tokens: TokenRepository,
    private val firstPageDelayMs: Long = 0,
    private val locks: FakeLockController = FakeLockController(),
    private val firstPage: () -> DevicesState = { DevicesState.Empty(DeviceQuery()) },
) : DeviceRepository {
    private val mutableState = MutableStateFlow<DevicesState>(DevicesState.NoToken)
    override val state: StateFlow<DevicesState> = mutableState.asStateFlow()
    override val query: StateFlow<DeviceQuery> = MutableStateFlow(DeviceQuery())
    override val devices: StateFlow<List<Device>> = MutableStateFlow(emptyList())
    override val providerNames: List<String> = listOf(PROVIDER_NAME)
    var firstPageLoads = 0
        private set
    var logouts = 0
        private set
    var startLiveDelayMs = 0L

    override fun providerName(device: Device): String = PROVIDER_NAME

    override suspend fun loadFirstPage(): DevicesState {
        firstPageLoads++
        delay(firstPageDelayMs)
        return firstPage().also { mutableState.value = it }
    }

    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun setOrigin(filter: OriginFilter) = Unit
    override fun setPageSize(size: Int) = Unit

    override fun logout() {
        logouts++
        tokens.clear()
        mutableState.value = DevicesState.NoToken
    }

    override suspend fun startLive(camera: Device, channel: Int): StreamSession {
        delay(startLiveDelayMs)
        return StreamSession(null, "rtsp://fake/${camera.ns}", null, null)
    }

    override suspend fun stopLive(session: StreamSession) = Unit
    override suspend fun lockDetails(lock: Device): LockDetails = locks.details(lock)
    override suspend fun setLock(lock: Device, open: Boolean) = locks.setOpen(lock, open)
    override suspend fun setLockVolume(lock: Device, volume: LockVolume) = locks.setVolume(lock, volume)
    override suspend fun lockVolume(lock: Device): LockVolume? = locks.volume(lock)
    override suspend fun lockHistory(lock: Device, quantity: Int): List<LockEvent> = locks.history(lock, quantity)
    override suspend fun firmware(device: Device): Firmware = Firmware("1.0", false)
    override suspend fun isOnline(device: Device): Boolean? = device.online

    private companion object {
        const val PROVIDER_NAME = "Parceiro de teste"
    }
}
```

**Testes.** `DeviceRepositoryImplTest` (17), `SubmitTokenUseCaseTest` (8) e `ChangeLockVolumeUseCaseTest` (3), lista na seção 8. O teste da implementação instancia o `Impl` (`DeviceRepositoryImpl` + `TokenRepositoryImpl(InMemoryTokenStorage())` + `FakeProvider`); o teste de caso de uso usa só os fakes das interfaces (`FakeTokenRepository`, `FakeDeviceRepository`). Padrão obrigatório do `Impl`:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryImplTest {
    private val seven = (1..7).map { device("D$it") } // página 1 cheia (5), página 2 curta (2)

    @Test
    fun staleFirstPageIsNotPublishedAfterOriginChanged() = runTest {
        val mixed = listOf(device("L1", origin = DeviceOrigin.LINKED), device("S1", origin = DeviceOrigin.SHARED))
        // "todos" demora 1 s; "compartilhados" 0,5 s (tempo virtual).
        val provider = FakeProvider(mixed, delayFor = { if (it.origin == OriginFilter.ALL) 1_000 else 500 })
        val tokens = TokenRepositoryImpl(InMemoryTokenStorage())
        // backgroundScope: os launches do repositório compartilham o relógio virtual e são cancelados no fim do teste.
        val repo = DeviceRepositoryImpl(tokens, listOf(provider), backgroundScope)
        tokens.setSession("Ot_session_000001")
        val stale = async { repo.loadFirstPage() } // chamada direta, como o SubmitTokenUseCase faz
        runCurrent()
        repo.setOrigin(OriginFilter.SHARED)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(DevicesState.Loading, repo.state.value, "resposta antiga descartada")
        assertTrue(repo.devices.value.isEmpty())
        val staleResult = assertIs<DevicesState.Content>(stale.await())
        assertEquals(OriginFilter.ALL, staleResult.query.origin, "o chamador direto ainda recebe o resultado")
        // advanceUntilIdle para quando só restam coroutines do backgroundScope: avance o relógio explicitamente.
        advanceTimeBy(500)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(OriginFilter.SHARED, content.query.origin)
        assertEquals(listOf("S1"), content.devices.map { it.ns })
    }

    @Test
    fun originChangeCancelsRefreshEvenWhenLoadMoreWasRequestedRightAfter() = runTest {
        var slow = false // ligado só depois da 1.ª página
        val provider = FakeProvider(seven, delayFor = { if (slow) 10_000 else 0 })
        val tokens = TokenRepositoryImpl(InMemoryTokenStorage())
        val repo = DeviceRepositoryImpl(tokens, listOf(provider), backgroundScope)
        tokens.setSession("Ot_session_000001")
        repo.loadFirstPage()
        slow = true
        // Sem rodar o dispatcher entre as chamadas: o refresh ainda não marcou refreshing = true.
        repo.refresh()
        repo.loadMore()
        repo.setOrigin(OriginFilter.SHARED)
        runCurrent()
        assertEquals(OriginFilter.SHARED, provider.queries.last().origin, "consulta nova não espera a antiga")
        assertEquals(listOf(1, 1), provider.queries.map { it.page }, "a página 2 do 'carregar mais' nunca foi pedida")
        advanceTimeBy(10_000)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertFalse(content.loadingMore)
        assertFalse(content.refreshing)
    }
}
```

Casos de uso, com fakes e Arrange/Act/Assert (escreva assim):

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SubmitTokenUseCaseTest {
    @Test
    fun rejectedTokenIsNotPersistedAndLogsOut() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) { DevicesState.Error(AppError.TokenInvalid("HTTP 401"), DeviceQuery()) }
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("  Ot_rejected_0001  ")
        // Assert
        assertIs<AppError.TokenInvalid>(error)
        assertTrue(tokens.persistedValues.isEmpty())
        assertNull(tokens.token.value)
        assertEquals(1, devices.logouts)
    }

    @Test
    fun cancelledValidationLeavesNoUnvalidatedSessionToken() = runTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual).
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val submitToken = SubmitTokenUseCase(tokens, devices)
        val validation = launch { submitToken("Ot_interrupted_0123") }
        runCurrent()
        assertEquals("Ot_interrupted_0123", tokens.token.value)
        // Act
        validation.cancel()
        runCurrent()
        // Assert
        assertNull(tokens.token.value)
        assertEquals(DevicesState.NoToken, devices.state.value)
        assertTrue(tokens.persistedValues.isEmpty())
    }
}

class ChangeLockVolumeUseCaseTest {
    private val lock = device("LOCK1", kind = DeviceKind.LOCK)

    @Test
    fun rereadFailureAssumesTheRequestedVolume() = runTest {
        // Arrange: leitura com o HTTP 500 real.
        val locks = FakeLockController().apply { readError = IllegalStateException("HTTP 500") }
        val changeVolume = ChangeLockVolumeUseCase(FakeDeviceRepository(FakeTokenRepository(), locks = locks))
        // Act
        val shown = changeVolume(lock, LockVolume.LOW)
        // Assert
        assertEquals(LockVolume.LOW, shown)
        assertEquals(LockVolume.LOW, locks.volume)
    }
}
```

Os testes de caso de uso não importam nada de `data`: a falha da API é uma exceção qualquer, porque o caso de uso não a interpreta.

Regras de teste do `DeviceRepositoryImplTest`: escopo do repositório **sempre** `backgroundScope`; **nunca** `advanceUntilIdle()` para trabalho do repositório — use `advanceTimeBy(<ms do FakeProvider>)` + `runCurrent()`; `StandardTestDispatcher` (padrão do `runTest`) não executa `launch` na hora, e é isso que permite testar a janela "depois do launch do refresh, antes de `refreshing = true`".

**Pronto quando.** 28 testes verdes (17 + 8 + 3), incluindo todos os de corrida.

**Verificar com.** `./gradlew :shared:testAndroidHostTest --tests "*DeviceRepositoryImplTest*" --tests "*UseCaseTest*"`.

---

### M6 — ViewModel, navegação e tela de token (RF01)

**Referência visual:** a tela de token não aparece nas capturas do Mibo; abra `design/app-01-token.png` (estado inicial) e `design/app-02-token-expirado.png` (erro de token expirado), que são prints do resultado esperado, e siga as cores da seção 6.1 e o layout da seção 6.3.

**Objetivo.** Fluxo completo token → Home vazia/lista simples; validação sobrevivendo à rotação; o MVVM da seção 4.1 montado (tela → eventos → `HomeViewModel` → `StateFlow` → tela).

**Arquivos.** `ui/Theme.kt` (seção 6.1), `ui/HomeViewModel.kt`, `ui/TokenScreen.kt`, `ui/Components.kt` (Snack, Title, BackButton, por ora), `platform/BuildInfo.kt` (+ actuals), `App.kt`; `appModule` completo (seção 4.3: `single<ErrorMapper> { GdiErrorMapper }` + `viewModelOf(::HomeViewModel)`); no `TestSupport.kt`, `FakeErrorMapper` e `homeViewModel(...)`.

`platform/BuildInfo.kt`:

```kotlin
/** Debug mostra AppError.detail na tela; release nunca. */
expect val isDebugBuild: Boolean
```

- Android: `actual val isDebugBuild: Boolean get() = runCatching { (GlobalContext.get().get<Context>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }.getOrDefault(false)` — biblioteca KMP não tem `BuildConfig`, então lê a flag do app hospedeiro; sem Koin iniciado (Preview da IDE) cai em `false` em vez de lançar. Imports: `android.content.Context`, `android.content.pm.ApplicationInfo`, `org.koin.core.context.GlobalContext`.
- iOS: `@OptIn(ExperimentalNativeApi::class) actual val isDebugBuild: Boolean get() = Platform.isDebugBinary` (imports `kotlin.experimental.ExperimentalNativeApi`, `kotlin.native.Platform`).

`ui/HomeViewModel.kt`:

```kotlin
/** Andamento da validação do token (RF01). Vive no ViewModel para sobreviver à rotação da tela de token. */
sealed interface TokenValidation {
    data object Idle : TokenValidation
    data object Validating : TokenValidation
    data class Failed(val error: AppError) : TokenValidation
    /** GDI aceitou: a tela navega e chama [HomeViewModel.tokenValidationHandled]. */
    data object Done : TokenValidation
}

/**
 * Único ViewModel do app: uma fonte de verdade para todas as telas. Só conhece o domain (interfaces de
 * repositório, casos de uso, ErrorMapper), injetado pelo Koin; nunca uma classe de data.
 */
class HomeViewModel(
    private val repository: DeviceRepository,
    private val tokens: TokenRepository,
    private val submitTokenUseCase: SubmitTokenUseCase,
    private val changeLockVolumeUseCase: ChangeLockVolumeUseCase,
    private val errors: ErrorMapper,
) : ViewModel() {
    val state: StateFlow<DevicesState> = repository.state
    val query: StateFlow<DeviceQuery> = repository.query
    val devices: StateFlow<List<Device>> = repository.devices
    val token: StateFlow<String?> = tokens.token

    /** Token de local.properties (desenvolvimento): só pré-preenche a tela de token. */
    val suggestedToken: String? get() = tokens.suggested
    val providerNames: List<String> get() = repository.providerNames
    fun maskedToken(): String? = tokens.masked()
    fun providerName(device: Device): String? = repository.providerName(device)

    private val mutableValidation = MutableStateFlow<TokenValidation>(TokenValidation.Idle)
    val tokenValidation: StateFlow<TokenValidation> = mutableValidation.asStateFlow()

    /** Aviso único para a Home (Snackbar), consumido por [noticeShown]. */
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()

    /**
     * Valida com a 1.ª página e só então persiste. Não-suspend, no viewModelScope: a rotação recria a tela
     * (e cancelaria um rememberCoroutineScope no meio da chamada), mas não o ViewModel.
     */
    fun submitToken(raw: String) {
        if (mutableValidation.value is TokenValidation.Validating) return // segundo toque ignorado
        mutableValidation.value = TokenValidation.Validating
        viewModelScope.launch {
            // O caso de uso já devolve AppError; o catch é a rede de segurança para falha inesperada (nunca crash).
            val error = try { submitTokenUseCase(raw) } catch (e: CancellationException) { throw e } catch (e: Exception) { errors.toAppError(e) }
            // Aceito mas não gravado (Keystore/Keychain falhou): segue para a Home e avisa lá, em vez de fingir erro.
            if (error == null && !tokens.persisted) mutableNotice.value = TOKEN_NOT_PERSISTED
            mutableValidation.value = error?.let(TokenValidation::Failed) ?: TokenValidation.Done
        }
    }

    /** A tela consumiu o resultado: sem isso, um Done antigo navegaria sozinho na próxima visita. */
    fun tokenValidationHandled() { mutableValidation.value = TokenValidation.Idle }
    fun noticeShown() { mutableNotice.value = null }
    fun logout() { repository.logout(); tokenValidationHandled() }

    /** Texto amigável de uma falha (RF04): a tela nunca importa o mapeador de data/gdi. */
    fun messageOf(error: Throwable): String = errors.toAppError(error).userMessage
    /** Versão curta, para caber entre parênteses ("a API respondeu HTTP 500"). */
    fun shortMessageOf(error: Throwable): String = errors.toShortMessage(error)

    fun refresh() = repository.refresh()
    fun loadMore() = repository.loadMore()
    fun setOrigin(filter: OriginFilter) = repository.setOrigin(filter)
    fun setPageSize(size: Int) = repository.setPageSize(size)

    // resultOf (domain) e não runCatching: cancelamento é relançado; senão a tentativa antiga escreveria
    // "Job was cancelled" como erro por cima da nova (armadilha 28).
    suspend fun startLive(camera: Device, channel: Int = 0): Result<StreamSession> = resultOf { repository.startLive(camera, channel) }

    /** No viewModelScope: no onDispose da tela, o escopo dela já foi cancelado. */
    fun stopLive(session: StreamSession) { viewModelScope.launch { repository.stopLive(session) } }

    suspend fun lockDetails(lock: Device): Result<LockDetails> = resultOf { repository.lockDetails(lock) }
    /** Comando real na fechadura; a UI só chama depois de confirmação do usuário. */
    suspend fun setLock(lock: Device, open: Boolean): Result<Unit> = resultOf { repository.setLock(lock, open) }
    suspend fun setLockVolume(lock: Device, volume: LockVolume): Result<LockVolume> = resultOf { changeLockVolumeUseCase(lock, volume) }
    suspend fun lockVolume(lock: Device): Result<LockVolume?> = resultOf { repository.lockVolume(lock) }
    suspend fun lockHistory(lock: Device, more: Boolean): Result<List<LockEvent>> =
        resultOf { repository.lockHistory(lock, if (more) HISTORY_MORE else HISTORY_FIRST) }
    suspend fun firmware(device: Device): Result<Firmware> = resultOf { repository.firmware(device) }
    suspend fun isOnline(device: Device): Result<Boolean?> = resultOf { repository.isOnline(device) }

    companion object {
        const val HISTORY_FIRST = 10
        const val HISTORY_MORE = 30
        const val TOKEN_NOT_PERSISTED = "Token válido, mas não foi possível guardá-lo neste aparelho."
    }
}
```

Imports do ViewModel: `androidx.lifecycle.{ViewModel, viewModelScope}` (artefato JetBrains, mesmo pacote do Google), `kotlinx.coroutines.{CancellationException, launch}`, `kotlinx.coroutines.flow.{MutableStateFlow, StateFlow, asStateFlow}`, `br.com.pompeo.casa.domain.{AppError, DevicesState, ErrorMapper, resultOf}`, `br.com.pompeo.casa.domain.repository.{DeviceRepository, TokenRepository}`, `br.com.pompeo.casa.domain.usecase.{ChangeLockVolumeUseCase, SubmitTokenUseCase}` e os modelos de `domain.model`. **Nenhum import de `br.com.pompeo.casa.data`** (regra 0.2.12).

`App.kt` — rotas tipadas e destino inicial (imports-chave: `androidx.navigation.compose.{NavHost, composable, rememberNavController}`, `androidx.navigation.toRoute`, `kotlinx.serialization.Serializable`, `org.koin.compose.viewmodel.koinViewModel`):

```kotlin
// Rotas tipadas (Navigation Compose multiplataforma + kotlinx.serialization). Argumentos pequenos: a tela
// busca o objeto no StateFlow do ViewModel pelo ns.
@Serializable object TokenRoute
@Serializable object HomeRoute
@Serializable data class CameraRoute(val ns: String)
@Serializable data class DeviceRoute(val ns: String)

/** Raiz da UI compartilhada: a mesma árvore Compose roda no Android e no iOS. */
@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MiboColors.Green, onPrimary = Color.White,
            background = MiboColors.PageBg, surface = MiboColors.Card,
            onBackground = MiboColors.TextPrimary, onSurface = MiboColors.TextPrimary,
            secondary = MiboColors.GreenNav,
            // Diálogos e menus brancos como os cartões, em vez do cinza-lilás padrão do Material 3.
            surfaceContainerHigh = MiboColors.Card, surfaceContainer = MiboColors.Card,
            outline = MiboColors.Divider,
        )
    ) {
        // Fora do NavHost: o dono é o ViewModelStoreOwner do host (Activity / ComposeUIViewController).
        val vm = koinViewModel<HomeViewModel>()
        val nav = rememberNavController()
        // Decidido UMA vez (senão o NavHost trocaria de destino a cada recomposição). Validação em andamento
        // (token só na sessão) volta para a tela de token, que mostra o progresso.
        val start: Any = remember { if (vm.token.value != null && vm.tokenValidation.value !is TokenValidation.Validating) HomeRoute else TokenRoute }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(nav, startDestination = start) {
                composable<TokenRoute> {
                    TokenScreen(vm, onConnected = { nav.navigate(HomeRoute) { popUpTo<TokenRoute> { inclusive = true } } })
                }
                composable<HomeRoute> {
                    HomeShell(
                        vm,
                        onCamera = { nav.navigate(CameraRoute(it.ns)) },
                        onDevice = { nav.navigate(DeviceRoute(it.ns)) },
                        onLogout = { vm.logout(); nav.navigate(TokenRoute) { popUpTo(0) } }, // limpa a pilha inteira
                    )
                }
                composable<CameraRoute> { entry -> CameraScreen(vm, entry.toRoute<CameraRoute>().ns, onBack = { nav.popBackStack() }) }
                composable<DeviceRoute> { entry -> DeviceScreen(vm, entry.toRoute<DeviceRoute>().ns, onBack = { nav.popBackStack() }) }
            }
        }
    }
}
```

`TokenScreen` (layout na seção 6.3). Núcleo:

```kotlin
val suggested = vm.suggestedToken
// `remember` e não `rememberSaveable`: o token digitado não deve ir para o estado salvo do sistema (Bundle).
var token by remember { mutableStateOf(suggested.orEmpty()) }
var visible by rememberSaveable { mutableStateOf(false) }
// A validação mora no ViewModel: rotação durante "Validando token…" não a cancela nem perde o resultado.
val validation by vm.tokenValidation.collectAsStateWithLifecycle()
val validating = validation is TokenValidation.Validating
val error = (validation as? TokenValidation.Failed)?.error
val canSubmit = TokenFormat.isPlausible(token) && !validating

LaunchedEffect(validation) {
    if (validation is TokenValidation.Done) {
        vm.tokenValidationHandled()
        onConnected()
    }
}
fun submit() { if (canSubmit) vm.submitToken(token) }
```

Para o M6 a `HomeShell` pode ser provisória (lista de nomes + botão Sair); a versão Mibo é o M7. As telas de câmera/dispositivo podem ser stubs com `BackButton`.

**Testes.** `HomeViewModelTest` (5; só fakes das interfaces do domínio + casos de uso reais, nenhuma classe de `data`): `validationRunsInViewModelScopeAndReportsDone`, `rejectedTokenBecomesFailed`, `acceptedTokenThatCouldNotBeStoredRaisesANotice` (`FakeTokenRepository(canPersist = false)`), `clearingTheViewModelMidValidationDropsTheSessionToken`, `startLiveRethrowsCancellation` (`FakeDeviceRepository.startLiveDelayMs`). Apoio no `TestSupport.kt`:

```kotlin
/** Mapeador previsível para testes de apresentação; o mapeamento real é coberto pelo GdiErrorMapperTest. */
object FakeErrorMapper : ErrorMapper {
    override fun toAppError(error: Throwable): AppError = AppError.Unexpected(error.message)
    override fun toShortMessage(error: Throwable): String = error.message.orEmpty()
}

/** HomeViewModel com casos de uso reais sobre fakes: o teste exercita só a apresentação. */
fun homeViewModel(devices: DeviceRepository, tokens: TokenRepository): HomeViewModel =
    HomeViewModel(devices, tokens, SubmitTokenUseCase(tokens, devices), ChangeLockVolumeUseCase(devices), FakeErrorMapper)
```

Configuração:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) } // viewModelScope usa Main; runTest herda o relógio
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun clearingTheViewModelMidValidationDropsTheSessionToken() = runTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual), então a validação fica em andamento.
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val store = ViewModelStore()
        // Caminho real de ciclo de vida: store.clear() cancela o viewModelScope.
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { homeViewModel(devices, tokens) } })[HomeViewModel::class]
        vm.submitToken("Ot_interrupted_0123")
        runCurrent()
        assertEquals("Ot_interrupted_0123", tokens.token.value)
        // Act
        store.clear()
        runCurrent()
        // Assert
        assertNull(tokens.token.value)
        assertEquals(DevicesState.NoToken, devices.state.value)
        assertTrue(tokens.persistedValues.isEmpty())
    }
}
```

**Pronto quando.** No aparelho Android: sem token abre a tela de token; token inválido mostra mensagem e fica na tela; token válido entra; **girar o aparelho durante "Validando token…"** não perde a validação; reabrir o app entra direto; Sair volta à tela de token e o "voltar" não retorna à Home. No iPhone: mesmo fluxo; o Keychain guarda o token entre execuções. Visual: sem captura de referência, a tela confere com a árvore da seção 6.3 (escudo verde, título, campo mascarado com olho, botão verde largo) e as cores da 6.1.

**Verificar com.** `./gradlew :shared:testAndroidHostTest :androidApp:installDebug` + rodar `iosApp` pelo Android Studio.

---

### M7 — Home no visual Mibo (RF02, RF04, RF07, RF08) + Definições

**Referência visual:** abra `design/home.jpg` antes de codar a casca e a Home (seções 6.2 e 6.4) e mantenha a imagem à vista enquanto escreve `HomeShell.kt`/`Components.kt`. Resultado esperado dos estados que o Mibo não mostra: `design/app-03-home-grade.png`, `app-04-home-lista.png`, `app-05-filtro-compartilhados-vazio.png`, `app-06-paginacao-carregar-mais.png`, `app-07-paginacao-fim.png`, `app-08-definicoes.png` e as abas `app-20`, `app-21`, `app-22` (leia as observações da tabela 6.0). Definições, Inteligente, Mensagens e Loja não têm captura: seguem o tema (6.1) e a seção 6.2.

**Objetivo.** A casca com 5 abas e a Home completa com estados, chips, grade/lista, cartões desenhados, rodapé de paginação; Definições com conta, itens por página e Sair.

**Arquivos.** `ui/HomeShell.kt`, `ui/Components.kt` (completo), `ui/MiboLogic.kt`, e `platform/WeekStrip.kt` **só com a parte pura** (`WeekDay`, `WEEK_STRIP_OFFSETS`, `weekInitial`, `buildWeekStrip`; o código está no M9) para o `MiboLogicTest` compilar já agora. As três `expect fun` e os actuals entram no M9.

`ui/MiboLogic.kt` (funções puras, testadas):

```kotlin
/** Lentes da linha "Dual" na ordem dos canais da GDI: canalVideo 0 = móvel, 1 = fixa. Usado pela CameraScreen (M8). */
val LENS_LABELS = listOf("Lente móvel", "Lente fixa")

/** Lentes pelo nome do modelo: a GDI não informa canais; a linha "Dual" (iM4 Dual) tem duas (móvel + fixa).
 *  O nome também é consultado porque o modelo pode vir genérico e o nome de fábrica traz o sufixo. */
fun lensCount(model: String?, name: String? = null): Int =
    if (listOfNotNull(model, name).any { it.contains("dual", ignoreCase = true) }) 2 else 1

/** "Online"/"Offline"; quando a GDI não informa (null) mostramos "—" em vez de inventar estado. */
fun onlineLabel(online: Boolean?): String = when (online) { true -> "Online"; false -> "Offline"; null -> "—" }

/** Rótulo "compartilhado"; vinculado é o caso comum e não ganha rótulo. */
fun sharedLabel(origin: DeviceOrigin?): String? = if (origin == DeviceOrigin.SHARED) "compartilhado" else null

/** Subtítulo da linha no modo lista: "MFR 2030 • via hub • compartilhado". */
fun deviceSubtitle(device: Device): String =
    listOfNotNull(device.model ?: device.kind.label, "via hub".takeIf { device.subdevice }, sharedLabel(device.origin)).joinToString(" • ")
```

`ui/Components.kt` (peças compartilhadas):

```kotlin
/** Regra de honestidade: controle sem rota na GDI avisa em vez de ficar mudo. */
const val GDI_UNAVAILABLE = "Indisponível na API GDI"

/** Snackbar por tela. Descarta o aviso anterior: toques repetidos não enfileiram segundos de espera. */
class Snack(val host: SnackbarHostState, private val scope: CoroutineScope) {
    operator fun invoke(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
}

@Composable
fun rememberSnack(): Snack {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(host, scope) { Snack(host, scope) }
}

/** Hub desenhado em Canvas (nada de imagem de produto Intelbras): disco branco, anel cinza, botão central com anel azulado. */
@Composable
fun HubDrawing(modifier: Modifier, ring: Color = Color(0xFFD9D9D9), ringWidth: Dp = 2.dp, centerFraction: Float = 22f / 84f,
               centerRing: Color = Color(0xFFBFD9FF), shadow: Dp = 0.dp) {
    Canvas(modifier.shadow(shadow, CircleShape).background(Color.White, CircleShape)) {
        val radius = size.minDimension / 2
        val stroke = ringWidth.toPx()
        drawCircle(ring, radius - stroke / 2, style = Stroke(stroke))
        val inner = radius * centerFraction
        drawCircle(Color.White, inner)
        drawCircle(centerRing, inner, style = Stroke(stroke))
    }
}

/** Fechadura desenhada: corpo escuro, cadeado e três pontos do teclado. */
@Composable
fun LockDrawing(modifier: Modifier = Modifier) {
    Column(modifier.size(34.dp, 64.dp).background(Color(0xFF2E3338), RoundedCornerShape(10.dp)).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.height(10.dp))
        repeat(3) { Box(Modifier.padding(vertical = 2.dp).size(4.dp).background(Color(0xFF8A8F94), CircleShape)) }
    }
}
```

Mais componentes no mesmo arquivo: `Title(text, action)`, `BackButton(onBack)` (`Icons.Default.ArrowBackIosNew` 20 dp, cd "Voltar"), `kindIcon(kind)` (Videocam, Lock, Hub, Sensors, Lightbulb, DevicesOther), `CameraThumb(device, modifier, badge, playSize)` (fundo `0xFF2B2F33`, círculo branco 35 % com PlayArrow; a GDI não tem snapshot), `LensBadge(count)`, `OnlineDot(online)`, `DeviceDrawing(device)`, `DeviceInfoDialog(device, providerName, extra, onDismiss)` (linhas chave/valor: Tipo, Modelo, Número de série, ID do produto, Origem, Parceiro, Conectado via hub, Versão, Último contato = `device.lastOnline`, que já chega formatado do parser; a UI não importa `GdiFormat`; botão "Entendi").

Grade com duas colunas de **mesma altura**:

```kotlin
/** A altura de cada linha vem do cartão mais alto (nome + miniatura 16:10), para a fechadura ao lado da câmera ficar igual. */
devices.chunked(2).forEach { pair ->
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pair.forEach { d ->
            if (d.isCamera) CameraCard(d, Modifier.weight(1f).fillMaxHeight()) { onCamera(d) }
            else DeviceCard(d, Modifier.weight(1f).fillMaxHeight()) { onDevice(d) }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
}
```

Rodapé de paginação (RF08):

```kotlin
Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    content.note?.let { Text(it, Modifier.padding(bottom = 8.dp), color = MiboColors.TextSecondary, fontSize = 12.sp) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MiboColors.Divider)
        if (content.hasMore) {
            OutlinedButton(onClick = onLoadMore, enabled = !content.loadingMore, modifier = Modifier.padding(horizontal = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MiboColors.Green), border = BorderStroke(1.dp, MiboColors.Green)) {
                if (content.loadingMore) { CircularProgressIndicator(Modifier.size(16.dp), color = MiboColors.Green, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text("Carregar mais")
            }
        } else {
            // Texto da referência Mibo; a frase completa fica para acessibilidade.
            Text("Sem mais informações", Modifier.padding(horizontal = 12.dp).semantics { contentDescription = "Todos os dispositivos carregados" },
                color = MiboColors.TextSecondary, fontSize = 13.sp)
        }
        HorizontalDivider(Modifier.weight(1f), color = MiboColors.Divider)
    }
    content.loadMoreError?.let { error ->
        Text(error.userMessage, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onLoadMore) { Text("Tentar novamente") }
    }
    Text("${content.pagesLoaded} página(s) · ${content.devices.size} dispositivo(s) · ${content.query.pageSize} por página",
        Modifier.padding(top = 8.dp), color = MiboColors.TextSecondary, fontSize = 12.sp)
}
```

Cartão de erro: botão "Tentar novamente" sempre; "Trocar token" só para `TokenInvalid` **ou** `TokenExpired` (mesma saída: logout). `detail` só se `isDebugBuild`.

**Testes.** `MiboLogicTest` (5): `lensBadgeComesFromDualInModelOrName`, `weekInitialsFollowPortugueseNames`, `weekStripGoesFromSixDaysAgoToTomorrowWithTodayAtIndexSix` (o `WeekStrip` é do M9, mas a lógica pura pode entrar agora), `onlineAndSharedLabels`, `deviceSubtitleJoinsModelHubAndShared`.

**Pronto quando.** No aparelho, com a conta de teste: Home mostra câmera iM4 Dual (badge "2"), fechadura MFR 2030 ("via hub") e hub MCA 1002; chips Compartilhados → cartão vazio "Nenhum dispositivo compartilhado com esta conta."; Definições → 2 por página → Início mostra 2 itens + "Carregar mais" → 3 itens + "Sem mais informações" + "2 página(s) · 3 dispositivo(s) · 2 por página"; modo avião + Atualizar → cartão "Sem conexão…"; token mascarado em Definições; cada controle sem API mostra Snackbar.
**Lado a lado com `home.jpg`:** no Android físico, com a conta de teste e o filtro Todos, rode `adb exec-out screencap -p > tela.png` e abra a captura ao lado de `design/home.jpg`. Têm de coincidir: fundo cinza-claro; "Minha casa ⌄" grande à esquerda com lupa e "+" branco em círculo verde à direita; banner verde vivo com o texto em verde-escuro à esquerda e "Mais registros para sua câmera" + pílula escura "Contratar agora" à direita; cartão branco arredondado "Selecione suas cenas preferidas" com chevron; "Meus dispositivos" com a barrinha verde embaixo e os ícones grade/lista à direita; grade de 2 colunas com cartões brancos — câmera com nome sem negrito + "••" no topo e miniatura escura com badge "2" no canto superior esquerdo e play translúcido no centro; fechadura e hub com bolinha online verde-água no canto superior esquerdo, desenho do aparelho e nome em negrito; "Sem mais informações" entre dois traços; FAB quadrado verde no canto inferior direito; barra inferior branca com Início verde e Inteligente/Mensagens/Loja/Definições em cinza. Diferenças aceitas: chips de filtro e contagem de paginação (RF07/RF08), desenhos no lugar das fotos de produto, miniatura da câmera desenhada (a GDI não fornece imagem estática), ícones do banner e do cartão de cenas, nomes da conta.

**Verificar com.** `./gradlew :shared:testAndroidHostTest :androidApp:installDebug` + iOS pelo Android Studio.

---

### M8 — Vídeo ao vivo (RF03): libVLC × VLCKit

**Referência visual:** abra antes de codar a `CameraScreen` as três capturas: `design/camera-carregando.jpg` (estado carregando, com %), `design/camera-ao-vivo.jpg` (vídeo tocando) e `design/camera-ptz.jpg` (d-pad PTZ aberto). Layout na seção 6.5. Resultado esperado deste app: `design/app-09-camera-carregando.png`, `app-10-camera-ao-vivo.png`, `app-11-camera-lente-fixa.png`, `app-12-camera-ptz-aviso.png` e `app-13-camera-informacoes.png`.

**Objetivo.** Ponto nativo único da tela de câmera: `expect fun CameraPlayer`. Android com libVLC em `AndroidView`; iOS com VLCKit implementado em **Swift** sobre uma **interface Kotlin** (inversão de dependência, sem cinterop). Sessão GDI aberta ao entrar e liberada ao sair, progresso em %, "1º quadro em X s", lentes.

**Arquivos.** `commonMain/player/CameraPlayer.kt`, `androidMain/player/CameraPlayer.android.kt`, `iosMain/player/NativeVideoPlayer.kt`, `iosMain/player/CameraPlayer.ios.kt`, `iosApp/iosApp/VLCVideoPlayer.swift`, `iOSApp.swift` (registro), `ui/CameraScreen.kt`.

`commonMain/.../player/CameraPlayer.kt`:

```kotlin
package br.com.pompeo.casa.player

/**
 * Ponto onde a UI compartilhada precisa de código nativo: decodificar vídeo depende da plataforma.
 * Android: libVLC. iOS: VLCKit, implementado em Swift sobre uma interface Kotlin.
 */
@Composable
expect fun CameraPlayer(
    url: String,
    muted: Boolean,
    modifier: Modifier = Modifier,       // valores padrão só no expect
    onProgress: (PlayerProgress) -> Unit = {},
)

/** Progresso reportado pelo player nativo, de 30 a 100 %. Os primeiros 30 % são a chamada à GDI, medida pela tela. */
data class PlayerProgress(val percent: Int, val label: String, val playing: Boolean = false, val error: String? = null)

/**
 * Buffer de rede do libVLC no Android. 300 ms fez o Moto G9 Play (H.265 por software) entrar em espiral:
 * "more than 5 seconds of late video -> dropping frame". 800 ms dá folga; custa ~0,5 s de latência.
 */
const val LIVE_NETWORK_CACHING_MS = 800
```

`androidMain/.../player/CameraPlayer.android.kt` (libVLC):

```kotlin
/** Android: libVLC para RTSP (o que a GDI real devolve) e também HTTP(S), se a API um dia seguir o Swagger. */
@Composable
actual fun CameraPlayer(url: String, muted: Boolean, modifier: Modifier, onProgress: (PlayerProgress) -> Unit) {
    val context = LocalContext.current.applicationContext // LibVLC não deve segurar a Activity
    val currentOnProgress by rememberUpdatedState(onProgress) // o listener nativo sempre chama a lambda mais recente
    var restart by remember(url) { mutableIntStateOf(0) }
    var layoutReady by remember { mutableStateOf(false) }

    // "--rtsp-tcp": RTP dentro da conexão TCP do RTSP (atravessa NAT/firewall, sem perda de UDP).
    val libVlc = remember { LibVLC(context, arrayListOf("--rtsp-tcp", "--network-caching=$LIVE_NETWORK_CACHING_MS")) }
    val player = remember(libVlc) { MediaPlayer(libVlc) }
    DisposableEffect(player) {
        // Eventos do libVLC viram a porcentagem da tela (30–100 %).
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> currentOnProgress(PlayerProgress(35, "Conectando ao stream"))
                MediaPlayer.Event.Buffering ->
                    if (event.buffering < 100f) currentOnProgress(PlayerProgress(40 + (event.buffering * 0.5f).toInt(), "Carregando vídeo"))
                MediaPlayer.Event.Playing -> currentOnProgress(PlayerProgress(92, "Decodificando o primeiro quadro"))
                // Vout = a saída de vídeo recebeu a primeira imagem: é o "primeiro quadro na tela".
                MediaPlayer.Event.Vout -> if (event.voutCount > 0) currentOnProgress(PlayerProgress(100, "Ao vivo", playing = true))
                MediaPlayer.Event.EncounteredError -> currentOnProgress(PlayerProgress(0, "", error = "Falha ao reproduzir o stream"))
                MediaPlayer.Event.EndReached -> currentOnProgress(PlayerProgress(0, "", error = "Transmissão encerrada"))
            }
        }
        onDispose {
            player.setEventListener(null) // nada chega a uma composição morta
            // detachViews mexe em View: main thread. stop()/release() são síncronos e, com a leitura RTSP
            // travada, esperam o timeout: na main thread isso deu ANR. Em outra thread, não.
            player.detachViews()
            Thread({
                player.stop()
                player.release()
                libVlc.release()
            }, "vlc-release").start()
        }
    }

    // Só inicia depois que a superfície de vídeo existe (attachViews no factory).
    LaunchedEffect(url, restart, layoutReady) {
        if (!layoutReady) return@LaunchedEffect
        currentOnProgress(PlayerProgress(32, "Iniciando player"))
        val media = Media(libVlc, Uri.parse(url)).apply {
            // Sem "fmtp" no SDP o decoder de HARDWARE não conhece a resolução e falha ("Set Resolution failed");
            // o de software lê VPS/SPS/PPS do próprio fluxo. (enabled = false, force = false)
            setHWDecoderEnabled(false, false)
            // O áudio AAC também vem sem "config" no SDP e decodifica como ruído: desativado.
            addOption(":no-audio")
            addOption(":network-caching=$LIVE_NETWORK_CACHING_MS") // opção por mídia usa ":"; global usa "--"
        }
        player.media = media
        media.release() // Media é nativo com contagem de referência; o player guarda a sua
        player.play()
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0 else 100 }

    // Ao vivo não "pausa": em background para o stream e reconecta ao voltar (poupa banda e cota).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> if (layoutReady) restart++
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        // (layout, displayManager = null, subtitles = false, textureView = false)
        factory = { ctx -> VLCVideoLayout(ctx).also { player.attachViews(it, null, false, false); layoutReady = true } },
        modifier = modifier,
    )
}
```

Imports: `org.videolan.libvlc.{LibVLC, Media, MediaPlayer}`, `org.videolan.libvlc.util.VLCVideoLayout`, `androidx.lifecycle.compose.LocalLifecycleOwner`, `androidx.lifecycle.{Lifecycle, LifecycleEventObserver}`, `androidx.compose.ui.viewinterop.AndroidView`, `android.net.Uri`.

`iosMain/.../player/NativeVideoPlayer.kt` (o contrato que o Swift implementa):

```kotlin
package br.com.pompeo.casa.player

import platform.UIKit.UIView

/**
 * Contrato que o Swift implementa (VLCKit). O Kotlin/Native não importa o VLCKit: em vez de cinterop
 * (.def, headers e linkagem por target, sincronizados com o SPM), o app iOS registra uma implementação
 * na inicialização. Kotlin define, Swift entrega; o Gradle nem sabe que o VLCKit existe.
 */
interface NativeVideoPlayer {
    fun view(): UIView
    /** O Swift chama este listener com os estados do VLCKit. */
    fun setListener(listener: NativeVideoListener?)
    fun play(url: String)
    fun setMuted(muted: Boolean)
    fun stop()
}

/** Callbacks do player nativo para o Compose: porcentagem, primeiro quadro e erro. (Flow não atravessa para o Swift.) */
interface NativeVideoListener {
    fun onProgress(percent: Int, label: String)
    fun onFirstFrame()
    fun onError(message: String)
}

interface NativeVideoPlayerFactory {
    fun create(): NativeVideoPlayer
}

/** No Swift: `NativePlayers.shared.factory = VLCVideoPlayerFactory()`. */
object NativePlayers {
    var factory: NativeVideoPlayerFactory? = null
}
```

`iosMain/.../player/CameraPlayer.ios.kt`:

```kotlin
/** iOS: o AVPlayer não reproduz RTSP; usamos o VLCKit, implementado em Swift (ver [NativeVideoPlayer]). */
@Composable
actual fun CameraPlayer(url: String, muted: Boolean, modifier: Modifier, onProgress: (PlayerProgress) -> Unit) {
    val factory = NativePlayers.factory
    if (factory == null) {
        // Esqueceu o registro no iOSApp.swift: mostra o problema em vez de quebrar.
        Box(modifier.background(Color(0xFF171E24)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideocamOff, null, tint = Color.White)
                Text("Player nativo não registrado", color = Color.White)
            }
        }
        return
    }
    // key(url): o factory do UIKitView roda uma vez por nó; URL nova = nó novo com a view do player novo.
    key(url) {
        val player = remember { factory.create() }
        val currentOnProgress by rememberUpdatedState(onProgress)
        DisposableEffect(player) {
            // Objeto Kotlin implementando interface Kotlin, entregue ao Swift: o VLCKit chama de volta.
            player.setListener(object : NativeVideoListener {
                override fun onProgress(percent: Int, label: String) = currentOnProgress(PlayerProgress(percent, label))
                override fun onFirstFrame() = currentOnProgress(PlayerProgress(100, "Ao vivo", playing = true))
                override fun onError(message: String) = currentOnProgress(PlayerProgress(0, "", error = message))
            })
            currentOnProgress(PlayerProgress(32, "Iniciando player"))
            player.play(url)
            onDispose {
                player.setListener(null)
                player.stop()
            }
        }
        LaunchedEffect(player, muted) { player.setMuted(muted) }
        UIKitView(factory = { player.view() }, modifier = modifier)
    }
}
```

`iosApp/iosApp/VLCVideoPlayer.swift`:

```swift
import Shared
import UIKit
import VLCKitSPM

/// Implementa em Swift a interface Kotlin `NativeVideoPlayer`: o Compose controla, o VLCKit decodifica.
/// Necessário porque o AVPlayer não reproduz RTSP e a nuvem Intelbras entrega H.265 por RTSP.
/// NSObject: uma interface Kotlin chega ao Swift como protocolo Objective-C.
final class VLCVideoPlayer: NSObject, NativeVideoPlayer, VLCMediaPlayerDelegate {
    private let container = UIView()
    private let player = VLCMediaPlayer()
    private var listenerRef: NativeVideoListener?
    private var firstFrameSent = false

    override init() {
        super.init()
        container.backgroundColor = .black
        player.drawable = container
        player.delegate = self
    }

    func view() -> UIView { container }

    func setListener(listener: NativeVideoListener?) {
        listenerRef = listener
    }

    func play(url: String) {
        guard let mediaURL = URL(string: url) else {
            listenerRef?.onError(message: "URL inválida")
            return
        }
        firstFrameSent = false
        let media = VLCMedia(url: mediaURL)
        // Mesmo diagnóstico do Android: sem "fmtp" no SDP o decoder de hardware não sabe a resolução;
        // o software (avcodec) lê VPS/SPS/PPS do fluxo. O áudio AAC vem sem "config" e é desativado.
        // "codec=avcodec" força o software já na 1.ª tentativa: só "avcodec-hw=none" não basta, o VLC
        // tentava antes o VideoToolbox, que sem "fmtp" espera e falha (abria bem mais devagar).
        // Dicionário chave→valor: aqui as opções vão SEM ":"/"--".
        media.addOptions([
            "network-caching": 300,
            "clock-jitter": 0,
            "rtsp-tcp": true,
            "codec": "avcodec",
            "avcodec-hw": "none",
            "no-audio": true,
        ])
        player.media = media
        player.play()
    }

    func setMuted(muted: Bool) {
        player.audio?.isMuted = muted
    }

    func stop() {
        player.stop()
    }

    // MARK: - VLCMediaPlayerDelegate -> porcentagem no Compose (30–100 %)

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        switch player.state {
        case .opening: listenerRef?.onProgress(percent: 35, label: "Conectando ao stream") // Int Kotlin = Int32 no Swift
        case .buffering: if !firstFrameSent { listenerRef?.onProgress(percent: 60, label: "Carregando vídeo") }
        case .playing: if !firstFrameSent { listenerRef?.onProgress(percent: 85, label: "Decodificando o primeiro quadro") }
        case .error: listenerRef?.onError(message: "Falha ao reproduzir o stream")
        case .ended: listenerRef?.onError(message: "Transmissão encerrada")
        default: break
        }
    }

    /// O tempo só avança quando quadros estão sendo exibidos: primeiro avanço = primeiro quadro na tela.
    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        guard !firstFrameSent, player.hasVideoOut else { return }
        firstFrameSent = true
        listenerRef?.onFirstFrame()
    }
}

final class VLCVideoPlayerFactory: NSObject, NativeVideoPlayerFactory {
    func create() -> NativeVideoPlayer { VLCVideoPlayer() }
}
```

`iOSApp.swift` final:

```swift
import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Mesmo grafo Koin do Android: repositório GDI, token store e ViewModel.
        KoinKt.doInitKoin()
        // Registra o player nativo (Swift + VLCKit) que o Compose usa via expect/actual.
        NativePlayers.shared.factory = VLCVideoPlayerFactory()
    }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

Nota sobre o buffer iOS: 300 ms + `clock-jitter 0` continua no iOS porque no Android essa combinação causou espiral num aparelho de entrada; o iPhone 13 Pro aguentou, mas mediu 37,8 s a frio contra ~21 s no Android. **Hipótese não comprovada** a testar se sobrar tempo: alinhar o iOS com 800 ms e sem `clock-jitter`. Registre o resultado no README (Limitações).

`ui/CameraScreen.kt` — bloco de sessão (a parte sensível; `LENS_LABELS` vem do `MiboLogic.kt`):

```kotlin
/** Fato da API: criar-fluxo-video devolve o mesmo RTSP para canalVideo 0 e 1 na iM4 Dual. */
const val SAME_STREAM_NOTE = "A API GDI devolve o mesmo vídeo para as duas lentes desta câmera."

/** 3140 -> "3,1 s" (formato brasileiro sem String.format, que não existe no commonMain). Trunca. */
internal fun formatSeconds(ms: Long): String = "${ms / 1000},${(ms % 1000) / 100} s"

@Composable
fun CameraScreen(vm: HomeViewModel, ns: String, onBack: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val camera = devices.firstOrNull { it.ns == ns && it.isCamera }
    if (camera == null) {
        // Câmera saiu da lista (logout/refresh): volta num efeito, não durante a composição.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var muted by rememberSaveable { mutableStateOf(true) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var ptzOpen by rememberSaveable { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val snack = rememberSnack()
    var channel by rememberSaveable(camera.ns) { mutableIntStateOf(0) } // lente; trocar reinicia a sessão
    val lenses = lensCount(camera.model, camera.name)

    var session by remember(camera.ns) { mutableStateOf<StreamSession?>(null) }
    var liveError by remember(camera.ns) { mutableStateOf<String?>(null) }
    var liveAttempt by remember(camera.ns) { mutableIntStateOf(0) }
    // Métrica de desempenho: do toque (ou retry/troca de lente) até o 1.º quadro, incluindo a GDI. Relógio monotônico.
    var startedAt by remember(camera.ns, liveAttempt) { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var progress by remember(camera.ns, liveAttempt) { mutableStateOf(PlayerProgress(0, "Solicitando sessão à Intelbras")) }
    var firstFrameMs by remember(camera.ns, liveAttempt) { mutableStateOf<Long?>(null) }

    LaunchedEffect(camera.ns, liveAttempt, channel) {
        liveError = null
        // Trocar de lente ou "Tentar novamente" substitui a sessão: a anterior é encerrada, não abandonada.
        session?.let(vm::stopLive)
        session = null
        vm.startLive(camera, channel).onSuccess { session = it }.onFailure { liveError = vm.messageOf(it) }
    }
    // A GDI não informa progresso: estimamos 0–29 % pelo tempo típico da chamada (~2,4 s medidos).
    LaunchedEffect(camera.ns, liveAttempt) {
        while (session == null && liveError == null) {
            val ms = startedAt.elapsedNow().inWholeMilliseconds
            progress = PlayerProgress(minOf(29, (ms * 30 / 2400).toInt()), "Solicitando sessão à Intelbras")
            delay(100)
        }
    }
    // Saída da tela: stopLive roda no viewModelScope (o escopo desta tela já está cancelado aqui).
    DisposableEffect(camera.ns) { onDispose { session?.let(vm::stopLive) } }

    val onProgress: (PlayerProgress) -> Unit = { p ->
        progress = p.copy(percent = maxOf(progress.percent, p.percent)) // nunca regride
        if (p.playing && firstFrameMs == null) firstFrameMs = startedAt.elapsedNow().inWholeMilliseconds
    }

    /** Um player por vez: encerra a sessão ANTES de mudar o canal, para o efeito novo não encerrá-la de novo. */
    fun switchLens(lens: Int) {
        if (lens == channel) return
        session?.let(vm::stopLive)
        session = null
        channel = lens
        liveAttempt++
    }

    val error = liveError ?: progress.error
    // Depois do 1.º quadro o overlay não volta: rebuffering breve não deve cobrir o vídeo.
    val loading = error == null && !progress.playing && firstFrameMs == null
    // ... layout (seção 6.5): o Box do vídeo é o MESMO nó nos dois modos (tela cheia só muda o Modifier),
    // então alternar não recria o player nem a sessão. CameraPlayer só existe com session != null.
}
```

Dentro do `Box` do vídeo: `session?.let { CameraPlayer(it.streamUrl, muted, Modifier.fillMaxSize(), onProgress) }`; badge "1/1"; erro + "Tentar novamente" (`liveAttempt++`) ou `LoadingOverlay`; chip "AO VIVO" só com `progress.playing`; "1º quadro em ${formatSeconds(ms)}"; botões Multiview (snack "Multiview indisponível na API GDI") e Tela cheia. Diálogo de informações com linha extra "Fluxo" → "RTSP da nuvem • expira automaticamente" quando `session?.sessionId == null`.

**Testes.** Já existem `GdiApiTest.acceptsRealRtspOnlyStreamResponse` e `streamChannelIsSentAsCanalVideo` (M3), `DeviceRepositoryImplTest.startLivePassesLensChannelToProvider` (M5; canais `[0,1,0]` chegam ao provider) e `HomeViewModelTest.startLiveRethrowsCancellation` (M6). Acrescente só `CameraFormatTest.formatSecondsTruncatesToTenths` (3140 → "3,1 s"; 3199 → "3,1 s"; 950 → "0,9 s"). Players nativos não têm teste unitário: são validados em aparelho.

**Pronto quando.** No Moto G9 (Android) e no iPhone: abrir a iM4 Dual mostra % subindo, etapa, "AO VIVO" e "1º quadro em X s"; abrir duas vezes (a frio ~21 s com o player parado em 92 % até chegar um quadro-chave; a quente ~3 s); trocar para "Lente fixa" reinicia e mostra a nota; tela cheia não reinicia o vídeo; mandar o app para background e voltar reconecta (Android); sair da tela durante "Conectando" **não dá ANR**; logcat sem "late video -> dropping frame" em espiral.
**Lado a lado com as três capturas:** no Android físico, capture três estados com `adb exec-out screencap -p > tela.png` — carregando (compare com `camera-carregando.jpg`), ao vivo (`camera-ao-vivo.jpg`) e PTZ aberto (`camera-ptz.jpg`). Têm de coincidir: barra superior branca com voltar, nome da câmera e, à direita, os ícones assistente, compartilhar e configurações; espaço branco e faixa de vídeo de borda a borda; enquanto carrega, fundo cinza-escuro, badge "1/1" no canto superior esquerdo, spinner verde com "NN%" branco no centro e ícones Multiview e Tela cheia no canto inferior direito; área branca abaixo do vídeo; pílula verde do PTZ encostada na borda esquerda (esmaecida enquanto carrega, verde cheia depois); com o PTZ aberto, círculo verde com quatro setas brancas, centro branco e botão × escuro no canto superior direito; barra inferior com mudo, gravar, microfone e foto (cinza enquanto carrega, pretos depois) e tela cheia (sempre preto). Diferenças aceitas: um player por vez com o seletor "Lente móvel | Lente fixa" no lugar das duas lentes empilhadas; chip "AO VIVO" e "1º quadro em X s" no lugar de "0KB/s"; etapa escrita abaixo do %; badge, chip e ícones continuam sobre o vídeo com o PTZ aberto; a imagem do vídeo é a da conta.

**Verificar com.** `adb logcat | grep -iE "vlc|late video|ANR"` durante os testes; iOS pelo Console do Xcode.

---

### M9 — Fechadura e hub (RF05, RF06, RF09)

**Referência visual:** abra `design/fechadura-mfr2030.jpg` antes de codar a `LockScreen` e `design/hub-mca1002.jpg` antes de codar a `HubScreen` (seções 6.6 e 6.7). `OtherDeviceScreen` não tem captura: segue o tema. Resultado esperado deste app: `design/app-14-fechadura.png`, `app-15-fechadura-volume.png`, `app-16-fechadura-confirmacao.png`, `app-17-fechadura-historico.png`, `app-18-hub-acessorio.png` e `app-19-hub-mensagens.png`.

**Objetivo.** `DeviceScreen` que escolhe `LockScreen`, `HubScreen` ou `OtherDeviceScreen` pelo `kind`.

**Arquivos.** `ui/DeviceScreen.kt`; em `platform/WeekStrip.kt` acrescente as três `expect fun` (a parte pura já veio no M7) + `WeekStrip.android.kt` e `WeekStrip.ios.kt`.

Entrada:

```kotlin
/** Explica por que o comando pela nuvem está bloqueado. */
const val REMOTE_HINT = "Habilite a abertura remota no app Mibo Smart para comandar pela nuvem."

@Composable
fun DeviceScreen(vm: HomeViewModel, ns: String, onBack: () -> Unit) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val device = devices.firstOrNull { it.ns == ns }
    if (device == null) { LaunchedEffect(Unit) { onBack() }; return }
    val snack = rememberSnack()
    var info by remember { mutableStateOf<List<Pair<String, String>>?>(null) } // null = diálogo fechado
    Scaffold(
        containerColor = MiboColors.PageBg,
        snackbarHost = { SnackbarHost(snack.host) },
        // Os cabeçalhos coloridos (degradê/cinza) sobem até a status bar; cada tela aplica statusBarsPadding.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (device.kind) {
                DeviceKind.LOCK -> LockScreen(vm, device, snack, onBack, onInfo = { info = it })
                DeviceKind.HUB -> HubScreen(vm, device, children = devices.filter { it.parentNs == device.ns }, onBack, onInfo = { info = emptyList() })
                else -> OtherDeviceScreen(vm, device, onBack, onInfo = { info = emptyList() })
            }
        }
    }
    info?.let { extra -> DeviceInfoDialog(device, vm.providerName(device) ?: device.providerId, extra = extra, onDismiss = { info = null }) }
}
```

`LockScreen` — estado e textos:

```kotlin
val scope = rememberCoroutineScope()
var details by remember(lock.ns) { mutableStateOf<LockDetails?>(null) }
var error by remember(lock.ns) { mutableStateOf<String?>(null) }
var loading by remember(lock.ns) { mutableStateOf(true) }
var reload by remember(lock.ns) { mutableIntStateOf(0) }
var confirm by remember { mutableStateOf<Boolean?>(null) } // true = destrancar, false = trancar
var sending by remember { mutableStateOf(false) }
var online by remember(lock.ns) { mutableStateOf(lock.online) }
// Volume e histórico atualizam só o próprio campo, sem repetir as outras leituras.
val update: ((LockDetails) -> LockDetails) -> Unit = { f -> details = f(details ?: LockDetails(null, null, null, null, null, emptyList())) }

LaunchedEffect(lock.ns, reload) {
    loading = true
    vm.lockDetails(lock).onSuccess { details = it; error = null }.onFailure { error = vm.messageOf(it) }
    loading = false
}
LaunchedEffect(lock.ns) { vm.isOnline(lock).onSuccess { online = it ?: online } }

val open = details?.open
val settled = details != null || !loading // campo que falhou vira texto, não sumiço
val remote = details?.remoteEnabled == true
val stateText = when (open) { true -> "Porta aberta"; false -> "Porta fechada"; null -> if (settled) "Estado indisponível" else "Consultando…" }
val subtitle = when {
    !settled -> null
    !remote -> REMOTE_HINT
    open == true -> "Pressione e segure para trancar a porta."
    open == false -> "Pressione e segure para abrir a porta."
    else -> null
}
```

`LockCircle` (pressione e segure 600 ms):

```kotlin
/** Círculo grande da fechadura. Segurar 600 ms abre a confirmação: o padrão da plataforma é ~400–500 ms,
 *  por isso uma ViewConfiguration própria SÓ neste nó (delegação de interface com `by`). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockCircle(open: Boolean?, consulting: Boolean, enabled: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val base = LocalViewConfiguration.current
    val longPress = remember(base) { object : ViewConfiguration by base { override val longPressTimeoutMillis: Long get() = 600L } }
    CompositionLocalProvider(LocalViewConfiguration provides longPress) {
        Box(
            Modifier.size(280.dp).clip(CircleShape).background(MiboColors.LockCircle)
                .border(14.dp, MiboColors.LockRing, CircleShape)
                .combinedClickable(enabled = enabled, onLongClick = onLongPress, onClick = onTap)
                .semantics {
                    role = Role.Button
                    contentDescription = if (open == true) "Pressione e segure para trancar a porta" else "Pressione e segure para abrir a porta"
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                consulting -> CircularProgressIndicator(Modifier.size(56.dp), color = MiboColors.Green, strokeWidth = 4.dp)
                open == true -> Icon(Icons.Default.LockOpen, null, Modifier.size(72.dp), tint = MiboColors.Green)
                else -> Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = Color.Black)
            }
        }
    }
}
```

Uso: `onTap = { snack(subtitle ?: stateText) }`; `onLongPress = { when { !settled -> snack("Consultando…"); !remote -> snack(REMOTE_HINT); open == null -> snack("Estado indisponível"); else -> confirm = !open } }`; `enabled = !sending`.

Confirmação (ação física real: sempre confirmar):

```kotlin
confirm?.let { wantOpen ->
    AlertDialog(
        onDismissRequest = { confirm = null }, containerColor = MiboColors.Card,
        title = { Text(if (wantOpen) "Destrancar a fechadura?" else "Trancar a fechadura?") },
        text = { Text("O comando será enviado para ${lock.name} pela nuvem Intelbras.") },
        confirmButton = { TextButton(onClick = {
            confirm = null; sending = true
            scope.launch {
                vm.setLock(lock, wantOpen).onSuccess { error = null }.onFailure { error = "Comando falhou: ${vm.messageOf(it)}" }
                sending = false
                reload++ // relê o estado REAL depois do comando
            }
        }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
    )
}
```

`VolumeControls` (RF06): `change(level)` → `vm.setLockVolume` (o ViewModel chama o `ChangeLockVolumeUseCase`: grava, relê, fallback) → sucesso `update { it.copy(volume = applied, volumeError = null) }` + "Volume alterado para ${applied.label}"; falha "Não foi possível alterar o volume: …". `reread()` → `vm.lockVolume` → falha `update { it.copy(volume = null, volumeError = vm.shortMessageOf(e)) }`. UI: "Volume indisponível ($volumeError)" + "Tentar de novo" quando a leitura falhou; `SingleChoiceSegmentedButtonRow` Mudo/Baixo/Médio/Alto **habilitado mesmo sem leitura** (`enabled = details != null && !changing`); `LinearProgressIndicator` enquanto grava/relê.

`HistoryCard` (RF09): `fetch(more)` com guarda `if (fetching) return`; estados na ordem: carregando → erro ("Histórico indisponível: …" + "Tentar de novo" repetindo o último pedido) → leitura inteira falhou ("Histórico indisponível" + "Tentar de novo" buscando só o histórico) → vazio (ChatBubble 72 dp + "Ainda não há registros de abertura") → lista (`event.description` + `event.time`, já formatados em `data/gdi`) → "Ver mais" quando `history.size >= 10 && !expanded`. Pílula com `todayDayMonth()` ("dd-MM").

`platform/WeekStrip.kt` (lógica pura em common, calendário nativo):

```kotlin
data class WeekDay(val initial: String, val day: String, val isToday: Boolean)

/** De hoje−6 até hoje+1 (a faixa do app Mibo termina em "amanhã"). */
val WEEK_STRIP_OFFSETS: IntRange = -6..1

/** Iniciais em português a partir do dia ISO (1 = segunda … 7 = domingo). */
fun weekInitial(isoDayOfWeek: Int): String = when (isoDayOfWeek) { 1, 5, 6 -> "S"; 2 -> "T"; 3, 4 -> "Q"; 7 -> "D"; else -> "?" }

/** Pura para ser testada com um calendário falso: recebe funções que respondem o dia do mês e o dia ISO por deslocamento. */
fun buildWeekStrip(dayOfMonthAt: (offset: Int) -> Int, isoDayOfWeekAt: (offset: Int) -> Int): List<WeekDay> =
    WEEK_STRIP_OFFSETS.map { offset -> WeekDay(weekInitial(isoDayOfWeekAt(offset)), dayOfMonthAt(offset).toString().padStart(2, '0'), offset == 0) }

expect fun weekStrip(): List<WeekDay>
/** Hoje em "MM-dd" (abas do hub). */
expect fun todayMonthDay(): String
/** Hoje em "dd-MM" (pílula do histórico). */
expect fun todayDayMonth(): String
```

- Android: `java.time.LocalDate` (`plusDays`, `dayOfWeek.value` já é ISO; `DateTimeFormatter.ofPattern("MM-dd")`) — exige minSdk 26.
- iOS: `NSCalendar.currentCalendar` (`dateByAddingUnit(NSCalendarUnitDay, …)`; `NSCalendarUnitWeekday` é 1 = domingo, converta para ISO com `if (w == 1) 7 else w - 1`) e `NSDateFormatter` com `locale = NSLocale(localeIdentifier = "en_US_POSIX")` (evita que a preferência regional do iPhone mude o padrão).

`HubScreen`: cabeçalho cinza ~42 % com canto inferior esquerdo arredondado, nome grande, Wi-Fi + `onlineLabel`, `HubDrawing` grande, botões redondos "Dispositivos" (aba Acessório) e "Firmware" (diálogo com `vm.firmware(hub)`); abas Acessório/Mensagens + `todayMonthDay()`; `WeekStrip`; Acessório = lista dos filhos (`parentNs == hub.ns`) + `FirmwareRow`; Mensagens = ChatBubble + "Sem eventos" + "Eventos em tempo real chegam por webhook e exigem backend".

**Testes.** Já cobertos em M3/M5 (`readsLockStateAndSendsControlCommand`, `readsAndWritesLockVolume`, `volumeServerErrorBecomesNullWithExplanationInDetails`, `lockCommandsDoNotRequireDataField`, `historyRequestsThirtyItemsOnVerMais`, `historyServerErrorIsReportedSeparatelyFromEmpty`, `lockOperationsDelegateToTheLockOwner`) e `MiboLogicTest` (faixa da semana).

**Pronto quando.** No aparelho: fechadura mostra "Porta fechada", bateria (âmbar < 30 %), Online, "Pressione e segure para abrir a porta."; segurar 600 ms abre a confirmação — **toque em Cancelar** (não confirme sem autorização do usuário, regra 0.2.5); cartão Volume mostra "Volume indisponível (a API respondeu HTTP 500)" com os níveis habilitados (**não toque nos níveis** sem autorização); histórico com eventos reais e "Ver mais"; hub com faixa da semana (hoje em verde), 1 subdispositivo, firmware 2.4.628243 "Atualizado".
**Lado a lado com `fechadura-mfr2030.jpg`:** no Android físico, rode `adb exec-out screencap -p > tela.png` na tela da fechadura e compare. Têm de coincidir: degradê verde-claro no topo indo para o cinza-claro; voltar, nome em negrito, compartilhar e configurações; linha com ícone de bateria e "NN%" (âmbar abaixo de 30 %) | Wi-Fi e estado; círculo grande central com anel cinza-esverdeado grosso e cadeado preto no meio; "Porta fechada" grande em negrito e "Pressione e segure para abrir a porta." em cinza; cartão branco arredondado com dois círculos de ação (verde e azul) e rótulos embaixo; cartão branco de histórico com título em negrito + seta e pílula cinza de data "dd-MM". Diferenças aceitas: Volume (RF06) e Histórico (RF09) no lugar de "Gerenciamento de usuários"/"Senhas temporárias"; "Online"/"Offline" no lugar de "Excelente"; título "Histórico de abertura" com os eventos reais no lugar de "Todas as Mensagens"/"Ainda não há mensagens"; pílula de data sem as setas ◀ ▶ (o histórico da GDI pagina por quantidade, não por dia).
**Lado a lado com `hub-mca1002.jpg`:** mesma captura na tela do hub. Têm de coincidir: cabeçalho cinza ocupando ~42 % da altura com o canto inferior esquerdo bem arredondado; voltar, nome centralizado e configurações; nome grande em duas linhas à esquerda com Wi-Fi e estado abaixo; desenho do hub (disco branco com anel e botão central) à direita; dois botões redondos brancos com ícone verde e rótulo cinza embaixo; abas "Acessório" | "Mensagens" (selecionada em negrito com sublinhado verde) e a data "MM-dd" à direita; faixa de 8 dias com inicial e número, hoje num quadrado verde com texto branco; vazio de Mensagens com balão cinza e "Sem eventos". Diferenças aceitas: "Dispositivos"/"Firmware" no lugar de "Modo Desarmar"/"Sirene silenciada" (inexistentes na GDI); hub desenhado em `Canvas` sem logotipo; "Online" no lugar de "Excelente"; dias da semana atual.

---

### M10 — Polimento: ciclo de vida, segurança, robustez

**Objetivo.** Fechar as pontas de ciclo de vida, segurança e robustez que aparecem ao usar o app no aparelho.

Checklist (faça e verifique cada item no aparelho):

1. **Rotação** (Android) em todas as telas: aba, modo grade/lista, lente, tela cheia e mudo sobrevivem (`rememberSaveable`); a validação do token continua (ViewModel).
2. **Morte de processo**: Opções do desenvolvedor → "Não manter atividades", ou `adb shell am kill br.com.pompeo.casa` com o app em background. Ao voltar: token restaurado do Keystore, destino inicial correto, lista recarrega sozinha.
3. **Background no vídeo**: Home do sistema durante o ao vivo → stream para (Android `ON_STOP`); ao voltar, reconecta. No iOS o player só para no dispose: registre no README (Próximos passos) como melhoria (usar `LocalLifecycleOwner` também no iOS).
4. **URL expirada**: a reconexão em `ON_START` usa a mesma URL; depois de muito tempo em background a URL pode ter expirado → o erro aparece com "Tentar novamente" (que pede sessão nova). Documente como limitação conhecida.
5. **Sem logs de token**: `grep -rn "println\|Log\.\|NSLog\|print(" shared/src iosApp/iosApp` deve vir vazio (ou só logs sem dado sensível); nenhum plugin `Logging` no Ktor.
6. **Backup off**: `android:allowBackup="false"` presente.
7. **Release sem token**: `./gradlew :androidApp:assembleRelease -PgdiDevToken=false` e confira que `shared/build/generated/gdi/.../GdiBuildConfig.kt` tem `TOKEN = ""`.
8. **Erros reais**: token expirado (espere 2 h ou use um antigo) → mensagem de expirado na tela de token e "Trocar token" na Home; modo avião → "Sem conexão…"; rede lenta → "demorou a responder".
9. **Debug × release**: `AppError.detail` só aparece em debug (`isDebugBuild`).
10. **Tamanho do APK**: anote (~165 MB em debug com 3 ABIs, por causa do libVLC); próximos passos: AAB/ABI splits, tirar `x86_64` do release.
11. **Licença**: libVLC/VLCKit são **LGPL 2.1+** — anote no README (tela de licenças e revisão jurídica, sobretudo no iOS).
12. **README.md** final: o que é, como rodar (JAVA_HOME, `local.properties`, Android Studio/Xcode, Team), arquitetura em 10 linhas (KMP + Compose Multiplatform, MVVM + Clean Architecture, regra de dependência, os dois casos de uso e por que só eles), decisões (Ktor manual `text/plain`, libVLC/VLCKit, Keystore direto, Koin, providers), limitações honestas (comandos físicos não executados, volume 500, mesma imagem nas lentes, cold start do proxy, sem eventos, token em `const val` só para demo), e os comandos de teste.
13. **Seção "Produto e arquitetura" no README** (é o conteúdo do PDF que o case pede; o usuário exporta): problema e público; os 9 RF com o estado de cada um (feito / feito sem execução física); diagrama de camadas da seção 4.1; múltiplos parceiros (`DeviceProvider` + `getAll`); modularização atual e o plano de divisão (nota da seção 1.3); interop com Java no `androidMain` e por que não há `.java` (nota da seção 1.3); segurança do token; contrato real × Swagger (resumo da seção 3); métricas medidas (tempo até o 1.º quadro, a frio e a quente); próximos passos. Sem token, sem número de série real, sem cópia do enunciado.
14. **Seção "Uso de IA" no README** (curta; o case permite IA e valoriza o registro do desenvolvimento orientado por especificação): qual especificação guiou o trabalho (marcos M1–M12 com critérios de pronto, contrato da API medido, capturas de referência) e como cada marco foi verificado (testes unitários, builds Android/iOS, CI, roteiro em aparelho e conferência lado a lado com as capturas). Sem colar este prompt nem o enunciado.

**Pronto quando.** Todos os 14 itens conferidos.

---

### M11 — CI (GitHub Actions)

**Arquivo.** `.github/workflows/ci.yml`:

```yaml
name: CI
on: [push, pull_request]
permissions:
  contents: read   # menor privilégio: o CI só lê o código

jobs:
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'   # só para iniciar o wrapper; o daemon usa o toolchain 25 (foojay)
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: android-debug
          path: androidApp/build/outputs/apk/debug/*.apk
  ios:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :shared:iosSimulatorArm64Test
      - run: >
          xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp
          -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
          -resolvePackageDependencies
      - run: >
          xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp
          -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
          CODE_SIGNING_ALLOWED=NO build
```

Sem `local.properties` no CI → `TOKEN = ""`; **nenhum segredo** é necessário. Se o runner macOS não tiver o Xcode necessário para o SDK 37/AGP, fixe a versão com `maxim-lobanov/setup-xcode@v1` e registre no README.

**Pronto quando.** Push num branch e os dois jobs verdes no GitHub (peça ao usuário para criar o repositório remoto e autorizar o push; não publique nada sem autorização).

---

### M12 — Verificação em aparelho real (roteiro RF01–RF09)

Prepare: apague os dados do app (`adb shell pm clear br.com.pompeo.casa`; no iPhone, apague e reinstale), gere um token novo no portal (Contas → Token Temporário) e coloque-o em `local.properties` **ou** cole na tela. Anote data/hora, aparelho e resultado de cada linha na seção "Verificação em aparelho" do README.

| # | RF | Passos | Deve aparecer |
|---|---|---|---|
| 1 | RF01 | Abrir o app sem dados | "Conectar à conta Intelbras", ícone de escudo verde, campo "Token de acesso" mascarado; se veio do `local.properties`, "Pré-preenchido pelo local.properties (desenvolvimento)" |
| 2 | RF04 | "Entrar" com um token **expirado** | "Seu token expirou (ele vale cerca de 2 horas)…", sem sair da tela |
| 3 | RF04 | "Entrar" com um texto qualquer de ≥ 8 caracteres | "Token inválido ou expirado. Gere um novo token…" |
| 4 | RF01/RF02 | "Entrar" com o token válido | "Validando token…" no botão → Home com iM4 Dual (badge 2), MFR 2030 (via hub), MCA 1002 |
| 5 | RF01 | Fechar e reabrir o app | Vai direto para a Home (token do Keystore/Keychain) |
| 6 | RF07 | Chips Compartilhados → Vinculados → Todos | Compartilhados: "Nenhum dispositivo encontrado" + "Nenhum dispositivo compartilhado com esta conta."; os outros: 3 dispositivos |
| 7 | RF08 | Definições → Itens por página 2 → Início → "Carregar mais" | "1 página(s) · 2 dispositivo(s) · 2 por página" + "Carregar mais" → "2 página(s) · 3 dispositivo(s) · 2 por página" + "Sem mais informações" |
| 8 | RF04/RF08 | (a) Modo avião → Definições → Atualizar → Início. (b) Sem modo avião, 2 por página e só a 1.ª página carregada → ligue o modo avião → "Carregar mais" | (a) Cartão "Sem conexão com a internet…" com "Tentar novamente"; a lista anterior não some das telas de detalhe. (b) Os 2 itens continuam na tela; abaixo, "Sem conexão…" + "Tentar novamente" (que, sem modo avião, carrega a página 2) |
| 9 | RF03 | Abrir a iM4 Dual (a frio) | % e etapa sobem; depois "AO VIVO" e "1º quadro em ~21 s" (a frio) |
| 10 | RF03 | Voltar e abrir de novo | "1º quadro em ~3 s" |
| 11 | RF03 | "Lente fixa" | Reinicia (~3 s) e mostra "A API GDI devolve o mesmo vídeo para as duas lentes desta câmera." |
| 12 | RF03 | Tela cheia, mudo, PTZ, gravar, microfone, foto | Tela cheia não reinicia o vídeo; mudo alterna (sem efeito audível: áudio desativado); os demais mostram Snackbar "Indisponível na API GDI"/"PTZ não disponível na API GDI" |
| 13 | RF05 | Abrir a MFR 2030 | "Porta fechada", bateria %, Online, "Pressione e segure para abrir a porta." |
| 14 | RF05 | Segurar o círculo 600 ms | Diálogo "Destrancar a fechadura?" → **Cancelar** (confirmar só com autorização do dono da conta) |
| 15 | RF06 | Cartão Volume | "Volume indisponível (a API respondeu HTTP 500)" + "Tentar de novo"; Mudo/Baixo/Médio/Alto habilitados (**não tocar** sem autorização) |
| 16 | RF09 | Histórico | Eventos "Remoto (APP)"/"Por dentro (manual)" com data/hora; "Ver mais" se houver 10 |
| 17 | — | Hub MCA 1002 | Online, faixa da semana com hoje em verde, 1 subdispositivo, "Firmware 2.4.628243" "Atualizado" |
| 18 | — | Definições → Sair | Volta à tela de token; "voltar" do sistema não retorna à Home |
| 19 | — | Repetir 4, 9, 13 e 17 no iPhone | Mesmo comportamento; vídeo via VLCKit (1.º quadro a frio pode passar de 30 s) |

**Passo final — conferência visual.** No Android físico, capture cada tela com `adb exec-out screencap -p > tela-<nome>.png` e compare lado a lado com a referência correspondente (mapa da seção 6.0): Home da linha 4 × `home.jpg`; câmera carregando e ao vivo da linha 9 × `camera-carregando.jpg` e `camera-ao-vivo.jpg`; PTZ aberto da linha 12 × `camera-ptz.jpg`; fechadura da linha 13 × `fechadura-mfr2030.jpg`; hub da linha 17 × `hub-mca1002.jpg`. A tela de token (linha 1) e as Definições (linha 7), que não têm captura, são conferidas contra as seções 6.3 e 6.2 e as cores da 6.1. Repita no iPhone (botões laterais para capturar). Toda diferença tem de estar entre as aceitas na seção 6.0 (recurso inexistente na GDI ou conteúdo de outra conta); qualquer outra é defeito a corrigir antes da entrega.

Capture as telas finais (`adb exec-out screencap -p > docs/telas/android-<tela>.png`; iPhone: botões laterais) para o README.

---

## 6. Especificação visual Mibo Smart

**Antes de codar a UI**, confira que a pasta `design/` na raiz do projeto (fora do Git: são telas de um app de terceiros com dados da conta de teste) tem exatamente estes arquivos:

```
design/home.jpg                    captura do app Mibo Smart
design/camera-carregando.jpg       captura do app Mibo Smart
design/camera-ao-vivo.jpg          captura do app Mibo Smart
design/camera-ptz.jpg              captura do app Mibo Smart
design/fechadura-mfr2030.jpg       captura do app Mibo Smart
design/hub-mca1002.jpg             captura do app Mibo Smart
design/app-*.png                   prints do resultado esperado (telas que o Mibo não mostra; lista na 6.0)
```

As capturas `*.jpg` mostram **como a tela deve parecer**. Os prints `app-*.png` mostram **o resultado esperado** deste app nas telas e estados sem captura do Mibo (token, erros, Definições, filtro vazio, paginação, volume…). Se alguma imagem faltar, pare e peça ao usuário; não reconstrua a tela de memória.

### 6.0 Mapa das capturas de referência

| Imagem | Tela | O que a imagem mostra | Marco | O que pode diferir |
|---|---|---|---|---|
| `home.jpg` | Casca + aba Início (6.2, 6.4) | Fundo cinza-claro; "Minha casa ⌄" com lupa e "+" em círculo verde; banner verde vivo "Armazenamento Complementar por Fotos" com pílula escura "Contratar agora"; cartão branco "Selecione suas cenas preferidas" com chevron; "Meus dispositivos" com barrinha verde e ícones grade/lista; grade de 2 colunas: câmera (nome em peso normal, sem negrito, + "••", miniatura com badge "2" e play), fechadura e hub (bolinha online verde-água, imagem do aparelho, nome em negrito); "Sem mais informações" entre traços; FAB quadrado verde; barra inferior branca com Início verde. | M7 | Chips de filtro e contagem de paginação (RF07/RF08); desenhos no lugar das fotos de produto; miniatura da câmera desenhada; ícones do banner e do cartão de cenas; nomes da conta. |
| `camera-carregando.jpg` | Câmera, carregando (6.5) | Barra branca com voltar, nome da câmera e ícones assistente/compartilhar/configurações; faixa de vídeo cinza-escura de borda a borda com badge "1/1", spinner verde e "35%" no centro, Multiview e Tela cheia embaixo à direita; pílula PTZ verde esmaecida na borda esquerda; barra inferior com 4 ícones cinza (mudo, gravar, microfone, foto) e o 5.º (tela cheia) já preto. | M8 | Texto da etapa abaixo do %. |
| `camera-ao-vivo.jpg` | Câmera, vídeo tocando (6.5) | Mesma barra; vídeo da câmera Dual com as duas lentes empilhadas ("Lente móvel" em cima, "Lente fixa" embaixo, OSD com data/hora da própria câmera); badge "1/1" ainda no canto superior esquerdo; chip Wi-Fi "0KB/s" no topo direito; Multiview e Tela cheia embaixo à direita; pílula PTZ verde cheia; ícones da barra inferior pretos. | M8 | Um player por vez + seletor "Lente móvel \| Lente fixa"; chip "AO VIVO" e "1º quadro em X s" no lugar de "0KB/s"; imagem do vídeo da conta. |
| `camera-ptz.jpg` | Câmera com PTZ aberto (6.5) | Vídeo ao vivo; no lugar da pílula, d-pad: círculo verde com quatro setas brancas e centro branco, botão × escuro no canto superior direito; sobre o vídeo, só os rótulos das lentes e o OSD (sem badge, chip nem ícones); barra inferior preta. | M8 | As setas mostram o Snackbar "PTZ não disponível na API GDI"; badge "1/1", chip "AO VIVO" e ícones Multiview/Tela cheia continuam sobre o vídeo com o PTZ aberto. |
| `fechadura-mfr2030.jpg` | Fechadura (6.6) | Degradê verde-claro no topo; voltar, "MFR 2030-…" em negrito, compartilhar e configurações; bateria "26%" (ícone âmbar) \| Wi-Fi "Excelente"; círculo grande com anel cinza-esverdeado e cadeado preto; "Porta fechada" + "Pressione e segure para abrir a porta."; cartão branco com dois círculos de ação (verde-água com ícone de pessoa, azul com cadeado) e rótulos embaixo; cartão branco "Todas as Mensagens ⌄" com pílula cinza de data "05-10" (dd-MM) entre setas ◀ ▶; vazio com balão cinza e "Ainda não há mensagens". | M9 | Volume (RF06) e Histórico (RF09) no lugar de "Gerenciamento de usuários"/"Senhas temporárias"; "Online/Offline" no lugar de "Excelente"; "Histórico de abertura" com eventos reais; pílula de data sem as setas (o histórico da GDI pagina só por quantidade, não por dia); cartão Volume expansível. |
| `hub-mca1002.jpg` | Hub (6.7) | Cabeçalho cinza (~42 % da altura) com canto inferior esquerdo arredondado; voltar, nome centralizado, configurações; nome grande em duas linhas à esquerda, Wi-Fi + "Excelente"; hub (disco branco) à direita; dois botões redondos brancos (escudo verde, alto-falante mudo cinza) com rótulo cinza; abas "Acessório" \| "Mensagens" (sublinhado verde) e data "10-05"; faixa de 8 dias com hoje em quadrado verde; vazio "Sem eventos" com balão cinza. | M9 | "Dispositivos"/"Firmware" no lugar de "Modo Desarmar"/"Sirene silenciada"; hub desenhado em `Canvas`; "Online" no lugar de "Excelente"; dias da semana atual. |

**Prints do resultado esperado (`design/app-*.png`).** Foram tirados num Android físico com o app pronto e a API real. Use-os para as telas e estados que o Mibo não mostra:

| Print | Tela / estado | Marco | Requisito |
|---|---|---|---|
| `app-01-token.png` | Tela de token: escudo verde, "Conectar à conta Intelbras", texto de onde gerar o token, campo "Token de acesso" mascarado com olho, aviso de pré-preenchimento, botão verde "Entrar", rodapé sobre o domínio | M6 | RF01 |
| `app-02-token-expirado.png` | Mesma tela depois de "Entrar" com token vencido: mensagem "Seu token expirou (ele vale cerca de 2 horas). Gere um novo no portal…" em vermelho abaixo do campo | M6 | RF04 |
| `app-03-home-grade.png` | Início em grade, filtro Todos: cartões da câmera (badge "2", play), fechadura ("via hub") e hub, FAB verde | M7 | RF02, RF07 |
| `app-04-home-lista.png` | Início em lista (ícone de lista ativo): uma linha por dispositivo com miniatura/ícone, nome, modelo, bolinha online e chevron | M7 | RF02 |
| `app-05-filtro-compartilhados-vazio.png` | Chip "Compartilhados" selecionado: cartão vazio "Nenhum dispositivo encontrado" + "Nenhum dispositivo compartilhado com esta conta." + "Atualizar" | M7 | RF04, RF07 |
| `app-06-paginacao-carregar-mais.png` | 2 itens por página: dois cartões, botão "Carregar mais" e "1 página(s) · 2 dispositivo(s) · 2 por página" | M7 | RF08 |
| `app-07-paginacao-fim.png` | Depois de "Carregar mais": três cartões, "Sem mais informações" entre traços e "2 página(s) · 3 dispositivo(s) · 2 por página" | M7 | RF08 |
| `app-08-definicoes.png` | Definições: cartão "Conta Intelbras" (parceiro, token mascarado — coberto no print —, "Atualizar", "Sair") e "Itens por página" 2 · 5 · 10 · 50; abaixo, a lista de opções | M7 | RF01, RF08 |
| `app-09-camera-carregando.png` | Câmera abrindo: "1/1", spinner verde, "29%" e "Solicitando sessão à Intelbras"; seletor "Lente móvel \| Lente fixa"; ícones da barra inferior em cinza | M8 | RF03 |
| `app-10-camera-ao-vivo.png` | Vídeo tocando: chip "AO VIVO", "1º quadro em X s" embaixo à esquerda, ícones pretos | M8 | RF03 |
| `app-11-camera-lente-fixa.png` | "Lente fixa" selecionada e o aviso "A API GDI devolve o mesmo vídeo para as duas lentes desta câmera." | M8 | RF03 |
| `app-12-camera-ptz-aviso.png` | d-pad PTZ aberto e o Snackbar "PTZ não disponível na API GDI" depois de tocar numa seta | M8 | regra de honestidade |
| `app-13-camera-informacoes.png` | Diálogo de informações (ícone de configurações): tipo, modelo, número de série e ID (cobertos no print), origem, parceiro, versão, último contato, fluxo; botão "Entendi" | M8 | RF02 |
| `app-14-fechadura.png` | Fechadura: degradê verde, bateria, Online/Offline, círculo com cadeado, "Porta aberta/fechada", "Pressione e segure…", cartão Volume/Histórico, início do histórico | M9 | RF05, RF09 |
| `app-15-fechadura-volume.png` | Cartão Volume aberto: "Volume indisponível (a API respondeu HTTP 500)", "Tentar de novo" e os níveis Mudo · Baixo · Médio · Alto | M9 | RF06 |
| `app-16-fechadura-confirmacao.png` | Diálogo depois de segurar o círculo: "Trancar a fechadura?" (ou "Destrancar…"), texto sobre a nuvem Intelbras, "Cancelar" e "Confirmar" | M9 | RF05 |
| `app-17-fechadura-historico.png` | Cartão "Histórico de abertura" rolado: eventos "Remoto (APP)" com data e hora, botão "Ver mais" | M9 | RF09 |
| `app-18-hub-acessorio.png` | Hub, aba Acessório: cabeçalho cinza, Online, botões Dispositivos/Firmware, faixa da semana, subdispositivo e firmware | M9 | RF02 |
| `app-19-hub-mensagens.png` | Hub, aba Mensagens: "Sem eventos" + "Eventos em tempo real chegam por webhook e exigem backend" | M9 | — |
| `app-20-aba-inteligente.png` | Aba Inteligente: "Cenas \| Automações" e o vazio "Não há rotinas criadas por aqui." | M7 | — |
| `app-21-aba-mensagens.png` | Aba Mensagens: "Notificações da conta \| Novidades" e o vazio. **Ignore** o botão "Registrar alerta de teste" e o texto sobre alertas locais que aparecem no print: alertas simulados estão fora do escopo; aqui o vazio explica que eventos chegam por webhook | M7 | — |
| `app-22-aba-loja.png` | Aba Loja: título e um botão. **No print** o botão abre uma tela que não faz parte deste app; aqui ele mostra o Snackbar "Indisponível na API GDI" | M7 | — |

Os prints `app-*.png` foram feitos com a conta de teste à noite: imagem do vídeo, estado da porta, bateria e Online/Offline variam; o que vale é a **estrutura** da tela e os **textos**.

A única tela sem print nem captura é "Outros dispositivos" (sensores, lâmpadas): segue as cores da seção 6.1 e o layout das seções 6.2/6.7, no mesmo estilo claro (fundo `PageBg`, cartões brancos arredondados, verde nos destaques).

**Abra cada imagem antes de escrever a tela correspondente** e compare lado a lado ao fim de cada marco de UI (regra 0.2.7). Regras gerais: tema claro em todas as telas; **nenhuma imagem de produto Intelbras** (fechadura e hub desenhados com ícones e `Canvas`; banner com ícone `PhotoCamera`); regra de honestidade (Snackbar "Indisponível na API GDI" em todo controle sem rota); Snackbar por tela via `rememberSnack()`; diálogos brancos (`containerColor = MiboColors.Card`).

### 6.1 Cores (`ui/Theme.kt`, literal)

```kotlin
package br.com.pompeo.casa.ui

import androidx.compose.ui.graphics.Color

/** Paleta medida nas capturas do app Mibo Smart: tema claro, verde Intelbras. */
object MiboColors {
    val Green = Color(0xFF00A237) // sublinhado de títulos, abas, chips, botões
    val GreenNav = Color(0xFF1D9C45) // item selecionado da barra inferior, d-pad PTZ
    val GreenBright = Color(0xFF00D441) // banner da Home e o "+"
    val GreenSpinner = Color(0xFF188C41) // spinner do vídeo
    val OnlineDot = Color(0xFF16C59C) // bolinha "online" nos cartões
    val PageBg = Color(0xFFF8F8F8) // fundo das telas
    val Card = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF8A8F94)
    val Divider = Color(0xFFE6E8EA)
    val VideoBg = Color(0xFF484848)
    val HubHeader = Color(0xFFD1D1D1)
    val LockHeaderTop = Color(0xFFE1F6ED) // degradê até PageBg
    val LockRing = Color(0xFFCDE0DC)
    val LockCircle = Color(0xFFF3F9F7)
    val ActionGreen = Color(0xFF49B696)
    val ActionBlue = Color(0xFF6C8CE6)
    val Amber = Color(0xFFFFB300)
}
```

Cores avulsas: título do banner `0xFF0B5A2A`; pílula "Contratar agora" `0xFF1B2A22`; item não selecionado da barra `0xFF9A9A9A`; miniatura da câmera `0xFF2B2F33`; corpo da fechadura `0xFF2E3338`; anel/anel central do hub no cartão `0xFFD9D9D9`/`0xFFBFD9FF`; anel do hub grande `0xFFE3E3E3`; bolinha offline e ícone genérico `0xFFBDBDBD`; botão × do PTZ `0xFF3A3A3A`; pílula de data do histórico `0xFFF1F3F4`; ChatBubble dos vazios `0xFFE0E0E0`/`0xFFD9D9D9`.

Ícones: o pacote **extended** é obrigatório (`ViewInAr`, `AutoAwesome`, `ControlCamera`, `BatteryStd`, `SystemUpdate`, `Hub`, `Sensors`, `DevicesOther`, `CloudOff`, `ChatBubble`, `GridView`, `Fullscreen`, `History`, `LockOpen`, `Store`, `Security`). Use as versões **AutoMirrored** de `ViewList`, `VolumeUp`, `VolumeOff`, `KeyboardArrowLeft`, `KeyboardArrowRight`.

Insets: Android `enableEdgeToEdge` claro; iOS `ComposeView().ignoresSafeArea()`; cada cabeçalho aplica `statusBarsPadding()`; barra própria no `bottomBar` precisa de `navigationBarsPadding()`.

### 6.2 Casca (abas)

`Scaffold(containerColor = PageBg)` com:
- **Barra inferior** branca (`NavigationBar(containerColor = Card, tonalElevation = 0.dp)`): Início (`Home`), Inteligente (`Lightbulb`), Mensagens (`Email`), Loja (`Store`), Definições (`Settings`); selecionado `GreenNav`, demais `0xFF9A9A9A`, `indicatorColor = Color.Transparent` (tira a pílula do Material 3), rótulos 11 sp.
- **FAB** só na aba Início: quadrado 56 dp, raio 12, `GreenBright`, ícone `AutoAwesome` branco, cd "Adicionar dispositivo" → Snackbar **"Adicionar dispositivos: use o app Mibo Smart (indisponível na API GDI)"** (a API não cadastra dispositivos).
- `notice` do ViewModel vira Snackbar (token aceito mas não guardado).
- **Inteligente**: `Title("Minha casa ⌄")` com "+" (→ Snackbar "Cenas e automações: indisponível na API GDI"); cartão branco raio 24 com abas de texto "Cenas"/"Automações" e o vazio `Lightbulb` 110 dp `Divider` + "Não há rotinas criadas por aqui." (20 sp cinza).
- **Mensagens**: `Title("Mensagens")`; `ChatBubble` 96 dp + "Sem notificações" + 12 sp "A GDI envia eventos de movimento e de abertura por webhook, que exige um backend; este app não recebe eventos em tempo real." (nada de alertas simulados).
- **Loja**: `Title("Loja")` + "Catálogo indisponível na API GDI".
- **Definições**: `Title("Definições")`, `AccountCard` e uma lista branca ("Conta", "Serviços", "Galeria", "Preciso de ajuda", "Configurações"; cada linha `Settings` cinza + rótulo + `ChevronRight`, padding h16 v18, `HorizontalDivider`), toque → diálogo "$label: área demonstrativa, sem serviço de conta conectado.".
- **AccountCard** (raio 16, padding 16): "Conta Intelbras" 18 sp bold; nomes dos parceiros (12 sp cinza, `providerNames.joinToString(" · ")`); "Token: ${masked ?: "—"}"; `Button "Atualizar"` (enabled se há token) + `OutlinedButton "Sair"`; "Itens por página" + `SingleChoiceSegmentedButtonRow` 2·5·10·50 (ativo `Green` com texto branco, borda `Divider`).

### 6.3 Tela de token

```
Column(fillMaxSize, verticalScroll, padding(h=24, v=32), CenterHorizontally)
 ├ Spacer(32)
 ├ Icon(Security, 64dp, Green)
 ├ "Conectar à conta Intelbras" (26sp Bold, centro, top 16)
 ├ "Cole o token temporário gerado no portal Casa Inteligente (menu Contas → Token Temporário). O token fica guardado com criptografia neste aparelho." (cinza, centro, v16)
 ├ OutlinedTextField: label "Token de acesso"; singleLine; enabled=!validating; isError; PasswordVisualTransformation (ou None se visível);
 │   KeyboardType.Password + ImeAction.Done (Done = enviar); trailing Visibility/VisibilityOff ("Mostrar token"/"Ocultar token");
 │   supportingText "Pré-preenchido pelo local.properties (desenvolvimento)" quando suggested != null && token == suggested
 ├ Button(fillMaxWidth, top 16, enabled=canSubmit): validando → Row{ spinner 18dp branco + "Validando token…" } senão "Entrar"
 ├ [erro] error.userMessage (cor error, centro) + [debug] error.detail (11sp cinza)
 ├ Spacer(32)   // espaço fixo: weight não funciona em coluna rolável
 └ "O token nunca é enviado para fora do domínio api-casainteligente.intelbras.com.br." (11sp cinza, top 24)
```

### 6.4 Home (`home.jpg`)

```
Column(fillMaxSize, verticalScroll, padding(h=16))
 1 Linha: "Minha casa ⌄" (28sp Bold, weight 1) · IconButton(Outlined.Search, preto, "Pesquisar") alterna busca
          · círculo 32dp GreenBright com "+" branco (→ mesmo Snackbar do FAB)
 2 [buscando] OutlinedTextField "Buscar pelo nome" (busca LOCAL por nome; a GDI não tem rota de busca)
 3 Banner: Surface(raio 12, GreenBright, altura 150) Row(padding 16):
     "Armazenamento Complementar por Fotos" (weight 1.2, 17sp Bold, 0xFF0B5A2A) · Icon(PhotoCamera 64dp branco)
     · Column(weight 1, End){ "Mais registros para sua câmera" (branco 12sp) ; pílula 0xFF1B2A22 "Contratar agora" (branco 12sp SemiBold) }
     toque → "Planos de armazenamento: indisponível nesta demo"
 4 Spacer(16)
 5 Cartão branco raio 12, padding h16 v22: Icon(ViewInAr, Green) · "Selecione suas cenas preferidas" (cinza 15sp) · ChevronRight → aba Inteligente
 6 "Meus dispositivos" (24sp Bold) com barra Green 20×4dp raio 2 abaixo · GridView ("Ver em grade") / AutoMirrored ViewList ("Ver em lista"), verde = ativo
 7 Chips FilterChip Todos · Vinculados · Compartilhados (spacedBy 8): borda Divider; selecionado fundo Green, texto branco, ícone Check 18dp
 8 [Content.refreshing] LinearProgressIndicator fino (Green, track Divider) — a grade continua visível
 9 Grade 2 colunas (12dp) ou lista
10 [busca sem resultado] "Nenhum dispositivo com esse nome"
11 Estado: Loading → spinner Green + "Consultando dispositivos da conta…"
           Error → cartão: CloudOff 40dp (error), userMessage, [debug] detail, "Tentar novamente" + ("Trocar token" se TokenInvalid/TokenExpired)
           Empty → cartão: DevicesOther 40dp, "Nenhum dispositivo encontrado" (18sp) + subtexto 13sp:
                   ALL "Esta conta não tem dispositivos vinculados nem compartilhados."
                   LINKED "Nenhum dispositivo vinculado. Veja em Compartilhados ou Todos."
                   SHARED "Nenhum dispositivo compartilhado com esta conta."  + OutlinedButton "Atualizar"
           Content → rodapé de paginação (M7)
12 Spacer(88)   // o FAB não cobre o rodapé
```

Cartões:
- **CameraCard**: `Surface(onClick, Card, raio 12)` → linha (start/end/top 12, bottom 8) com nome 16 sp `FontWeight.Normal` (sem negrito, como em `home.jpg`; só fechadura/hub têm nome em negrito) 1 linha `softWrap=false` Ellipsis + "compartilhado" 11 sp cinza se SHARED, e "••" cinza à direita; `CameraThumb(fillMaxWidth, aspectRatio 1.6f)` com badge preto 70 % "2"/"1" (`lensCount`) no canto superior esquerdo (`RoundedCornerShape(bottomEnd = 8.dp)`) e play translúcido 44 dp.
- **DeviceCard**: `Surface(defaultMinSize(minHeight 150), Card, raio 12)` → `OnlineDot` 8 dp (OnlineDot/`0xFFBDBDBD`); `DeviceDrawing` centralizado (fechadura 34×64; hub `HubDrawing` 84 dp; outros ícone 48 dp cinza); nome 18 sp bold 1 linha; "via hub • compartilhado" 11 sp cinza.
- **Lista**: linha branca raio 12 com miniatura 72×45 (câmera `CameraThumb` sem badge, play 24 dp; outros ícone 28 dp), nome 16 sp bold, `deviceSubtitle` 12 sp cinza, `OnlineDot`, `ChevronRight`.

### 6.5 Câmera (`camera-carregando.jpg`, `camera-ao-vivo.jpg`, `camera-ptz.jpg`)

```
Scaffold(containerColor = fullscreen ? Black : White, bottomBar = if (!fullscreen) CameraToolbar)
 └ Column
    ├ [!fullscreen] Row(h8 v4): BackButton · nome (20sp, 1 linha) · AutoAwesome ("Assistente") e Share ("Compartilhar") → Snackbar GDI_UNAVAILABLE
    │               · Outlined.Settings ("Informações do dispositivo") → DeviceInfoDialog
    │  Spacer(48)
    ├ Box( fullscreen ? fillMaxSize.background(Black).clickable{ fullscreen = false } : fillMaxWidth.aspectRatio(16/9).background(VideoBg) )
    │   ├ CameraPlayer (se há sessão)
    │   ├ "1/1" (TopStart, padding 16, preto 70 %, raio 4, branco 14sp)
    │   ├ erro → Column(Center, preto 70 %, raio 8): texto branco + TextButton "Tentar novamente"
    │   │ senão carregando → spinner GreenSpinner 40dp + "NN%" (branco 18sp) + etapa (LightGray 12sp)
    │   ├ [playing] chip TopEnd (preto 50 %, raio 6): Wifi 14dp + "AO VIVO" (12sp)
    │   ├ [firstFrameMs] "1º quadro em X s" (BottomStart, preto 60 %, 11sp)
    │   └ BottomEnd: GridView ("Multiview" → "Multiview indisponível na API GDI") · Fullscreen ("Tela cheia"/"Sair da tela cheia")
    └ [!fullscreen]
       ├ [Dual] SingleChoiceSegmentedButtonRow(h16 v16) "Lente móvel" | "Lente fixa" (cores dos outros segmentos)
       ├ [Dual && channel == 1] SAME_STREAM_NOTE (12sp cinza, h16)
       ├ Spacer(weight 1)
       ├ PTZ: fechado = pílula GreenNav 90×60 dp, cantos direitos 30 dp, encostada à esquerda, ControlCamera branco 28 dp ("Abrir controle PTZ"),
       │      alpha 0,5 enquanto !playing (esmaecida como em camera-carregando.jpg);
       │      aberto = Box(220×210, start 24) com círculo 190 dp GreenNav, 4 setas brancas 28 dp ("Para cima/baixo/esquerda/direita"),
       │      centro branco 70 dp, × (círculo 0xFF3A3A3A 28 dp, "Fechar PTZ") no TopEnd; cada seta → "PTZ não disponível na API GDI"
       └ Spacer(16)
CameraToolbar: Surface(White) Row(navigationBarsPadding, v12, SpaceEvenly), ícones 28 dp, os quatro primeiros cinza enquanto carrega e pretos depois;
   Fullscreen sempre preto (em camera-carregando.jpg ele já está preto):
   VolumeOff/VolumeUp ("Alternar áudio", REAL) · Videocam ("Gravar") · Mic ("Áudio bidirecional") · PhotoCamera ("Capturar") → GDI_UNAVAILABLE
   · Fullscreen ("Tela cheia")
```

Diferenças aceitas em relação às capturas: o Mibo mostra as duas lentes empilhadas; o app usa **um player por vez** (cota e CPU de aparelho fraco) e troca pelo segmento; a referência mostra "0KB/s", o app mostra "AO VIVO" e o tempo até o 1.º quadro (métrica de desempenho); com o PTZ aberto, `camera-ptz.jpg` esconde badge, chip e ícones sobre o vídeo, e o app os mantém.

### 6.6 Fechadura (`fechadura-mfr2030.jpg`)

```
Box(fillMaxSize, Brush.verticalGradient(0f to LockHeaderTop, 0.45f to PageBg, 1f to PageBg))
 └ Column(verticalScroll, CenterHorizontally)
    ├ ScreenHeader(nome, 22sp Bold, statusBarsPadding) · Share → GDI_UNAVAILABLE · Outlined.Settings → diálogo (linhas extras: Bateria, Abertura remota)
    ├ Row(start 20, top 16): BatteryStd 20dp (Amber se < 30 %) "26%"/"Bateria —" · "|" · Wifi 20dp onlineLabel
    │   (a GDI só informa online/offline; o Mibo mostra "Excelente")
    ├ Spacer(28) · LockCircle 280 dp (anel LockRing 14 dp, interior LockCircle; Lock preto 72 / LockOpen Green / spinner)
    ├ Spacer(28) · stateText 28sp Bold · subtitle 14sp cinza · erro (error) · [falhou] TextButton "Tentar de novo" · [enviando] LinearProgressIndicator
    ├ Cartão de ações (raio 16, padding 20): círculos 56 dp ActionGreen (VolumeUp, "Volume") e ActionBlue (History, "Histórico");
    │   Volume expande (AnimatedVisibility) os controles; Histórico rola até o cartão de histórico
    ├ Cartão de histórico (raio 16): "Histórico de abertura" 18sp Bold + ExpandMore/ExpandLess · [spinner] · pílula 0xFFF1F3F4 "dd-MM"
    └ Spacer(24)
```

Diferença consciente: o Mibo tem "Gerenciamento de usuários" e "Senhas temporárias", fora do escopo do case; o app usa Volume (RF06) e Histórico (RF09). A pílula de data do Mibo tem setas ◀ ▶ para trocar de dia; o app mostra só a data de hoje, porque o histórico da GDI pagina por quantidade (seção 3.7), não por dia.

### 6.7 Hub (`hub-mca1002.jpg`)

```
Column(fillMaxSize)
 ├ Box(fillMaxWidth, fillMaxHeight(0.42f), HubHeader, RoundedCornerShape(bottomStart = 40.dp))
 │   ├ ScreenHeader(nome 20sp centralizado) · Outlined.Settings
 │   ├ Row(weight 1, h20): Column{ nome 34sp/38sp Bold maxLines 2 ; Wifi 24dp ; onlineLabel 14sp }
 │   │                     · HubDrawing(sizeIn(max 200dp).fillMaxHeight().aspectRatio(1f), ring 0xFFE3E3E3 3dp, centro 44/200, sombra 4dp)
 │   └ Row(bottom 16, SpaceEvenly): RoundAction(Devices, "Dispositivos") · RoundAction(SystemUpdate, "Firmware")
 │      RoundAction = Surface(CircleShape, White, shadowElevation 2dp, 88dp) + ícone Green 32dp + rótulo cinza 13sp
 ├ Abas "Acessório" | "Mensagens" (18sp; selecionada Bold + sublinhado Green 32×3dp) · Spacer · "MM-dd" 13sp cinza
 ├ WeekStrip: 8 colunas hoje−6…hoje+1; inicial 12sp cinza + dia 18sp; hoje em quadrado Green 48dp raio 8 com texto branco
 └ Conteúdo rolável (padding 16):
     Acessório: "Dispositivos conectados ao hub (N)" (16sp SemiBold) · linhas brancas raio 12 (ícone Green/cinza, nome, "modelo • online/offline")
                · "Nenhum subdispositivo" · FirmwareRow (SystemUpdate cinza, "Firmware x.y.z", "Atualização disponível" Amber / "Atualizado")
     Mensagens: ChatBubble 96dp 0xFFE0E0E0 · "Sem eventos" 20sp cinza · "Eventos em tempo real chegam por webhook e exigem backend" 12sp
```

Diferença consciente: os botões do Mibo são "Modo Desarmar" e "Sirene silenciada", inexistentes na GDI.

**Outros dispositivos**: `ScreenHeader` + cartão (raio 16) com ícone do tipo 72 dp (Green/cinza), `kind.label` 26 sp bold, `onlineLabel`; `FirmwareRow`.

---

## 7. Armadilhas conhecidas (sintoma → causa → solução)

1. **Resposta não vira JSON / `NoTransformationFoundException`** → a GDI serve JSON com `content-type: text/plain` → `bodyAsText()` + `Json.parseToJsonElement` (leniente); `ContentNegotiation` só para serializar o pedido.
2. **HTTP 500 na listagem** → faltou `origem` (os três campos são obrigatórios) ou o corpo saiu `{}` (DTO sem `encodeDefaults`) → montar o corpo com `buildJsonObject { put(...) }`.
3. **HTTP 400 "Parâmetro inválido para 'origem'"** → enviou singular/outro valor → `todos`/`vinculados`/`compartilhados`; e o fallback para `todos` + filtro no aparelho.
4. **HTTP 400 "Parâmetro inválido ou não declarado"** → `pagina = 0` → páginas começam em 1.
5. **Fechadura/bateria respondem erro ou 404 "Dispositivo não encontrado"** → `ns` puro → `ns` composto `NsFechadura_NsHub_IdProdutoHub` e `idProduto` da própria fechadura no corpo.
6. **Media3: `missing attribute fmtp`** → o RTSP da nuvem não tem `a=fmtp` no SDP (H.265 com parâmetros in-band, padrão RFC 7798) → libVLC no Android, VLCKit no iOS; não inclua `media3-exoplayer-rtsp` (com ele no classpath, URLs `rtsp://` caem no `RtspMediaSource`).
7. **libVLC conecta e a tela fica preta; logcat `OMX.qcom.video.decoder.hevc … Set Resolution failed` + `Could not find ref with POC` em loop** → decoder de hardware precisa da resolução no `configure`, que só viria do `fmtp`; o VLC cai para software no meio do fluxo → `setHWDecoderEnabled(false, false)`: software desde o 1.º pacote.
8. **Áudio vira ruído** → AAC sem `config` no SDP → `:no-audio` / `"no-audio": true` (o mudo fica sem efeito audível; documente no README).
9. **Espiral "more than 5 seconds of late video -> dropping frame" no aparelho de entrada** → buffer de 300 ms + `--clock-jitter=0` com H.265 por software → `LIVE_NETWORK_CACHING_MS = 800` e sem `clock-jitter` no Android (+~0,5 s de latência).
10. **ANR ao sair da tela do vídeo** → `MediaPlayer.stop()/release()` síncronos esperam o timeout RTSP na main thread → `detachViews()` na main e `stop/release/libVlc.release` numa `Thread("vlc-release")`. No iOS o `stop()` roda na main: risco igual, não observado; anote.
11. **iOS abre o vídeo muito mais devagar** → o VLC tentava VideoToolbox antes do software → `"codec": "avcodec"` além de `"avcodec-hw": "none"`.
12. **AVPlayer não toca** → AVPlayer não reproduz RTSP → VLCKit via SPM (interface Kotlin implementada em Swift).
13. **App iOS fecha ao abrir (PlistSanityCheck)** → faltou `CADisableMinimumFrameDurationOnPhone = true` no Info.plist → adicionar a chave (correção certa; não desligue a checagem).
14. **"Multiple commands produce …/Info.plist"** → Info.plist dentro da pasta sincronizada → mover para `iosApp/Info.plist` e apontar `INFOPLIST_FILE`.
15. **Fase do Gradle no Xcode falha com permissão negada** → sandbox de scripts → `ENABLE_USER_SCRIPT_SANDBOXING = NO`. **"Unable to locate a Java Runtime"** → exporte `JAVA_HOME` (seção 2.1).
16. **`No such module 'Shared'`** → fase Kotlin depois de Sources, ou `FRAMEWORK_SEARCH_PATHS` errado → fase como primeira; caminho `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
17. **Assinatura falha no iPhone** → usou o ID do certificado como Team → Team **AH3MDC3666** (nunca o XXXXXXXXXX do certificado); Team precisa estar no pbxproj.
18. **Interop Swift**: (a) exceção Kotlin que atravessa para o Swift derruba o app se a função não tiver `@Throws(Exception::class)` — por isso o contrato Swift não lança e erros viram callback `onError`; (b) **valores padrão não são exportados** ao Obj-C → sobrecarga `initKoin()` sem parâmetro; (c) **`Flow` não chega ao Swift** (use callbacks/listener); (d) genéricos são apagados; (e) `init…` vira `doInit…`; (f) `object` vira `.shared`; (g) `Int` vira `Int32`; (h) a classe Swift que implementa a interface precisa herdar de `NSObject`.
19. **Keystore**: não passe IV ao cifrar (o Keystore exige IV aleatório e gera; leia `cipher.iv`); `load(null)` antes de `getEntry`; não use `setUserAuthenticationRequired` (invalida a chave ao trocar o bloqueio e exige autenticação a cada leitura); leitura que falha apaga a entrada; `allowBackup=false` (a chave não migra num restore). minSdk 26 também é o que libera `java.time` sem desugaring.
20. **Keychain nos testes iOS** → `errSecMissingEntitlement (-34018)` no executável de teste → teste com `InMemoryTokenStorage`; Keychain só no app; `CFRelease` só do que você reteve.
21. **Token expirado não renova** → a renovação devolve 400 "Não foi possível renovar o token…" → mantenha o status original (403 → `TokenExpired`) e peça token novo.
22. **401 com corpo string** (`"Não autorizado"`) quebra o parser de objeto → corpo não-objeto vira `{"raw": …}` sem aspas.
23. **Comando da fechadura "falha" mesmo funcionando** → sucesso exigindo `data` → `successJson()` não exige `data`.
24. **Duas renovações para chamadas paralelas** (a 2.ª é recusada) → renovação serializada com `Mutex` e "token já mudou? só repete".
25. **Cold start do proxy (~21 s, player parado em 92 %)** → é a nuvem (mesmo padrão em qualquer app) → não há correção no app; mostrar progresso, etapa e o tempo medido; overlay não volta depois do 1.º quadro.
26. **Mesma imagem nas duas lentes** → a GDI devolve o mesmo RTSP para `canalVideo` 0 e 1 → enviar o canal certo e mostrar `SAME_STREAM_NOTE`. `canalVideo 0 + streamId 0` → 500 → `streamId = 1`.
27. **Sessão órfã ao trocar de lente/tentar de novo** → encerrar a sessão atual e zerar `session` antes de mudar `channel`/`liveAttempt`.
28. **"Job was cancelled" aparecendo como erro** → `runCatching` engole `CancellationException` → `resultOf { … }` (M2) no ViewModel, no repositório e no `ChangeLockVolumeUseCase`, e `catch (e: CancellationException) { throw e }` antes do `catch` genérico em `SubmitTokenUseCase`/`loadFirstPage`/`loadNextPage`/`fetchPage`. Únicos `runCatching` aceitáveis: acesso síncrono ao storage (`TokenRepositoryImpl`, `KeystoreTokenStorage`), parse de JSON e o `async` isolado do `GdiLockController.details` (comente o porquê no código).
29. **Teste do repositório trava ou não avança** → `advanceUntilIdle` não espera coroutines do `backgroundScope` → `advanceTimeBy(ms) + runCurrent()`. **`viewModelScope` falha na JVM** → `Dispatchers.setMain(StandardTestDispatcher())`.
30. **`String.format`/`AtomicInteger`/`kotlin.jvm.Volatile` não compilam em common** → montar string à mão; `MutableStateFlow` como contador atômico; `kotlin.concurrent.Volatile`.
31. **Grade com alturas diferentes** → `Row.height(IntrinsicSize.Max)` + `fillMaxHeight()` nos cartões. **Nome quebrando no meio no iOS** → `softWrap = false` + `Ellipsis`.
32. **Tela cheia recria o player** → usar o mesmo `Box` trocando só o `Modifier`, nunca um ramo `if` diferente.
33. **`onBack()` chamado durante a composição** quando o item some → chamar dentro de `LaunchedEffect(Unit)`.
34. **APK de ~165 MB** → libVLC traz um `.so` por ABI → `abiFilters`; em produção, AAB/ABI splits. **LGPL** do libVLC/VLCKit → tela de licenças.

---

## 8. Testes que devem existir no final (meta ≥ 70; esta lista soma 85)

Todos em `shared/src/commonTest/kotlin/br/com/pompeo/casa/`, `kotlin.test` + `kotlinx-coroutines-test` + `ktor-client-mock`, sem JUnit/MockK. Tokens sempre falsos.

**`TokenFormatTest` (3)**
1. `normalizeTrimsAndHandlesNull` — trim; null e espaços viram "".
2. `plausibleNeedsEightCharsWithoutInnerSpaces` — ≥ 8 após trim, sem espaço interno.
3. `maskShowsOnlyEdgesAndHidesShortTokens` — 5 + "…" + 4; < 12 ou null → "••••".

**`DomainModelTest` (4)**
4. `originParseAcceptsSingularPluralCaseAndSpaces`.
5. `originFilterAcceptsUnknownOrigin` — UNKNOWN nunca é descartado.
6. `devicePageHasMoreWhenFull`.
7. `lockVolumeFromLevel` — 0..3 e fora da faixa → null.

**`TokenRepositoryImplTest` (7)** — instancia `TokenRepositoryImpl` com `InMemoryTokenStorage`/`ThrowingTokenStorage`.
8. `loadsPersistedTokenAndIgnoresBlankSuggestion` — `masked()` = "Ot_pe…3456".
9. `suggestionIsNotALogin` — sugestão não preenche `token`.
10. `sessionStaysInMemoryUntilPersist` — `setSession` não grava; `persist` grava; `clear` grava null.
11. `persistedTellsWhetherTheSessionTokenIsStored`.
12. `storageFailureKeepsTheTokenInMemoryOnly` — `persist` devolve false; `clear` não lança; `writeAttempts == 2`.
13. `unreadableStorageStartsWithoutToken`.
14. `inMemoryStorageBehavesLikeRealOne`.

**`GdiApiTest` (18)**
15. `sendsBearerAndJsonBody` — Bearer + `pagina`, `tamanhoPagina`, `origem:"todos"`.
16. `providerSendsOriginFilterAndParsesPage` — `origem:"compartilhados"`, página cheia = `hasMore`, `providerId "gdi"`.
17. `renewsTokenOnce401AndRetries` — uma renovação, repetição, token novo persistido.
18. `parallelExpiredCallsRenewTheTokenOnlyOnce` — dois 401 paralelos, uma renovação.
19. `failedRenewalBecomesTokenInvalid` — renovação 400 mantém 401 → `TokenInvalid`.
20. `parsesStreamSessionAndSurfacesApiErrors` — forma do Swagger + 402 vira `GdiException(402)`.
21. `readsLockStateAndSendsControlCommand` — aberto/remoto/bateria/histórico; corpo do comando com `ns`, `aberto`, `idProduto`.
22. `readsAndWritesLockVolume` — 2 → MEDIUM; LOW envia `ns "LOCK1_HUB1_PHUB"`, `idProduto "PLOCK1"`, `volume 1`.
23. `volumeServerErrorBecomesNullWithExplanationInDetails` — `volumeError = "a API respondeu HTTP 500"`; histórico vazio não é erro.
24. `lockCommandsDoNotRequireDataField` — `{"status":"sucesso"}` ok; 200 + `status:"erro"` → `AppError.Server`.
25. `historyRequestsThirtyItemsOnVerMais` — `quantidade:30` com `ns` composto.
26. `historyServerErrorIsReportedSeparatelyFromEmpty` — 503 → `historyError`; volume 0 → MUTE.
27. `streamChannelIsSentAsCanalVideo` — 0 padrão, 1 pedido, sempre `streamId:1`.
28. `acceptsRealRtspOnlyStreamResponse` — `sessionId`/`monitorUrl` nulos; `endSession(null)` não chama a API.
29. `parsesJsonServedAsTextPlain` — regressão `text/plain`.
30. `failsFastWithoutToken` — `GdiNoTokenException` → `TokenMissing`.
31. `unauthorizedStringBodyBecomesTokenInvalid` — detalhe `"HTTP 401: Não autorizado"`, sem aspas.
32. `expiredTokenBecomesTokenExpired` — 403, mensagem exata, detalhe `"HTTP 403: Token expirado, por favor gere um novo token"`.

**`GdiDeviceParserTest` (5)**
33. `parsesEnvelopeWithPortugueseKeys` — `data.dispositivos`; `tipo:"lampada"` → LAMP; sem origem → UNKNOWN.
34. `parsesFlatArrayWithEnglishKeysAndStatusStrings` — dedup por ns; `status:"online"` → true; `"shared"` → UNKNOWN.
35. `unknownTypeAndModelIsTreatedAsCamera` — `online` null.
36. `ignoresObjectsWithoutSerial` — envelope de erro → lista vazia.
37. `parsesOriginValues` — singular/plural/maiúsculas/espaços; null/desconhecido → UNKNOWN.

**`GdiRealResponseTest` (2)**
38. `keepsCamerasLockAndHubFromRealListing` — 4 dispositivos, `online` de `status`, origens, câmeras CAM…1 e CAM…4.
39. `classifiesCameraLockAndHubAndBuildsSubdeviceNs` — hub pelo nome "MCA" com modelo `IOT-ZG2-IB`; `gdiApiNs` composto; `lastOnline` sai formatado ("20261002T192905Z" → "02/10/2026 19:29:05 UTC").

**`GdiErrorMapperTest` (5)**
40. `tokenErrors` — sem token → `TokenMissing`; 401 → `TokenInvalid`; 400 "renovar o token" → `TokenInvalid`.
41. `expiredTokenHasItsOwnExplanation` — 403 e "expirado" em qualquer status/caixa.
42. `networkAndTimeoutErrors` — `IOException`/`ConnectTimeoutException` → Network; `HttpRequestTimeoutException`/`SocketTimeoutException` → Timeout.
43. `serverAndUnexpectedErrors` — 503 → Server(503); 200 + envelope → Server(200); 402 → Unexpected; `IllegalStateException` → Unexpected com `detail`.
44. `shortMessageCitesHttpStatusOnlyForGdiErrors` — `GdiException(500)` → "a API respondeu HTTP 500"; `IOException` → mensagem de Network.

**`GdiFormatTest` (2)**
45. `dateTimeFormatsLocalAndUtcAndKeepsUnknownRaw`.
46. `lockEventTypeTranslatesKnownTypes` — "Remoto (APP)", "Por dentro (manual)", desconhecido "x • y", vazio "Desconhecido".

**`DeviceRepositoryImplTest` (17)** — instancia `DeviceRepositoryImpl` + `TokenRepositoryImpl(InMemoryTokenStorage())` + `FakeProvider`.
47. `withoutTokenStateIsNoToken`.
48. `persistedTokenLoadsFirstPageAtStartup` — `Loading` → `Content` com 5, `hasMore`, `DeviceQuery(ALL,1,5)`.
49. `loadMoreAccumulatesPagesAndStopsWhenPageIsShort` — 2.º toque ignorado; D1..D7; páginas `[1,2]`.
50. `loadMoreErrorKeepsListAndExposesError` — 503 na página 2 mantém os 5 + `loadMoreError` Server.
51. `emptyAndErrorStates` — vazio → `Empty`; `IOException` → `Error(Network)`.
52. `originFilterIsSentAndAppliedDefensivelyOnDevice` — remove LINKED, mantém UNKNOWN.
53. `fallsBackToAllWhenApiRejectsOriginValue` — origens `[LINKED, ALL]`, `note = DeviceRepositoryImpl.FILTER_ON_DEVICE_NOTE`.
54. `setPageSizeRestartsFromPageOne`.
55. `refreshWithContentKeepsListVisibleWhileRefreshing`.
56. `staleFirstPageIsNotPublishedAfterOriginChanged`.
57. `changingOriginCancelsLoadMoreInFlight` — páginas `[1,2,1]`, `loadingMore` false.
58. `originChangeCancelsRefreshEvenWhenLoadMoreWasRequestedRightAfter`.
59. `loadMoreDuringRefreshIsIgnored`.
60. `firstPageWhileLoadMoreInFlightClearsLoadingMoreAndDropsStalePage`.
61. `logoutDiscardsRefreshInFlight` — e o token sai da memória e do storage (`writes` termina em `null`).
62. `lockOperationsDelegateToTheLockOwner` — `setLockVolume` só grava (sem releitura); erro de escrita sobe; histórico pede 10/30; parceiro sem `locks` lança.
63. `startLivePassesLensChannelToProvider` — `[0,1,0]`; e operações delegam ao parceiro certo com dois providers (`providerName`).

**`SubmitTokenUseCaseTest` (8)** — só `FakeTokenRepository` + `FakeDeviceRepository`.
64. `implausibleTokenIsRejectedWithoutLoadingAPage` — "curto" → `TokenMissing`, `firstPageLoads == 0`.
65. `rejectedTokenIsNotPersistedAndLogsOut` — 1.ª página `Error(TokenInvalid)` → erro devolvido, `persistedValues` vazio, token null, `logouts == 1`.
66. `expiredTokenReportsExpiry` — `Error(TokenExpired)` → `TokenExpired`.
67. `acceptedTokenIsPersistedTrimmed` — "  Ot_valid_0001  " → `persistedValues == ["Ot_valid_0001"]`, retorno null.
68. `emptyAccountStillPersists` — 1.ª página `Empty` também é sucesso.
69. `persistsTheTokenRenewedDuringValidation` — a 1.ª página troca o token da sessão; persiste o novo.
70. `cancelledValidationLeavesNoUnvalidatedSessionToken` — cancelamento → token null, `NoToken`, nada persistido; o cancelamento é relançado.
71. `succeedsEvenWhenSecureStorageFails` — `FakeTokenRepository(canPersist = false)` → retorno null, `persisted == false`.

**`ChangeLockVolumeUseCaseTest` (3)** — `FakeDeviceRepository(FakeTokenRepository(), locks = FakeLockController())`.
72. `writesThenRereadsTheVolume` — devolve o volume relido.
73. `rereadFailureAssumesTheRequestedVolume` — releitura com erro (o HTTP 500 real) → devolve o pedido.
74. `writeFailureIsPropagatedWithoutReread` — erro de escrita sobe; volume não muda.

**`HomeViewModelTest` (5)** — fakes das interfaces + casos de uso reais + `FakeErrorMapper` (helper `homeViewModel`).
75. `validationRunsInViewModelScopeAndReportsDone`.
76. `rejectedTokenBecomesFailed`.
77. `acceptedTokenThatCouldNotBeStoredRaisesANotice`.
78. `clearingTheViewModelMidValidationDropsTheSessionToken`.
79. `startLiveRethrowsCancellation` — job cancelado não vira `Result.failure`.

**`MiboLogicTest` (5)**
80. `lensBadgeComesFromDualInModelOrName` — e `LENS_LABELS.size == 2`.
81. `weekInitialsFollowPortugueseNames` — S T Q Q S S D; 0 → "?".
82. `weekStripGoesFromSixDaysAgoToTomorrowWithTodayAtIndexSix` — calendário falso (segunda, 05/10): dias 29..06, iniciais T Q Q S S D S T, hoje no índice 6.
83. `onlineAndSharedLabels`.
84. `deviceSubtitleJoinsModelHubAndShared`.

**`CameraFormatTest` (1)**
85. `formatSecondsTruncatesToTenths`.

Comandos: `./gradlew :shared:testAndroidHostTest` (relatório em `shared/build/reports/tests/testAndroidHostTest/index.html`) e `./gradlew :shared:iosSimulatorArm64Test`.

---

## 9. Entrega

Checklist final (tudo marcado antes de passar o link ao usuário):

- [ ] **Repositório**: código no GitHub (o usuário cria o remoto e autoriza o push), `main` com commits por marco e CI verde nos dois jobs (M11). O link do repositório é o que vai no e-mail do case.
- [ ] **Build e testes verdes** a partir de um clone limpo, no Android e no iOS:
  ```sh
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test    # ≥ 70 testes (a lista da seção 8 tem 85)
  ./gradlew :androidApp:assembleDebug
  ./gradlew :androidApp:assembleRelease -PgdiDevToken=false
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
  ```
- [ ] **MVVM + Clean Architecture conferidas** (seção 4.1; os três comandos têm que vir vazios):
  ```sh
  SRC=shared/src/commonMain/kotlin/br/com/pompeo/casa
  # 1. domain sem framework nem camadas externas
  grep -rnE "^import (io\.ktor|org\.koin|androidx|org\.jetbrains\.compose|kotlinx\.serialization|br\.com\.pompeo\.casa\.(data|ui|di|player|platform))" "$SRC/domain"
  # 2. apresentação sem data (nem Ktor)
  grep -rnE "^import (br\.com\.pompeo\.casa\.data|io\.ktor)" "$SRC/ui" "$SRC/player" "$SRC/App.kt"
  # 3. ViewModel só com domain (interfaces de repositório, casos de uso, ErrorMapper, modelos)
  grep -n "^import br\.com\.pompeo\.casa\." "$SRC/ui/HomeViewModel.kt" | grep -v "br\.com\.pompeo\.casa\.domain\."
  ```
  Além disso: o construtor do `HomeViewModel` recebe só `DeviceRepository`, `TokenRepository`, `SubmitTokenUseCase`, `ChangeLockVolumeUseCase` e `ErrorMapper`; o `appModule` registra cada `*Impl` pela interface (`single<TokenRepository> { TokenRepositoryImpl(...) }`); nenhuma tela coleta estado sem `collectAsStateWithLifecycle`.
- [ ] **README.md** com: o que é; como rodar (JAVA_HOME, `local.properties`, Android Studio/Xcode, Team); arquitetura; decisões; limitações honestas; licença LGPL do VLC; verificação em aparelho (M12); a seção **"Produto e arquitetura"** (M10, item 13), pronta para o usuário exportar como o PDF do case; e a seção curta **"Uso de IA"** (M10, item 14).
- [ ] **Higiene do token**: `git grep -n "Ot_"` só mostra tokens falsos de teste; `local.properties` e `design/` fora do Git; `git log -p | grep -c "gdi\.token="` dá 0; nenhum `println`/`Log`/`NSLog` com dado sensível; nenhum plugin `Logging` no Ktor; o release com `-PgdiDevToken=false` tem `TOKEN = ""`.
- [ ] **Aparelho real**: roteiro RF01–RF09 do M12 conferido no Android físico **e** no iPhone, incluindo a conferência visual lado a lado com as seis capturas (seção 6.0).
- [ ] Nenhum item fora de escopo (seção 0.1) entrou no código.
- [ ] Nenhum comando físico foi enviado à fechadura sem autorização explícita do usuário.

O envio (e-mail com o PDF exportado do README + link do repositório) é feito pelo usuário.
