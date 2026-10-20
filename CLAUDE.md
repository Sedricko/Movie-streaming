# Lumicast — instruções para o Claude Code

Você está continuando o **Lumicast**: app Android (celular) que **abre vídeos do Telegram e os envia direto
para a TV** (Chromecast/Google TV, DLNA/UPnP e, depois, Roku/Fire TV/LG/Samsung), com design moderno.

Leia nesta ordem antes de codar: `docs/01-analise-dos-apks.md` → `docs/02-arquitetura.md` →
`docs/03-design-system.md` → `docs/04-roadmap.md` → `docs/05-riscos-e-conformidade.md` → `docs/06-especificacao-tecnica.md` (antes de M2–M4).

## Origem do projeto
Nasceu da análise estática de dois apps: **TvGram** (cliente Telegram para TV: TDLib + libVLC) e
**Web Video Caster** (casting multi-protocolo + servidor HTTP local). O Lumicast une:
- do TvGram → login Telegram via **TDLib**, streaming progressivo de arquivo ainda baixando, pastas/canais, retomar;
- do Web Video Caster → **descoberta de TVs** (SSDP/DLNA, Cast, Roku…), **servidor HTTP local com Range**,
  fila, legendas, notificação de controle.
Não copie código, assets ou marcas desses apps: eles são só referência de funcionalidades. Escreva tudo do zero.

## Decisão central (não mude sem conversar com o usuário)
"Espelhar" = **cast do vídeo** (a TV puxa o stream do celular pela rede local), NÃO espelhamento de tela.
Fluxo: `TDLib downloadFile (progressivo) → arquivo parcial → servidor HTTP local (Ktor, Range) → URL na LAN → TV`.
O celular vira controle remoto; pode bloquear a tela (foreground service mantém o servidor vivo).

## Stack
Kotlin 2.x · Jetpack Compose + Material 3 · Navigation Compose · Coroutines/Flow · Hilt (a adicionar no M1) ·
Ktor server (CIO) · TDLib (JNI oficial) · Google Cast SDK · jUPnP (DLNA) · Media3 (player local/prévia) ·
DataStore · minSdk 26 · compileSdk 35 · pacote `app.lumicast`.

## Estado atual (M0 concluído, nada compilado ainda!)
O esqueleto foi escrito SEM acesso à rede e SEM compilar. **Primeira tarefa: fazer `./gradlew assembleDebug`
e `./gradlew test` passarem**, corrigindo versões/imports. Depois siga `docs/04-roadmap.md`.
- UI completa em modo demonstração com dados falsos (`Fake*Gateway`).
- `RangeParser` + `RangeStreamServer` reais, com teste unitário.
- `TelegramGateway` e `CastGateway` são interfaces; as implementações reais (TDLib/Cast/DLNA) são os marcos M2–M4.

## Regras de trabalho
1. Um marco por vez; cada marco termina com build verde + testes + atualização do `docs/04-roadmap.md`.
2. Nada de segredo no repositório: `api_id`/`api_hash` do Telegram vão em `local.properties`
   (`TELEGRAM_API_ID`, `TELEGRAM_API_HASH`) lidos via `BuildConfig`. Crie `local.properties.example`.
3. Camadas: `ui` → `domain` (interfaces/modelos puros) ← `data`. A UI nunca importa TDLib/Cast diretamente.
4. Textos de interface em **português do Brasil** (`strings.xml`), frases curtas, verbo no infinitivo nos botões,
   erros dizem o que houve e como resolver (veja `docs/03-design-system.md`).
5. Siga o design system à risca (cores/tipografia/formas em `ui/theme`). Não introduza gradientes decorativos,
   cantos iguais em tudo nem ícones sem função.
6. Sem anúncios, sem rastreadores, sem analytics de terceiros no v1.
7. Teste em dispositivo real com TV na mesma rede Wi-Fi; emulador não descobre TVs. Quando não puder
   testar algo, diga isso explicitamente em vez de afirmar que funciona.
8. Verifique versões atuais de bibliotecas antes de fixá-las (o `libs.versions.toml` é uma estimativa).

## Comandos
```
./gradlew assembleDebug          # compilar
./gradlew test                   # testes unitários (RangeParserTest)
./gradlew installDebug           # instalar no aparelho
```
(O wrapper `gradlew` não está no repositório: gere com `gradle wrapper` ou abra no Android Studio.)
