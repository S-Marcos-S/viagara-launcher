### 🚀 Novidades e Melhorias da Versão 0.59.41

- **Ajuste Fino de Posicionamento e Elevação da Janela de Inspeção (`AppRootInspectorDialog`):**
  - **Elevação da Base do Diálogo (~2 mm):** Aplicado deslocamento vertical e calibração de altura proporcional (`fillMaxHeight(0.89f)` e `offset(y = -8.dp)`), subindo a base da tela de inspeção em aproximadamente 2 milímetros em relação à borda inferior do display. O diálogo não colide mais com a barra de navegação/gestos do sistema e mantém um espaçamento visual equilibrado e confortável.

---

### 🚀 Novidades e Melhorias da Versão 0.59.40

- **Redesign Completo e Otimização de Espaço da Tela de Inspeção de Aplicativos (`AppRootInspectorDialog`):**
  - **Alinhamento Preciso do Ícone com o Nome do App:** Corrigido o alinhamento vertical do cabeçalho (`Alignment.Top` com calibração visual), alinhando o topo do ícone perfeitamente com o título do aplicativo e eliminando o aspecto desalinhado/torto causado pela centralização vertical anterior.
  - **Botões de Ação Superiores Compactos:** Os botões de recarregar, logs e fechar foram reposicionados no topo à direita com dimensões reduzidas (28dp), liberando largura horizontal e altura no cabeçalho.
  - **Selo "AO VIVO" em Linha Única Sem Quebras:** Prevenção estrita de quebra de linha (`maxLines = 1, softWrap = false`) no selo de telemetria em tempo real, garantindo que o texto "AO VIVO" seja sempre exibido em uma única linha com espaçamento impecável.
  - **Cards de Métricas Compactos (CPU, RAM, Velocidades):** Reestruturação dos blocos de CPU, RAM, Download e Upload em cartões horizontais reduzidos (nome e ícone à esquerda, valor à direita), economizando dezenas de pixels verticais.
  - **Nomenclatura Concisa de Transferência:** Redução dos títulos de rede de "Velocidade Download" e "Velocidade Upload" para os termos diretos "Download" e "Upload", eliminando poluição visual e texto excessivo.
  - **Área Ampla Expandida para Processos e Memória:** Todo o espaço vertical recuperado do topo e a calibração de altura da janela foram revertidos integralmente para a seção inferior (`weight(1f)`), aumentando substancialmente a quantidade de processos ativos, gráficos de memória PSS, conexões e permissões visíveis simultaneamente na tela sem necessidade de rolagem excessiva e sem alterar a escala tipográfica.

---

### 🚀 Novidades e Melhorias da Versão 0.59.39

- **Correção de Referência no Gerenciador de Sobreposição (`FloatingLogOverlayManager`):**
  - **Resolução de Chamada de Método em Crashes:** Corrigida a referência da ação de limpeza de crashes na aba de falhas (`LogViewerTab.CRASHES`), invocando corretamente `crashManager.clearCrashes()` em vez de `clearAllCrashes()`, assegurando compilação limpa na pipeline de release.

---

### 🚀 Novidades e Melhorias da Versão 0.59.38

