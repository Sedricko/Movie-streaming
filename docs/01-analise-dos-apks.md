# 01 · Análise dos dois APKs

**Método:** análise **estática** dos arquivos `.xapk` (manifest binário decodificado, listagem de classes DEX,
strings, bibliotecas nativas). Nada foi executado, e a rede estava desligada. Os dois apps passaram por R8
(ofuscação), então muitos nomes de classe sumiram. Por isso cada ponto abaixo está marcado:
**[Observado]** = visto diretamente no pacote · **[Inferido]** = dedução razoável · **[Desconhecido]**.

## A. TvGram 2.9 — `dev.vishnuv.tvgram` (Telegram na TV)
minSdk 32 · targetSdk 36 · v. 68 · splits: base + arm64 + mdpi.

| Tema | Achado |
|---|---|
| Plataforma | **[Observado]** App de **Android TV** (`LEANBACK_LAUNCHER`, banner, `touchscreen required=false`). Roda *na* TV, não no celular. |
| Telegram | **[Observado]** Cliente real via **TDLib 1.8.67** (`libtdjni.so`, ~25 MB). Estados de login: telefone, e-mail, registro; "o código chega no app do Telegram". |
| Player | **[Observado]** **libVLC 3.0.x** (`libvlc.so` 46 MB) para formatos difíceis + `media3 SubtitleView` para legendas. `VlcPlaybackStats` (bytes lidos, cache à frente). |
| Streaming | **[Observado]** `ExternalVideoStreamService` (foreground `dataSync`) com `127.0.0.1`, strings `tdlib_offset`, `tdlib_limit`, `DownloadFile off=`, `StreamDownloadMode`. **[Inferido]** servidor HTTP local que lê o arquivo **parcialmente baixado** do TDLib e atende Range, para tocar enquanto baixa e para players externos. |
| UI | **[Observado]** Jetpack Compose; abas (`ShellTab`), pastas de chat (`folders`, `folderVid`), canais favoritos, modo/ordem de lista (`LocalViewMode`, `LocalSortKey`). |
| Extras | **[Observado]** Mídia local (`localmedia`), playlists **M3U/IPTV** (`netstream/M3uPlaylist`), **Watch Next** (`tvgram://watchnext`, `WRITE_EPG_DATA`), PiP e multi-janela, personalização de legendas. |
| Negócio | **[Observado]** Play Billing, Firebase/Crashlytics, licença `pairip`. Não replicar. |
| Limite | Não faz **cast**: só reproduz na própria TV. É aí que o Lumicast se diferencia. |

**Pontos fortes a absorver:** login/TDLib, streaming progressivo, pastas e canais, retomar de onde parou, legendas.

## B. Web Video Caster 6.0.4 — `com.instantbits.cast.webvideo` (celular → TV)
minSdk 29 · targetSdk 36 · v. 8034 · 7 DEX (~20k+ classes).

| Tema | Achado |
|---|---|
| Descoberta | **[Observado]** Derivado do **Connect SDK**: provedores `SSDP`, `Zeroconf`, `FireTV`, `TVAppReceiver`, `WebReceiver`. |
| Protocolos | **[Observado]** Serviços `DLNA`, `DIAL`, `Roku`, `RokuChannel`, `AirPlay`, `FireTV`, `NetcastTV` (LG antiga), `WebOSTV` (LG), mais **Google Cast** (`play-services-cast-framework`) e **Amazon Fling/WhisperPlay**. |
| Servidor local | **[Observado]** **Jetty embutido** (`org/eclipse/jetty`, `javax/servlet`) com servlets `LocalFileServlet`, `MediaProxyServlet` (proxy de URLs remotas com cabeçalhos), `CorsServlet`, `ThumbnailServlet`. |
| Navegador | **[Observado]** `WebBrowser` com regras de detecção de vídeo em JSON (regex para `.m3u8`/`.mpd`, reescrita `.mpd→.m3u8`), histórico, favoritos, mais visitados, downloads. |
| Controle | **[Observado]** `PlayingActivity`, `MediaNotificationService`, `InternalPlayerService`, fila e playlists (`queue/Playlist*`), itens já reproduzidos, IPTV, legendas (OpenSubtitles). |
| Negócio | **[Observado]** Muito SDK de anúncios (MBridge, ByteDance, InMobi, AppLovin, Amazon, Facebook) + premium. **Não replicar.** |
| Limite | **[Observado]** Nenhuma integração com Telegram nem TDLib. |

**Pontos fortes a absorver:** descoberta multi-protocolo, servidor HTTP local com Range, fila, legendas, notificação de controle.

## C. Síntese — o que o Lumicast faz
| Capacidade | TvGram | Web Video Caster | **Lumicast** |
|---|:-:|:-:|:-:|
| Vídeos do Telegram | ✔ | ✘ | **✔ (TDLib)** |
| Tocar enquanto baixa | ✔ | — | **✔** |
| Enviar para a TV (cast) | ✘ | ✔ | **✔** |
| Vários protocolos de TV | — | ✔ | **Cast + DLNA (v1), Roku/Fire TV/LG (v2)** |
| Servidor local com Range | ✔ (127.0.0.1) | ✔ (LAN) | **✔ (LAN)** |
| Fila / retomar / legendas | ✔ / — / ✔ | ✔ / ✔ / ✔ | **✔** |
| Anúncios/rastreadores | sim | sim | **não** |

## D. O que NÃO foi possível concluir
- Fluxo exato de pareamento por protocolo e quais codecs cada TV aceita (exigiria execução em rede real).
- Como o TvGram trata reabertura de arquivos TDLib já removidos do cache.
- Detalhes de transcodificação do Web Video Caster (não identificados).
