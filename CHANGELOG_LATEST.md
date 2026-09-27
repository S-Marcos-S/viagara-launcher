### Novidades e Melhorias da Versão 0.59.29

- **Gerenciador de Tarefas Completo em Tela Inteira (Estilo Windows Task Manager):**
  - Adicionada a nova opção **"Gerenciador de tarefas"** no menu inferior da Tela Inicial, posicionada logo abaixo de "Configurações" para usuários com privilégios de superusuário (Root).
  - **Cabeçalho Minimalista e Clean:** Topo simplificado com a seta de voltar posicionada no topo com `statusBarsPadding()` e o seletor das abas "Processos" e "Desempenho", eliminando elementos supérfluos e maximizando o espaço útil de tela.
  - **Navegação por Gesto de Rolagem Horizontal (Swipe entre Telas):** Implementada a transição fluida entre as abas "Processos" e "Desempenho" ao arrastar a tela para os lados (`HorizontalPager`), com sincronização bidirecional instantânea das pílulas no cabeçalho e liberação automática do teclado ao alternar de tela.

- **Aba de Desempenho (Performance):**
  - **Gráfico de Ondas de CPU Fluido:** Gráfico oscilante contínuo com movimentação fluida de onda e preenchimento vertical em gradiente translúcido baseado na utilização em tempo real do processador via `/proc/stat` (com leitura direta e fallback via root contra restrições de SELinux).
  - **Colunas Invisíveis de Núcleos Integradas:** Sobreposto ao gráfico de ondas, duas colunas invisíveis exibem a velocidade em tempo real de cada um dos 8 núcleos (CPU 0 a 3 à esquerda, CPU 4 a 7 à direita) acompanhados da unidade "MHz".
  - **Memória (RAM):** Gráfico histórico dinâmico de consumo, memória total física, memória em uso, disponível e telemetria de zRAM / Swap ativa.
  - **Rede & Internet:** Gráfico de tráfego de dados, velocímetro instantâneo de Download e Upload (↓ / ↑), total de dados trafegados e tipo de conexão ativa (Wi-Fi, Dados Móveis).
  - **Estabilidade e Espaçamento no Cabeçalho de Rede:** O atalho para o monitor de rede foi posicionado como texto "Ver mais" abaixo do velocímetro instantâneo de download/upload, liberando todo o espaço horizontal para que o título "Rede & Internet" caiba perfeitamente na mesma linha sem truncamento.
  - **Integração com Monitor de Rede por Aplicativo:** Adicionado atalho "Ver mais" que abre a nova tela de **Monitor de Rede por App** (baseada no NetSpeedIndicator, portada e adaptada ao estilo Material Expressive do launcher). Exibe histórico de consumo com título consistente e limpo ("Histórico de Consumo"), seletor de períodos (Hoje, 7 dias, 30 dias), gráficos de barras por horário/dia, visualização detalhada por aplicativo e ranking dos apps que mais consumiram dados.
  - **Visualização de Área Colorida nos Gráficos:** Preenchimento de cor vibrante e nítido sob as linhas de todos os gráficos (CPU, Memória, Rede), conferindo destaque visual imediato e profundidade na leitura do histórico.
  - **Unidade Gráfica (GPU):** Telemetria de GPU via sysfs (Qualcomm Adreno / ARM Mali) com leitura de frequência de clock e carga.
  - **Armazenamento:** Indicador de ocupação e espaço livre no armazenamento interno do dispositivo.

- **Aba de Processos (Processes):**
  - **Normalização e Correção do Cálculo de CPU:** Corrigida a medição por processo e aplicativo que excedia 100% devido ao modo Irix do Linux em processadores multi-core. O cálculo agora normaliza a taxa pelo número total de núcleos (estilo Windows Task Manager / modo Solaris) e limita o teto a 100%, garantindo consistência com a capacidade global do processador.
  - **Categorização em Primeiro e Segundo Plano:** Lista agrupada de aplicativos ativos com status dinâmico (`FOCO` para o aplicativo em primeiro plano e `2º Plano` para processos em background).
  - **Métricas por Processo:** Exibição em tempo real de consumo de CPU (%) e memória RAM (MB) consumida por cada processo e aplicativo.
  - **Inspeção de Aplicativo com 1 Toque:** Toque em qualquer aplicativo para abrir a tela de diagnóstico avançado com métricas completas de memória (Dalvik, Native, Graphics), processos filhos, conexões de rede em tempo real, permissões perigosas, limpeza de cache e gravação de logs.
  - **Ação Rápida de Finalização:** Botão direto para finalizar processos indesejados (`kill -9`) ou forçar parada.
  - **Processos do Sistema:** Seção colapsável opcional para visualizar daemons e serviços do sistema Android.
  - **Barra de Pesquisa Expressiva:** Filtro em tempo real por nome do app, pacote ou PID.

- **Aprimoramento da Física e Gestos do Menu Rápido (Bottom Sheet):**
  - **Inércia e Continuidade no Gesto de Subida:** Ao rolar o menu para baixo e depois jogá-lo para cima ("fling up"), a folha agora respeita a velocidade e o vetor de arraste do usuário (`initialVelocity`), continuando a subida e reabrindo na mesma velocidade com desaceleração orgânica.
  - **Eliminação de Fechamento Prematuro e Saltos Visuais:** Corrigida a condição de descarte acidental que fechava o menu caso o movimento de subida não chegasse ao topo imediato, e removido o snap instantâneo de offset que causava um salto visual brusco durante a transição de saída.

