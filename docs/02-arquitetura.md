# 02 · Arquitetura

## Visão geral
```
 ┌────────────────────────── Celular (Lumicast) ───────────────────────────┐
 │  UI Compose ── ViewModels ── domain (interfaces)                        │
 │                                  │                                      │
 │            ┌─────────────────────┼──────────────────────┐               │
 │            ▼                     ▼                      ▼               │
 │   TelegramGateway         StreamServer            CastGateway           │
 │   (TDLib)                 (Ktor + Range)          (Cast / DLNA / …)     │
 │      │  downloadFile          ▲  lê arquivo parcial       │ SetURI/Play │
 │      └──────► arquivo.parte ──┘                           │             │
 └───────────────────────────────┬───────────────────────────┼─────────────┘
                                 │ http://IP-do-celular:PORT/stream/{id}
                                 ▼
                               TV  ◄──────────────────────────┘
```

## Módulos de código (pacote `app.lumicast`)
| Pacote | Responsabilidade |
|---|---|
| `domain` | Modelos (`TgChat`, `TgVideo`, `CastDevice`, `PlaybackState`) e interfaces (`TelegramGateway`, `CastGateway`, `StreamSource`). Kotlin puro. |
| `data/stream` | `RangeParser` (RFC 9110), `RangeStreamServer` (Ktor/CIO), `StreamSource`. Pronto e testado no M0. |
| `data/telegram` | `FakeTelegramGateway` (demo) → `TdlibTelegramGateway` (M2). |
| `data/cast` | `FakeCastGateway` (demo) → `GoogleCastGateway` (M3) e `DlnaCastGateway` (M4). |
| `ui` | Tema, componentes, telas, navegação. |

## Fluxo "tocar na TV"
1. Usuário escolhe vídeo (`TgVideo.fileId`) e TV (`CastDevice`).
2. `TelegramGateway.openStream(fileId)` inicia `downloadFile(priority=32, offset=0, limit=0, synchronous=false)`
   e devolve uma `StreamSource` (tamanho total, `read(offset,len)` que **espera** os bytes existirem).
3. `StreamServer.register(source)` retorna `http://<ip-wifi>:<porta>/stream/<token>`.
4. `CastGateway.play(device, url, mime, título, legenda?)`.
5. Se a TV faz **seek** (Range além do que foi baixado): `StreamSource` chama
   `downloadFile(offset=pedido)` para repriorizar; a resposta espera até haver dados (timeout 30 s).
6. Ao parar: cancelar download, `StreamServer.unregister`, parar serviço em primeiro plano.

## Pontos técnicos críticos
- **TDLib:** o arquivo cresce em `local.path` com `is_downloading_active`/`downloaded_prefix_size`. Só é seguro
  servir bytes até `local.downloaded_prefix_size` a partir do offset do download. Para seek longe do prefixo,
  reiniciar o download no novo offset (`DownloadFile.offset`) e servir do arquivo novo.
- **Servidor:** vincular ao IP do Wi-Fi (não `0.0.0.0` aberto a todas as interfaces), token aleatório por stream,
  HEAD + `Accept-Ranges: bytes`, `Content-Range` correto, 416 para faixa inválida, cabeçalhos CORS para receivers web.
- **Serviço em primeiro plano** (`mediaPlayback`/`dataSync`) mantém servidor + TDLib vivos com a tela bloqueada.
- **Compatibilidade de codec:** TV pode recusar MKV/HEVC/áudio AC3-DTS. v1: detectar via `TgVideo.mimeType` e avisar
  ("Esta TV pode não reproduzir este formato"). v2: transcodificar (Media3 Transformer) ou HLS sob demanda.
- **Cast:** Google Cast com *Default Media Receiver* no v1 (sem registrar receiver próprio). DLNA via jUPnP
  (`AVTransport#SetAVTransportURI`/`Play`/`Seek`, `RenderingControl#SetVolume`), com `DIDL-Lite` e
  `contentFeatures.dlna.org` corretos (vários TVs são exigentes).
- **Segurança:** `api_hash` fora do git; banco TDLib criptografado com chave no Android Keystore; sem log de
  conteúdo de mensagens.

## Dependências (confirmar versões atuais no M1)
`androidx.compose` (BOM) · `navigation-compose` · `lifecycle` · `hilt` · `ktor-server-cio` ·
`play-services-cast-framework` · `org.jupnp` · `media3` · TDLib (build oficial JNI — ver M2).

## Fora do escopo do v1
Espelhamento de tela (MediaProjection), app receptor próprio para Android TV, transcodificação, iOS, anúncios/premium.