- **Bolinha e Janela Flutuante Globais do Sistema (`FloatingLogOverlayManager`):**
  - **Sobreposição em Qualquer Aplicativo:** A bolinha flutuante e a janela de logs agora utilizam o gerenciador nativo de janelas do sistema operacional (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`), permanecendo abertas e ativas sobre **qualquer aplicativo** que o usuário abrir (Chrome, WhatsApp, jogos, configurações ou o próprio launcher).
  - **Expansão e Compactação Interativas:** Em qualquer aplicativo em primeiro plano, o usuário pode tocar na bolinha para expandir a Central de Logs completa flutuando sobre o app em execução. Ao tocar no botão de minimizar (`-`) ou na área externa, a janela se compacta suavemente de volta à bolinha.
  - **Gerenciamento de Ciclo de Vida Próprio para Compose (`OverlayLifecycleOwner`):** Implementada infraestrutura completa de `LifecycleOwner`, `ViewModelStoreOwner` e `SavedStateRegistryOwner` para renderizar a interface Jetpack Compose diretamente no `WindowManager` do sistema fora de uma Activity.
  - **Concessão Automatizada de Permissão de Sobreposição:** Adicionada permissão `android.permission.SYSTEM_ALERT_WINDOW` no manifesto. Para usuários com Root, a permissão é concedida instantaneamente via `appops set <pkg> SYSTEM_ALERT_WINDOW allow`; para aparelhos sem root, a tela de configuração do sistema é aberta diretamente com 1 toque.
  - **Movimentação Livre e Encerramento:** A bolinha e a janela flutuante podem ser arrastadas para qualquer ponto da tela sobre qualquer aplicativo, contando com botão dedicado `✕` para encerramento a qualquer momento.

---

### 🚀 Novidades e Melhorias da Versão 0.59.37

- **Desbloqueio Total de Toques e Rolagem do Alfabeto com a Bolinha Minimizada:**
  - **Transição Não-Modal com `Popup`:** A bolinha flutuante minimizada foi desacoplada da janela modal do Android (`Dialog`), que cobria a tela inteira e impedia toques na atividade subjacente. Ao minimizar, o `Dialog` é desativado e a bolinha passa a ser exibida como um `Popup` não-modal e não-focalizável (`focusable = false`).
  - **Rolagem do Alfabeto e Gaveta 100% Funcionais:** Toda a área da tela (exceto a própria bolinha de 56dp) fica livre para interação imediata, permitindo rolar a barra alfabética rápida (A-Z), navegar na lista de aplicativos e abrir apps normalmente enquanto o monitoramento de log continua ativo.
  - **Limites de Tela Inteligentes:** A bolinha possui detecção de limites da tela (`screenWidthPx` e `screenHeightPx`), evitando que seja arrastada para fora do display ou sobreposta indevidamente.
  - **Gesto de Toque vs. Arraste Aperfeiçoado:** O toque na bolinha restaura instantaneamente a janela flutuante completa, enquanto o movimento contínuo desloca a bolinha suavemente pela tela.

---

### 🚀 Novidades e Melhorias da Versão 0.59.36

- **Janela Flutuante Totalmente Arrastável & Minimizar para Bolinha ("Floating Bubble"):**
  - **Movimentação Livre da Janela:** Adicionada barra de arraste (*drag handle*) no topo da Central de Logs e suporte a gestos de arrasto contínuos (`detectDragGestures`) no cabeçalho, permitindo mover e reposicionar a janela livremente em qualquer lugar da tela.
  - **Minimização para Bolinha Flutuante:** Novo botão de minimizar (`-`) no cabeçalho que compacta a janela em um ícone circular flutuante de 56dp. A bolinha pode ser arrastada livremente por toda a tela e possui botão dedicado para fechamento rápido.
  - **Telemetria e Status em Tempo Real na Bolinha:** A bolinha exibe o ícone do aplicativo em análise (ou ícone de terminal), ponto indicador vermelho durante gravações de sessão ativas e selo numérico de alerta quando novos crashes são detectados.
  - **Restauração Instantânea com 1 Toque:** Toque simples na bolinha expande a Central de Logs de volta ao seu estado flutuante com animação e foco preservados. Ao minimizar, o scrim escuro e o desfoque de fundo são dispensados dinamicamente para manter a tela limpa e acessível.

- **Alternador de Filtro de Aplicativo Reversível e Intuitivo no Cabeçalho:**
  - **Design Contextual Claro:** Substituição do botão anterior por uma pílula de ação expressiva que deixa inequívoca a ação do usuário: exibe `[ 🌐 Ver todos ]` ao filtrar pelo app e `[ 🎯 Filtrar <Nome do App> ]` ao exibir o log geral do sistema.
  - **Memória de Contexto:** A janela preserva o aplicativo de origem em memória (`sourceApp`), possibilitando alternar quantas vezes quiser entre os logs globais do Android e os logs exclusivos daquele app sem perder o contexto original.

- **Correção de Cursor e Alinhamento na Barra de Pesquisa:**
  - **Alinhamento Vertical Preciso:** Corrigido o salto de altura do cursor e do texto na barra de busca de logs. O campo de digitação e o placeholder agora utilizam alinhamento vertical compartilhado (`Alignment.CenterStart`), garantindo que o cursor apareça exatamente no centro vertical desde o primeiro clique antes de iniciar a digitação.

- **Correção de Compilação na Central de Logs (`AppLogViewerDialog`):**
  - Ajustado o fechamento de blocos de interface (`Surface` e `Box`) do diálogo modal, corrigindo o erro de escopo de funções internas e garantindo compilação limpa na pipeline de release.

---

### 🚀 Novidades e Melhorias da Versão 0.59.35

- **Aprimoramentos Visuais e de Layout na Central de Logs (`AppLogViewerDialog`):**
  - **Efeito Translúcido com Desfoque de Fundo (Background Blur):** Corrigido o ponto de captura do `DialogWindowProvider` movendo o efeito imperativo para dentro do escopo do `Dialog`, ativando o flag `FLAG_BLUR_BEHIND` com raio de desfoque calibrado (40px) no Android 12+ e ajustando a opacidade do scrim escuro para destacar o efeito de vidro fosco (frosted glass) sobre o papel de parede.
  - **Visual Realmente Flutuante e Proporções Aperfeiçoadas:** Ajustadas as dimensões da janela modal flutuante de 92% para 82% da altura e 92% da largura, garantindo margens simétricas e bordas arredondadas (24dp) claramente suspensas acima da tela, sem colidir ou se estender até a barra inferior do dispositivo.
  - **Barra de Abas Responsiva com Rolagem Horizontal:** A navegação superior agora utiliza rolagem horizontal suave com espaçamento e preenchimento adequados, impedindo que textos como "Gravação", "Crashes & ANRs" e contadores de badge sejam espremidos ou sofram corte em qualquer tamanho de tela ou escala de fonte.
  - **Ajustes Tipográficos e Prevenção de Overflow:** O botão de início de gravação de sessão e os títulos de aplicativo no cabeçalho agora possuem limitação inteligente de linha única com reticências (`TextOverflow.Ellipsis`), assegurando alinhamento visual impecável mesmo com nomes extensos de aplicativos.

- **Estabilidade e Correção Crítica de Foreground Service (`AppLogCaptureService`):**
  - **Resolução do Crash `ForegroundServiceDidNotStartInTimeException`:** Corrigida a inicialização de monitoramento passivo (`startMonitoring`), que chamava indevidamente `startForegroundService` sem invocar `startForeground()`. O monitoramento agora utiliza o ciclo padrão de serviço iniciado e é interrompido de forma limpa ao fechar a janela (quando não houver gravação ativa).
  - **Chamada Imediata de `startForeground` na Gravação:** A inicialização de sessões gravadas (`ACTION_START_CAPTURE`) agora invoca imediatamente a notificação persistente de primeiro plano com tipo explícito `FOREGROUND_SERVICE_TYPE_DATA_SYNC` no Android 10+ (Q+), prevenindo estritamente timeouts de inicialização e garantindo estabilidade absoluta.

---

### 🚀 Novidades e Melhorias da Versão 0.59.34

- **Auto-Ajuste Inteligente de Fonte para Widgets com Frase do Dia:**
  - **Mecanismo Dinâmico de Medição Tipográfica (`AutoSizedDailyQuoteView`):** Implementado cálculo em tempo real utilizando o `TextMeasurer` nativo do Jetpack Compose para avaliar com exatidão os limites horizontais e verticais disponíveis para a citação e autor.
  - **Eliminação Completa de Frases Incompletas e Reticências ("..."):** Frases longas não sofrem mais corte ou truncamento com reticências nos widgets de relógio com citação diária (`DAILY_REFLECTION` e `DAILY_REFLECTION_STATS`) nem no bloco de citação inferior da tela inicial.
  - **Redução Gradual e Proporcional de Fonte:** O algoritmo reduz a tipografia em passos suaves (de 13sp até 8.5sp no relógio, e de 15sp até 11.5sp no bloco inferior), ajustando simultaneamente a entrelinha (`lineHeight`) para que 100% das 98 frases da curadoria caibam com perfeição e elegância visual sem desalinhar a altura do relógio.
  - **Escala Harmônica do Nome do Autor:** O autor da frase tem sua escala calibrada dinamicamente para manter a hierarquia visual em relação à frase e garantir que caiba em linha única sem quebras inadequadas.
  - **Adaptação Responsiva a Acessibilidade e Dispositivos:** Suporte total a telas estreitas, aparelhos compactos, larguras personalizadas de margem lateral e escalas de fonte aumentadas nas opções de acessibilidade do Android.
- **Correção de Compilação na Central de Logs (`AppLogViewerDialog`):** Ajustada a referência de propriedade do aplicativo para `AppInfo.label`, corrigindo o erro de compilação (`Unresolved reference 'name'`) durante as etapas de build.
- **Proteção Nula em Telemetria de Dispositivo (`DeviceInfoProvider`):** Adicionado tratamento com chamadas seguras na leitura de propriedades de hardware (`Build.SUPPORTED_ABIS`, etc.), prevenindo falhas de `NullPointerException` em ambientes de teste unitário.

---

### 🚀 Novidades e Melhorias da Versão 0.59.33

- **Central de Logs, Diagnóstico e Crashes Avançada (Motor LogFox Integrado):**
  - Implementação completa dos recursos do aplicativo open source **LogFox** diretamente no Victoria Launcher, transformando o launcher em uma ferramenta de diagnóstico e captura de logs de nível profissional.
  - Acesso direto via menu de contexto de aplicativos na Gaveta de Apps e na Tela Inicial ("Central de Logs & Crashes (LogFox)") e botão dedicado no cabeçalho do Inspetor de Processos (Root Inspector).

- **Console de Logs ao Vivo (Live Console):**
  - **Transmissão Contínua de Logcat:** Leitura de alta vazão via stream com suporte a UID e Epoch (`-v uid -v epoch`) ou Threadtime.
  - **Identificação Automática de Pacotes:** Resolução em tempo real de UIDs numéricos e `u0_a...` para o nome de pacote do aplicativo com cache LRU ultrarrápido e mapa de UIDs conhecidos do sistema.
  - **Filtros por Nível de Log (Log Level):** Chips seletores com contadores e cores dinâmicas Material 3 para Todos, Verbose (`V`), Debug (`D`), Info (`I`), Warning (`W`), Error (`E`) e Fatal (`F`).
  - **Barra de Pesquisa Expressiva:** Busca por tag, mensagem, PID ou pacote com alternador para sensibilidade a maiúsculas/minúsculas (`Aa`).
  - **Controles em Tempo Real:** Botão de pausa/retomada de transmissão, rolagem automática para o final e limpeza instantânea do buffer.
  - **Inspeção de Linha de Log:** Toque em qualquer linha para abrir a folha de detalhes com timestamp em milissegundos, UID, PID, TID, nível, tag, mensagem completa e opções para copiar mensagem, copiar linha ou filtrar pela tag.

- **Detector em Tempo Real de Crashes & ANRs:**
  - **Captura Multimodal de Falhas:** Monitoramento constante em segundo plano de Java Crashes (`FATAL EXCEPTION:` no `AndroidRuntime`), falhas nativas JNI / Sigfaults (`DEBUG`) e congelamentos de tela ANR (`ActivityManager`).
  - **Histórico Persistente de Crashes:** Armazenamento local de relatórios detalhados com aplicativo, tipo de falha, resumo do erro e stacktrace completo.
  - **Notificações Heads-Up de Alta Prioridade:** Alertas imediatos ao ocorrer um crash com ações interativas de 1 toque: "Ver Stacktrace", "Copiar Stacktrace" para a área de transferência e "Compartilhar Relatório".

- **Gravação de Sessões & Exportação Estilo LogFox (ZIP com Telemetria do Aparelho):**
  - **Gravação Alvo ou Global:** Grave logs isolados de um aplicativo específico ou capture todos os registros do sistema operacional.
  - **Controles da Notificação em Primeiro Plano:** Notificação em tempo real exibindo linhas capturadas, status e botões de Salvar, Pausar/Retomar e Descartar.
  - **Exportação para ZIP com Informações do Dispositivo:** Gera arquivos `.zip` em `Downloads/VictoriaLogs/` contendo o log gravado (`recorded.txt`) e um snapshot completo de hardware (`device.txt`: SDK, versão do Android, patch de segurança, fabricante, modelo, placa, produto, ABIs suportadas e fingerprint), imediatamente indexados pelo scanner de mídia.

- **Sistema de Filtros Personalizados (Include & Exclude Rules):**
  - Crie regras personalizadas de Whitelist (Incluir) ou Blacklist (Excluir) por tag, pacote de aplicativo, texto da mensagem, PID ou níveis de log específicos.
  - Chaves liga/desliga para habilitar ou desabilitar cada filtro instantaneamente com persistência em armazenamento local.

- **Gerenciador de Terminais & Concessão de Permissão com 1 Toque:**
  - Suporte a Superusuário (Root) e modo direto via permissão `android.permission.READ_LOGS`.
  - Botão de 1 toque para conceder automaticamente a permissão `READ_LOGS` usando Root (`pm grant`).
  - Botão para copiar o comando exato de ADB para a área de transferência para aparelhos sem root.

- **Correções de Compilação & Integração da UI:**
  - Correção na sincronização de funções assíncronas no `LogRecordingsManager`.
  - Resolução de importações e referências nos componentes Compose (`HomeScreen`, `AppListScreen` e `AppLogViewerDialog`).
  - Suporte à abertura da Central de Logs com identificadores de pacotes avulsos ou não indexados.
