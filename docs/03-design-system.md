# 03 · Design system — "Sala escura, feixe de luz"

**Ideia:** assistir na TV é uma sala escura com um feixe de projetor. O app é a sala (tons de azul-tinta, quietos);
o único elemento vivo é o **Feixe** (âmbar): o botão de cast e a linha animada celular → TV. Tudo mais fica calmo
para o vídeo ser o protagonista. **A ousadia está em um lugar só: o Feixe.**

## Cores (tokens em `ui/theme/Color.kt`)
| Token | Escuro | Claro | Uso |
|---|---|---|---|
| `Noite` (fundo) | `#15121F` | `#F7F5FB` | Fundo das telas |
| `Tela` (superfície) | `#1F1B2D` | `#FFFFFF` | Cartões, barras |
| `Nevoa` (superfície alta) | `#2C2740` | `#ECE8F5` | Chips, campos, mini player |
| `Papel` (texto) | `#F1EEF8` | `#1B1730` | Texto principal |
| `PapelSuave` (texto 2) | `#A8A2BD` | `#5C5675` | Texto secundário |
| `Feixe` (ação/cast ativo) | `#FFB454` | `#B26A00` | Botão de cast, progresso, foco |
| `FeixeSuave` | `#3A2E1C` | `#FFE3BD` | Fundo de estado "transmitindo" |
| `Fonte` (Telegram) | `#4FA3F7` | `#1B6FC2` | Indica origem Telegram/conectado |
| `Erro` | `#FF6B6B` | `#C62828` | Falhas |
| `Ok` | `#5BD6A4` | `#13795B` | Sucesso |

Regras: âmbar = *ação e transmissão*; azul = *origem Telegram*; nunca usar os dois como decoração.
Sem gradientes decorativos. Contraste mínimo AA (4.5:1 para texto).

## Tipografia (`ui/theme/Type.kt`)
- **Títulos/destaque:** *Bricolage Grotesque* (600–700). **Corpo/rótulos:** *Figtree* (400–600).
- Escala (sp): Display 32/36 · Título 22/28 · Subtítulo 17/24 · Corpo 15/22 · Rótulo 13/18 · Legenda 12/16.
- Sentence case sempre; **sem** rótulos em CAIXA ALTA; sem "eyebrow" acima de títulos; linhas < 70 caracteres.
- Os .ttf ficam em `res/font` (baixar no M1). Até lá o código usa `FontFamily.Default`.

## Forma e espaço
- Raios **diferentes por papel**: miniatura 14 dp · cartão 20 dp · folha inferior 28 dp (topo) · chip 999 dp ·
  botão Feixe circular. Não usar um raio único.
- Grade de 4 dp; margens de tela 20 dp; espaçamento entre blocos 24 dp.
- Elevação por **cor de superfície** (Tela → Névoa), não por sombra.

## O Feixe (assinatura)
- **Botão Feixe:** círculo 56 dp âmbar com ícone de cast. Em repouso, sólido. Transmitindo: anel pulsando devagar
  (1 ciclo = 2,4 s) que respeita "reduzir animações".
- **Linha do Feixe:** em *Reproduzindo*, uma linha fina âmbar liga o ícone do celular ao da TV; um ponto de luz
  percorre a linha **só enquanto está carregando/buffering** (o movimento significa "algo está chegando").
- Nada mais anima sozinho. Animações respondem a toques (expandir mini player, trocar aba).

## Navegação e telas
Barra inferior com 3 destinos: **Biblioteca · TVs · Reproduzindo**. Mini player acima da barra quando há transmissão.

```
 Entrar            Biblioteca              TVs                  Reproduzindo
┌─────────────┐   ┌───────────────┐   ┌───────────────┐    ┌───────────────┐
│             │   │ Olá, Ana   ⚙  │   │ TVs na rede   │    │  ◄  Sala      │
│  Lumicast   │   │ [Buscar vídeo]│   │ ● Sala  Cast  │    │ ┌───────────┐ │
│  Seus vídeos│   │ Mensagens salv│   │ ○ Quarto DLNA │    │ │ miniatura │ │
│  do Telegram│   │ ▭▭▭ ▭▭▭ ▭▭▭   │   │ ○ Cozinha     │    │ └───────────┘ │
│  na TV.     │   │ Canais        │   │               │    │ 📱 ·····► 📺 │
│ [Telefone ] │   │ ▭ Título  1h4 │   │ Não achou?    │    │ ━━━●────  1:02│
│ [Continuar] │   │ ▭ Título  42m │   │ Procurar de   │    │ ⏮  ⏯  ⏭  🔊  │
└─────────────┘   └───────────────┘   │ novo          │    │ Legendas  Fila│
                                      └───────────────┘    └───────────────┘
```
- **Entrar:** telefone → código (chega no app do Telegram) → senha 2 etapas se existir. Um passo por tela.
- **Biblioteca:** busca; atalhos *Mensagens salvas* e pastas; lista de chats; dentro do chat, grade/lista de vídeos
  com duração, tamanho e barra fina âmbar de "retomar". Toque = escolher TV (folha inferior) ou tocar na última TV.
- **TVs:** lista com tipo (Cast/DLNA), estado e botão "Transmitir". Vazio: explicar Wi-Fi igual e oferecer "Procurar de novo".
- **Reproduzindo:** controles grandes, seek com pré-visualização do buffer (trecho já baixado em tom mais claro),
  legendas, fila, volume da TV.

## Voz e textos (pt-BR)
Botões dizem o que acontece: "Transmitir na Sala", "Procurar de novo", "Entrar com o Telegram".
Erros explicam a causa e a saída, sem pedir desculpas: *"Não achamos TVs. Confira se o celular e a TV estão no mesmo Wi-Fi."*
Vazios convidam: *"Nenhum vídeo neste chat. Envie um vídeo pelo Telegram e ele aparece aqui."*
Mesma ação, mesmo nome no fluxo todo (Transmitir → "Transmitindo", nunca "Enviar"/"Castear" em outro ponto).

## Acessibilidade
Alvos ≥ 48 dp · `contentDescription` em todo ícone funcional · foco visível (anel âmbar) · respeitar escala de fonte
e "reduzir animações" · legenda/controle por TalkBack · tema claro e escuro (e cores dinâmicas **desligadas** por padrão
para preservar a identidade).
