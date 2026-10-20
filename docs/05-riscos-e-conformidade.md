# 05 · Riscos, licenças e conformidade

## Origem e propriedade intelectual
- TvGram e Web Video Caster foram **analisados só para entender funcionalidades**. O Lumicast é implementação nova:
  não copiar código descompilado, recursos, textos, ícones, nomes nem regras proprietárias (ex.: JSON de detecção
  de sites do Web Video Caster).
- "Telegram" é marca registrada: o nome do app **não** deve conter "Telegram"; descrever como "para vídeos do Telegram"
  apenas de forma factual e seguir as diretrizes de marca do Telegram.
- Nome **Lumicast** é provisório: verificar disponibilidade na Play Store/INPI antes de publicar.

## Telegram
- Requer `api_id`/`api_hash` próprios (my.telegram.org). Seguir os *API Terms of Service* (identificar o app como
  cliente não oficial, não burlar limites, respeitar privacidade). Revisar antes da publicação.
- TDLib é licenciada sob Boost Software License 1.0 (permissiva) — confirmar na versão usada.
- Dados: sessão e mensagens ficam só no aparelho; nada vai a servidores nossos.

## Conteúdo
- O app transmite o que o usuário já pode acessar na conta dele. Não incluir recursos para burlar proteção de
  conteúdo (chats com "proibir salvar/encaminhar" devem ser respeitados quando o TDLib sinalizar `has_protected_content`).

## Riscos técnicos
| Risco | Mitigação |
|---|---|
| TV não suporta codec/contêiner | Aviso por mime/codec; transcodificação no v2 |
| Seek longe do trecho baixado | Repriorizar `downloadFile` por offset; esperar com timeout e feedback na UI |
| Android mata serviço em segundo plano | Foreground service, notificação, tratar `onTimeout` (Android 15) |
| Redes com isolamento de clientes (AP isolation) | Detectar falha de conexão e orientar o usuário |
| Servidor local exposto na LAN | Token por stream, bind no IP Wi-Fi, expirar ao parar |
| Rate limit/FLOOD_WAIT do Telegram | Backoff e mensagem clara |
| APIs de Cast/DLNA variam por TV | Matriz de testes em 3+ TVs reais (M3/M4) |
