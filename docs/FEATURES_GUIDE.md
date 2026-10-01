# 📖 Guia Completo de Funcionalidades do Viagara Launcher

Este documento apresenta uma visão técnica e funcional detalhada de todas as capacidades, comportamentos, algoritmos e interações de usuário disponíveis no **Viagara Launcher**.

---

## 📑 Sumário

1. [Barra Alfabética com Deformação Gaussiana (Edge Scrubber)](#1-barra-alfabética-com-deformação-gaussiana-edge-scrubber)
2. [Sistema de Alinhamento Harmônico Unificado](#2-sistema-de-alinhamento-harmônico-unificado)
3. [Botão Dinâmico de Ação (Dynamic Action Button)](#3-botão-dinâmico-de-ação-dynamic-action-button)
4. [Widget de Relógio e Calendário (Niagara Clock)](#4-widget-de-relógio-e-calendário-niagara-clock)
5. [Gaveta de Aplicativos e Busca em Tempo Real](#5-gaveta-de-aplicativos-e-busca-em-tempo-real)
6. [Sistema de Pastas (Folders)](#6-sistema-de-pastas-folders)
7. [Hospedagem de Widgets do Android (WidgetSlot)](#7-hospedagem-de-widgets-do-android-widgetslot)
8. [Reprodutor de Mídia Integrado (Now Playing)](#8-reprodutor-de-mídia-integrado-now-playing)
9. [Integração de Notificações do Sistema](#9-integração-de-notificações-do-sistema)
10. [Gestos Globais e Atalhos de Acessibilidade](#10-gestos-globais-e-atalhos-de-acessibilidade)
11. [Ícones Temáticos Dinâmicos (Material You / Monet)](#11-ícones-temáticos-dinâmicos-material-you--monet)

---

## 1. Barra Alfabética com Deformação Gaussiana (Edge Scrubber)

O **Edge Scrubber** é o mecanismo principal de navegação ergonômica com uma mão do Viagara Launcher, inspirado no Niagara Launcher.

```
      │
   ┌──┴──┐
   │  A  │
   │  B  │
  (   C   ) ─── Dedo do usuário (pico da curva gaussiana)
   │  D  │
   │  E  │
   └──┬──┘
      │
```

### Componentes Arquiteturais:
- [`EdgeScrubber.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/applist/EdgeScrubber.kt): Renderiza a coluna de glifos (`★`, `A`–`Z`) e executa o deslocamento horizontal via `graphicsLayer`.
- [`ScrubberGeometry.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/applist/ScrubberGeometry.kt): Mapeia as posições Y de cada letra em relação à altura total do display e calcula a amplitude da deformação.
- [`EdgeTouchZone.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/applist/EdgeTouchZone.kt): Faixa invisível de toque na borda da tela com prioridade sobre o sistema de gestos do Android.
- [`ScrubState.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/applist/ScrubState.kt): Mantém o estado reativo (`letter`, `scrubY`, `pullPx`, `isDragging`) desacoplado do escopo de recomposição principal.

### Princípios de Engenharia e Comportamento:
1. **Curva Gaussiana sem Escalonamento de Glifos:** Ao invés de aumentar a escala das fontes dos glifos (o que causaria distorção e borrão de rasterização), as letras se deslocam no eixo X projetando uma curvatura suave centrada nas coordenadas do dedo.
2. **Leitura na Fase de Desenho (Draw Phase):** As posições X e Y do scrubber são passadas como lambdas para o modificador `.graphicsLayer { translationX = ... }`. Isso evita disparar recomposição nas 27 letras a cada evento de toque a 60/120 Hz, garantindo fluidez absoluta sem engasgos (jank).
3. **Feedback Háptico Tátil:** Ao transicionar de um glifo para o seguinte, a função [`HapticUtil.tick()`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/service/HapticUtil.kt) gera um pulso ultrarrápido nos motores táteis lineares (Haptic Feedback) do aparelho.
4. **Atalho de Estrela (`★`):** O primeiro item no topo da régua é uma estrela (`★`). Ao deslizar ou tocar sobre ela, a gaveta fecha ou salta diretamente para o início dos Favoritos.
5. **Configuração de Borda (`edgeSide`):** Pode ser configurado para a borda esquerda (`LEFT`), direita (`RIGHT`) ou ambas (`BOTH`), permitindo total flexibilidade para canhotos e destros.

---

## 2. Sistema de Alinhamento Harmônico Unificado

O layout do Viagara Launcher adota um alinhamento horizontal matemático rigoroso entre **Alfabeto**, **Aplicativos Favoritos**, **Pastas**, **Widgets** e **Relógio**.

### Fórmula de Alinhamento (`HomeScreen.kt`):
```kotlin
val hasAlphabetStart = alwaysShowAz && (edgeSide == EdgeSide.LEFT || edgeSide == EdgeSide.BOTH)
val hasAlphabetEnd = alwaysShowAz && (edgeSide == EdgeSide.RIGHT || edgeSide == EdgeSide.BOTH)
val alphabetClearance = (sidePaddingDp + 32).dp
val contentStart = if (hasAlphabetStart) alphabetClearance else sidePaddingDp.dp
val contentEnd = if (hasAlphabetEnd) alphabetClearance else sidePaddingDp.dp
```

```
┌─────────────────────────────────────────────────────────────┐
│                       BORDA DO DISPLAY                      │
├───────┬────────────┬────────────────────────────────┬───────┤
│ side  │ Coluna     │ Gutter (8dp)                   │       │
│ Pad   │ Alfabeto   │                                │       │
│ (20dp)│ (24dp)     │                                │       │
├───────┴────────────┴────────────────────────────────┤       │
│                                                     │       │
│ ► ALINHAMENTO contentStart (52dp)                   │       │
│                                                     │       │
│   [ 12:45 ] Relógio (Classic, Stacked, Minimal...)  │       │
│   [ WIDGET ] AppWidgetHostView                      │       │
│   [ ♪ CAPA ] Reprodutor Now Playing                 │       │
│   [ ÍCONE  ] Aplicativo Favorito                    │       │
│   [ 📁     ] Pasta                                  │       │
│                                                     │       │
└─────────────────────────────────────────────────────┴───────┘
```

### Características Principais:
1. **Coerência Visual de Linha Base:** Os ícones de favoritos (`AppIcon`), a capa de álbum do Now Playing, a margem externa de widgets do sistema e o texto do relógio iniciam exatamente na mesma coordenada `contentStart`.
2. **Zero Padding em Widgets Nativos:** O Viagara Launcher implementa `ViagaraAppWidgetHostView`, anulando os paddings automáticos herdados do framework do Android (API 14+) para que qualquer widget adicionado permaneça perfeitamente alinhado à grade.
3. **Puxador de Ajuste Lateral (`SidePaddingHandle`):** Permite arrastar o espaçamento de borda entre `0dp` e `96dp`. Todos os elementos (alfabeto, relógio, widgets e apps) recalculam suas posições simultaneamente.
4. **Botão *"Alinhar elementos"* no Modo de Edição:** No toolbar superior de edição de layout, o botão de restauração redefine instantaneamente todos os afastamentos e retorna o `sidePaddingDp` para `20dp`, recompondo a harmonia original do launcher com confirmação háptica.

---

## 3. Botão Dinâmico de Ação (Dynamic Action Button)

Localizado estrategicamente na parte inferior da tela inicial (ao lado da barra de navegação e do alfabeto), o **Dynamic Action Button** oferece acesso ergonômico instantâneo a até 3 aplicativos através de gestos mecânicos com física de mola e borracha.

```
          [▲ Swipe Up App]
                 ▲
                 │ (Arrasto com resistência tanh)
         ┌───────────────┐
         │ (★) Click App │ ──► Toque rápido (Overshoot bounce)
         └───────────────┘
                 │ (Arrasto com resistência tanh)
                 ▼
         [▼ Swipe Down App]
```

### Gestos e Funcionalidades:
- **Toque Rápido (Click):** Inicia o aplicativo principal configurado com micro-animação elástica de compressão (`scale(0.88f)`) e expansão com overshoot.
- **Swipe Up (Arrastar para Cima):** Ao puxar o botão para cima além do limiar físico, o ícone transiciona verticalmente em estilo roleta (carousel) revelando o app de destino e emitindo vibração no momento do disparo.
- **Swipe Down (Arrastar para Baixo):** Permite lançar um terceiro aplicativo configurado ao puxar para baixo.
- **Toque Longo (Pressionar e Segurar):** Abre a tela de configuração dedicada ([`DynamicButtonSettingsScreen.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/settings/DynamicButtonSettingsScreen.kt)) para selecionar os apps de cada ação.

### Física e Animações Implementadas:
1. **Resistência Física Hiperbólica (`tanh`):** O deslocamento visual segue a equação:
   $$\Delta y_{visual} = \text{maxStretchPx} \cdot \tanh\left(\frac{\Delta y_{bruto}}{\text{maxStretchPx} \cdot 1.8}\right)$$
   Isso simula a resistência elástica de uma faixa de borracha, impedindo que o botão ultrapasse limites bizarros na tela.
2. **Squash & Stretch Dinâmico:** Conforme o usuário arrasta verticalmente, o botão sofre compressão no eixo X e alongamento no eixo Y proporcional à força de tração.
3. **Cores Dinâmicas Monet (Material You):** O ícone do botão herda automaticamente a cor primária temática do papel de parede e fundo vítreo translúcido.

---

## 4. Widget de Relógio e Calendário (Niagara Clock)

O componente [`NiagaraClockWidget.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/home/NiagaraClockWidget.kt) fica fixado acima dos favoritos, a aproximadamente 2,5 cm da margem superior do dispositivo (`CLOCK_TOP_PADDING_DP = 158.dp`).

### 6 Estilos Disponíveis ([`ClockStyle`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/data/Prefs.kt)):
1. **`CLASSIC`:** Horário em tipografia grande semibold (`58sp`) com espaçamento negativo entre caracteres (`-1.5sp`), seguido pela data por extenso logo abaixo.
2. **`STACKED`:** Formato moderno empilhado com horas em peso extra-bold acima dos minutos em peso leve (light), com data minimalista.
3. **`MINIMAL`:** Visual limpo e discreto em peso leve (`46sp`) com `letterSpacing = 1.sp`.
4. **`ANALOG`:** Mostrador analógico desenhado em Compose Canvas (`AnalogDial`) com ponteiros vetoriais de hora e minuto, centro de rotação e marcadores de hora circulares.
5. **`DIGITAL_CARD`:** Cartão translúcido com efeito de vidro fosco tingido na cor do papel de parede (`dynamicSurfaceColor`) e borda sutil.
6. **`DAY_FOCUS`:** Ênfase no dia da semana em caixa alta com estilo tipográfico contemporâneo.

### Ações Integradas com o Sistema:
- **Tocar na Hora:** Dispara `launchClockApp(context)`, abrindo o aplicativo de Relógio/Alarmes padrão (`AlarmClock.ACTION_SHOW_ALARMS`).
- **Tocar na Data:** Dispara `launchCalendarApp(context)`, abrindo o aplicativo de Calendário padrão (`Intent.CATEGORY_APP_CALENDAR` ou `CalendarContract`).
- **Atualização Otimizada por Broadcast:** O relógio não utiliza loops ou coroutines de delay; ele escuta diretamente os broadcasts do sistema (`ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`), economizando 100% de bateria quando o display está desligado.

---

## 5. Gaveta de Aplicativos e Busca em Tempo Real

A lista completa de aplicativos ([`AppListScreen.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/applist/AppListScreen.kt)) fica em camada sobreposta à tela inicial:

### Funcionalidades:
- **Indexação Alfabética A-Z Instantânea:** Os aplicativos instalados são categorizados com separadores de letra correspondentes.
- **Barra de Pesquisa com Filtro em Tempo Real:** Campo de busca no topo com foco automático ou sob demanda, filtrando nomes originais e apelidos personalizados em milissegundos.
- **Gesto de Puxar para Fechar (Elastic Pull-to-Dismiss):** Arrastar a lista para baixo além do topo ativa o recolhimento elástico que fecha a gaveta de volta para a tela inicial.
- **Menu de Contexto do Aplicativo ([`AppMenuDialog.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/common/AppMenuDialog.kt)):** Ao pressionar e segurar um app:
  - Adicionar/Remover dos Favoritos.
  - Mover para Pasta.
  - Editar Nome e Ícone personalizado.
  - Informações do App no sistema Android (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
  - Desinstalação direta (`Intent.ACTION_UNINSTALL_PACKAGE`).

---

## 6. Sistema de Pastas (Folders)

O Viagara Launcher permite agrupar múltiplos aplicativos em coleções organizadas:

### Modos de Exibição:
1. **Modo Inline (Expansão na Lista):** Ao tocar na pasta, ela expande seus itens verticalmente entre as linhas de favoritos, mantendo o fluxo contínuo.
2. **Modo Janela Flutuante ([`FolderFloatingDialog.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/home/FolderFloatingDialog.kt)):** Exibe a pasta como uma janela suspensa com desfoque de fundo (*frosted glass*), botões de ação e ícones de fechar/configurar.

### Customização:
- **Ícones de Pastas:** Ícone padrão de pasta estilizada ou montagem dinâmica dos primeiros 4 ícones em miniatura.
- **Renomeação:** Suporte a qualquer título descritivo.
- **Serialização JSON:** As pastas são persistidas de forma atômica no DataStore em formato JSON (`folders_json`).

---

## 7. Hospedagem de Widgets do Android (WidgetSlot)

O Viagara Launcher possui um slot versátil de widgets nativos do Android posicionado entre o relógio e a lista de favoritos.

### Características Técnicas:
- **[`ViagaraAppWidgetHost.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/widget/ViagaraAppWidgetHost.kt):** Gerencia a vinculação dos IDs de widgets (`bindAppWidgetIdIfAllowed`) e o ciclo de vida de atualização (`startListening` / `stopListening`).
- **Arbitragem de Toques via [`LongPressFrameLayout`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/widget/LongPressFrameLayout.kt):** O container não consome os toques no `ACTION_DOWN`. Ele permite que os botões internos do widget funcionem normalmente e intercepta o fluxo apenas se o usuário mantiver o dedo imóvel pelo tempo de um clique longo.
- **Redimensionamento Dinâmico:** No menu do widget ou no modo de edição, é possível redimensionar a altura do widget dinamicamente com puxadores visuais de arrasto e feedback háptico.

---

## 8. Reprodutor de Mídia Integrado (Now Playing)

O bloco de mídia Now Playing ([`NowPlayingWidget.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/media/NowPlayingWidget.kt)) surge automaticamente quando qualquer aplicativo do sistema está reproduzindo áudio (Spotify, YouTube Music, Deezer, Apple Music, Podcats, etc.).

### Recursos:
- **Serviço de Escuta ([`NowPlayingListenerService.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/media/NowPlayingListenerService.kt)):** Conecta-se às sessões de mídia ativas do Android (`MediaSession`).
- **Controles de Reprodução:** Botões de faixa anterior, reproduzir/pausar e próxima faixa.
- **Capa do Álbum Extraída:** Exibe o bitmap da arte do álbum com cantos arredondados (`8dp`).
- **Barra de Progresso em Tempo Real:** Avança localmente a barra de reprodução em intervalos de 500ms durante o playback.
- **Gesto de Descarte (Swipe to Dismiss):** Deslizar o cartão para a direita com velocidade suficiente encerra a reprodução (`transportControls.stop()`) e recolhe o widget.

---

## 9. Integração de Notificações do Sistema

O Viagara Launcher monitora notificações ativas de mensagens (WhatsApp, Telegram, SMS, etc.) para exibição contextual na tela inicial.

### Recursos:
- **Prévias de Mensagens sob o Nome do App:** A última mensagem recebida é exibida diretamente abaixo do rótulo do aplicativo favorito.
- **Contador de Mensagens Não Lidas:** Indica se há mensagens adicionais acumuladas na conversa.
- **Diálogo Detalhado Translúcido ([`NotificationDetailDialog.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/notification/NotificationDetailDialog.kt)):** Exibe histórico completo com balões de conversa, avatar do remetente e botões para responder ou dispensar.

---

## 10. Gestos Globais e Atalhos de Acessibilidade

- **Toque Duplo para Bloquear Tela (Double Tap to Lock):** Tocar duas vezes em qualquer área vazia da tela inicial desliga a tela usando o serviço de acessibilidade ([`ViagaraAccessibilityService.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/service/ViagaraAccessibilityService.kt)) ou o efeito de transição escura.
- **Arrastar para Baixo (Pull Down Shade):** Deslizar o dedo verticalmente para baixo em qualquer lugar da tela inicial abre a cortina de notificações do Android (`GLOBAL_ACTION_NOTIFICATIONS`).
- **Espiar Barra de Status (Peek Status Bar):** Um leve puxão para baixo aciona o [`StatusBarFader`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/service/StatusBarFader.kt) para visualizar a barra de status temporariamente caso ela esteja configurada como oculta.
- **Deslizar da Borda:** Puxar a partir de qualquer ponto da borda abre a gaveta de aplicativos já filtrada na letra correspondente à altura do toque.

---

## 11. Ícones Temáticos Dinâmicos (Material You / Monet)

Geração automática e inteligente de ícones adaptativos com as cores dinâmicas do papel de parede para **100% dos aplicativos** do usuário:

- **Cobertura Universal (Geração pela Própria Launcher):**
  - Para aplicativos com camada monocromática oficial (Android 13+), o vetor oficial é extraído e tingido com precisão.
  - Para aplicativos sem camada monocromática (legados ou adaptativos comuns), um algoritmo especializado de remoção de fundo e contraste analisa as bordas, remove fundos opacos e preserva apenas o emblema central.
  - Como salvaguarda final para ícones lisos ou corrompidos, um monograma tipográfico elegante é renderizado automaticamente.
- **Estilos Visuais Selecionáveis:**
  - *Material You:* Recipiente squircle dinâmico com cantos arredondados a 28% e glifo centralizado.
  - *Minimalista:* Glifo monocromático vazado com a cor primária de destaque Monet, sem container.
- **Integração nas Preferências:** Ativação em *Configurações → Aparência → Ícones temáticos* com prévia instantânea em tempo real.