- **Correção da Telemetria de Rede em Tempo Real e Desacoplamento de Disco (I/O):**
  - **Suporte Nativo a Mapas eBPF do Kernel (Android 9 ao 16):** Adicionada a leitura direta do mapa de contadores em tempo real `mAppUidStatsMap` via `dumpsys netstats` no script de superusuário, garantindo medição instantânea e contínua de tráfego (Download e Upload) por UID/aplicativo em versões modernas do Android onde interfaces legadas como `xt_qtaguid` foram extintas.
  - **Separação Rigorosa entre Rede e Armazenamento:** Eliminada a inclusão indevida de taxas de leitura e gravação em disco (`/proc/[pid]/io`, `rchar` e `wchar`) no cálculo de velocidade de download e upload por aplicativo.
  - **Fim do Consumo de Rede Fantasma na Launcher:** Resolvido o consumo elevado de rede exibido indevidamente ao inspecionar o Niagara/Viagra Launcher, que era provocado por rotinas de leitura de arquivos e cache em disco (`rchar`) sendo confundidas com download.
  - **Correção de Upload Maior que Download na Play Store:** Corrigida a distorção onde downloads na Google Play Store exibiam altas taxas de upload, que na verdade eram blocos do instalador sendo gravados em disco (`wchar`) durante o processo de download e descompactação de pacotes APK.
  - **Fallback Robusto para Métricas Globais do Dispositivo:** Implementada leitura de `/proc/net/dev` como contingência para garantir telemetria global ininterrupta de Download e Upload na aba Desempenho.

- **Indicador de Progresso Ondulado Material Expressive no Reprodutor de Mídia (`NowPlayingWidget`):**
  - **Linha Ondulada Orgânica e Fluida:** Substituído o indicador linear estático padrão pela autêntica linha ondulada/sinusoidal com física e estética do Material Expressive / Android 13+.
  - **Animação Contínua e Achatamento Inteligente ao Pausar:** Enquanto a música toca, a onda se move suavemente de forma contínua com atenuação orgânica nas pontas; ao pausar ou ao buscar (scrubbing), a onda transiciona suavemente com animação de mola (*spring*) para uma elegante linha reta com pontas arredondadas.
  - **Controle de Busca Interativo por Toque e Arraste (Scrubbing):** Adicionado suporte para tocar ou arrastar ao longo da barra de progresso para avançar ou retroceder a música diretamente da tela inicial, com cabeçote de reprodução estilizado em pílula e achatamento em tempo real sob o dedo.
  - **Correção de Recorte Vertical (Clipping):** Ajustadas as dimensões e alterada a restrição de altura do card para `heightIn(min = heightDp.dp)`, impedindo que a barra de progresso fosse empurrada para fora dos limites do card ou cortada pelo fundo arredondado.
  - **Correção da Sincronização do Progresso da Faixa (Base de Tempo `elapsedRealtime`):** Corrigida a divergência entre a base de tempo do Android (`PlaybackState.getLastPositionUpdateTime()` em `SystemClock.elapsedRealtime`) e o relógio de época (`System.currentTimeMillis()`), que inflava o tempo transcorrido e travava a barra de progresso instantaneamente em 100%. O indicador agora acompanha com fidelidade a evolução real da faixa em tempo real (0% a 100%), exibindo a onda no trecho já reproduzido e a linha atenuada no trecho restante.
  - **Suporte a Transmissões e Rádios ao Vivo (Duração Indeterminada):** Corrigido o sumiço do indicador quando a duração da mídia é desconhecida ou zero (`durationMs <= 0L`), exibindo a onda expressiva contínua ao longo de toda a extensão do bloco.

- **Estabilidade Estrutural e de Quebra de Linha nos Relógios de Reflexão Diária (`DAILY_REFLECTION` e `DAILY_REFLECTION_STATS`):**
  - **Fim da Variação de Linhas da Frase ao Mudar a Hora:** Resolvido o problema onde o bloco de horário com medição intrínseca variável (`IntrinsicSize.Min`) mudava de largura conforme os dígitos do relógio (por exemplo, "11:11" sendo mais estreito que "20:00"), alterando a largura disponível para a frase e fazendo com que ela ocupasse mais ou menos linhas ao longo do dia.
  - **Largura Estável e Proporcional para o Horário:** O bloco do relógio agora possui largura estável calculada proporcionalmente à escala de fonte (`widthFactor` e `fontScale`), garantindo que o espaço destinado à citação permaneça 100% constante em qualquer hora do dia.
  - **Numerais Tabulares (`fontFeatureSettings = "tnum"`):** Aplicado suporte a dígitos tabulares de largura uniforme no relógio, impedindo oscilações e saltos de caracteres na transição de minutos.
  - **Imutabilidade Visual da Divisória e Alinhamentos:** A divisória vertical central e a estrutura completa do widget permanecem rigorosamente fixas e estáveis a cada atualização de hora ou minuto.
