# 04 · Roadmap (marcos com critério de aceite)

Marque `[x]` ao concluir. Sempre: build verde + testes + atualizar este arquivo.

## M0 — Esqueleto ✅ (escrito, **não compilado**)
- [x] Projeto Gradle, tema, 4 telas em modo demonstração, `RangeParser`/`RangeStreamServer`, interfaces de domínio.
- [ ] **Compilar** (`assembleDebug`) e rodar `RangeParserTest` — primeira tarefa do Claude Code.

## M1 — Fundação
- [ ] Hilt, DataStore (preferências, última TV), navegação final, estados de UI (`Loading/Empty/Error`).
- [ ] Baixar e embutir fontes (Bricolage Grotesque, Figtree) em `res/font`; ícone adaptativo final.
- [ ] Strings extraídas para `strings.xml`; tema claro/escuro validado.
- **Aceite:** app abre no demo sem crash; telas fiéis ao design system; `./gradlew test` verde.

## M2 — Telegram real (TDLib)
- [ ] Integrar TDLib 1.8.x (JNI) — preferir **build oficial** (`td/example/android`) empacotado como módulo `:tdlib`,
      ou artefato Maven confiável (validar licença Boost e origem).
- [ ] `TdlibTelegramGateway`: estados de login (telefone → código → senha 2FA), `getChats`, pastas, `searchChatMessages`
      com `searchMessagesFilterVideo`, miniaturas, `getMe`, logout. `api_id/api_hash` via `BuildConfig`.
- [ ] `openStream(fileId)` com `downloadFile` progressivo e `StreamSource` real (arquivo parcial, repriorizar no seek).
- **Aceite:** entrar com conta real, listar vídeos de um canal, tocar prévia local (Media3) enquanto baixa.

## M3 — Cast para a TV (Google Cast)
- [ ] `GoogleCastGateway`: descoberta (`MediaRouter`), sessão, `RemoteMediaClient.load` com URL do servidor local.
- [ ] Serviço em primeiro plano: servidor + TDLib vivos com tela bloqueada; notificação de mídia com controles.
- [ ] Seek na TV → repriorizar download.
- **Aceite:** vídeo de 1 GB do Telegram começa na Chromecast em < 10 s, seek funciona, tela do celular pode apagar.

## M4 — DLNA/UPnP
- [ ] `DlnaCastGateway` com jUPnP: descoberta SSDP, `AVTransport`/`RenderingControl`, DIDL-Lite correto.
- [ ] Lista unificada de TVs (Cast + DLNA), ícone do protocolo, última TV favorita.
- **Aceite:** reproduz em ao menos 2 TVs DLNA diferentes (ex.: Samsung/LG/Sony) com seek e volume.

## M5 — Qualidade de uso
- [ ] Fila, retomar de onde parou (posição por `chatId+messageId`), legendas (arquivo no chat/`.srt`), velocidade.
- [ ] Aviso de formato incompatível; mensagens de erro do design system; acessibilidade (TalkBack).
- [ ] Segurança: chave do banco TDLib no Keystore, token por stream, bind apenas no IP do Wi-Fi.

## M6 — Expansão (v2)
- [ ] Roku (ECP), Fire TV (Fling), LG webOS, Samsung/Tizen. Transcodificação/HLS sob demanda.
- [ ] App receptor opcional para Android TV (player VLC/Media3 + login por QR) para quem não tem Chromecast.
- [ ] Watch Next / continuar assistindo no Google TV.

## M7 — Publicação
- [ ] Ícone/arte da loja, política de privacidade, revisão dos termos da API do Telegram, assinatura de release.
