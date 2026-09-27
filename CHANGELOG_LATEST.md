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
  - **Estabilidade do Cabeçalho de Rede:** O cabeçalho do cartão de Rede agora possui altura e posições travadas com tipografia otimizada e limite de linha única, impedindo que o título e o ícone se desloquem ou saltem conforme as taxas de velocidade flutuam em tempo real.
  - **Integração com Monitor de Rede por Aplicativo (+):** Adicionado botão de atalho `+` no cabeçalho do cartão de Rede que abre a nova tela de **Monitor de Rede por App** (baseada no NetSpeedIndicator, portada e adaptada ao estilo Material Expressive do launcher). Permite visualizar consumo total e histórico (Hoje, 7 dias, 30 dias), gráficos de barras por horário/dia, visualização de segmentos coloridos por aplicativo e lista dos apps que mais consumiram internet (Móvel vs Wi-Fi).
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
