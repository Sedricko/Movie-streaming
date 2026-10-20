# 06 · Especificação técnica para M2–M4

> Assinaturas do TDLib mudam entre versões 1.8.x. **Sempre confira o `TdApi.java` gerado** da versão usada;
> os nomes abaixo são o roteiro, não código final.

## M2 · TDLib
**Inicialização:** `Client.create(updatesHandler, exceptionHandler, exceptionHandler)`; reagir a
`UpdateAuthorizationState`:
| Estado | Ação |
|---|---|
| `AuthorizationStateWaitTdlibParameters` | `SetTdlibParameters` com `apiId`, `apiHash`, `databaseDirectory` e `filesDirectory` em `filesDir`, `useFileDatabase/useChatInfoDatabase/useMessageDatabase=true`, `systemLanguageCode="pt-BR"`, `deviceModel`, `applicationVersion`, `databaseEncryptionKey` (chave do Keystore) |
| `WaitPhoneNumber` | `SetAuthenticationPhoneNumber` |
| `WaitCode` | `CheckAuthenticationCode` |
| `WaitPassword` | `CheckAuthenticationPassword` |
| `WaitEmailAddress`/`WaitRegistration` | mostrar erro amigável "Esta conta precisa ser configurada no app oficial do Telegram" |
| `Ready` | liberar a Biblioteca |

**Listagem:** `LoadChats(ChatListMain, limit)` + `GetChat`; pastas via `UpdateChatFolders`;
"Mensagens salvas" = chat privado com o próprio `GetMe().id`.
**Vídeos de um chat:** `SearchChatMessages(chatId, query="", filter=SearchMessagesFilterVideo, limit=50, fromMessageId=…)`
(paginar). Do `MessageVideo.video`: `duration`, `fileName`, `mimeType`, `thumbnail`, `video` (`File.id`, `File.size`).
Respeitar conteúdo protegido (`Message.canBeSaved`/`Chat.hasProtectedContent`).
**Streaming:** `DownloadFile(fileId, priority=32, offset, limit=0, synchronous=false)`. Em `UpdateFile`,
`File.local.downloadedPrefixSize` conta **a partir do `offset` do download**, não do byte 0 do arquivo.
Portanto a `StreamSource` real precisa saber `[downloadOffset, downloadOffset + prefix)` disponível. Se o cliente
pedir fora dessa janela → `DownloadFile` novo com o novo `offset` e esperar. **Ajustar `GrowingFileStreamSource`
para receber essa janela** (hoje ele assume prefixo contíguo desde 0 — é o ponto a evoluir no M2).
**Limpeza:** `CancelDownloadFile` ao parar; `DeleteFile` opcional; limitar cache (`OptimizeStorage`).
**Erros:** `FLOOD_WAIT_n` → backoff; `PHONE_NUMBER_INVALID`, `PHONE_CODE_INVALID`, `PASSWORD_HASH_INVALID` → textos em pt-BR.

## M3 · Google Cast
- `OptionsProvider` declarado no manifest (`com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`),
  receiver `CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`.
- Descoberta via `MediaRouter`/`CastContext`; sessão com `SessionManagerListener`.
- Carregar: `MediaInfo.Builder(url).setStreamType(STREAM_TYPE_BUFFERED).setContentType(mime).setMetadata(...)`
  → `RemoteMediaClient.load(MediaLoadRequestData)`; controlar com `play/pause/seek/stop`; `setStreamVolume` na sessão.
- O Default Media Receiver aceita bem MP4/H.264+AAC; MKV/HEVC/AC3 dependem do aparelho → avisar o usuário.
- Serviço em primeiro plano (`mediaPlayback`) com `MediaSession` + notificação de mídia; tratar `onTimeout` do Android 15.

## M4 · DLNA/UPnP
- Descoberta: SSDP `M-SEARCH` por `urn:schemas-upnp-org:device:MediaRenderer:1` (jUPnP ou implementação própria);
  exige `CHANGE_WIFI_MULTICAST_STATE` e um `MulticastLock`.
- Controle (SOAP): `AVTransport#SetAVTransportURI(0, url, didl)` → `Play(0,"1")`; `Pause`, `Seek(0,"REL_TIME","HH:MM:SS")`,
  `Stop`, `GetPositionInfo`/`GetTransportInfo` (polling a cada 1 s); `RenderingControl#SetVolume/SetMute`.
- DIDL-Lite mínimo: `<item><dc:title/><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000">URL</res></item>`
  (escapar XML; ajustar o mime).
- Servidor local deve devolver `Accept-Ranges`, `Content-Length` correto e `transferMode.dlna.org: Streaming`
  (já implementado em `RangeStreamServer`). Algumas TVs enviam `HEAD` antes do `GET`.
- Matriz de testes: Samsung, LG, Sony e uma TV Android/Google TV.

## Servidor local (já existe)
`RangeStreamServer.start()` → `register(source)` → URL. Pendências: porta fixa configurável, parada automática por
inatividade, limite de conexões simultâneas, teste instrumentado com `curl`/ExoPlayer contra o servidor.
