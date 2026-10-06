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
