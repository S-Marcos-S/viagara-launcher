### 🚀 Novidades e Melhorias da Versão 0.59.29

- **Gerenciador de Tarefas Completo em Tela Inteira (Estilo Windows Task Manager):**
  - Adicionada a nova opção **"Gerenciador de tarefas"** no menu inferior da Tela Inicial, posicionada logo abaixo de "Configurações" para usuários com privilégios de superusuário (Root).
  - Tela cheia nativa integrada com o design **Material Expressive** (Material 3 avançado com bordas dinâmicas, superfícies tonais e microinterações fluidas).

- **Aba de Processos (Processes):**
  - **Categorização em Primeiro e Segundo Plano:** Lista agrupada de aplicativos ativos com status dinâmico (`FOCO` para o aplicativo em primeiro plano e `2º Plano` para processos em background).
  - **Métricas por Processo:** Exibição em tempo real de consumo de CPU (%) e memória RAM (MB) consumida por cada processo e aplicativo.
  - **Inspeção de Aplicativo com 1 Toque:** Toque em qualquer aplicativo para abrir a tela de diagnóstico avançado com métricas completas de memória (Dalvik, Native, Graphics), processos filhos, conexões de rede em tempo real, permissões perigosas, limpeza de cache e gravação de logs.
  - **Ação Rápida de Finalização:** Botão direto para finalizar processos indesejados (`kill -9`) ou forçar parada.
  - **Processos do Sistema:** Seção colapsável opcional para visualizar daemons e serviços do sistema Android.
  - **Barra de Pesquisa Expressiva:** Filtro em tempo real por nome do app, pacote ou PID.

- **Aba de Desempenho (Performance):**
  - **Processador (CPU):** Gráfico histórico em tempo real com curva Bézier e gradiente suave dos últimos 30 segundos, exibição da frequência por núcleo individual (Core 0 a Core 7 ao vivo), frequência máxima, contagem de threads e tempo de atividade (Uptime) do sistema.
  - **Memória (RAM):** Gráfico histórico dinâmico de consumo, memória total física, memória em uso, disponível e telemetria de zRAM / Swap ativa.
  - **Rede & Internet:** Gráfico de tráfego de dados, velocímetro instantâneo de Download e Upload (↓ / ↑), total de dados trafegados e tipo de conexão ativa (Wi-Fi, Dados Móveis).
  - **Unidade Gráfica (GPU):** Telemetria de GPU via sysfs (Qualcomm Adreno / ARM Mali) com leitura de frequência de clock e carga.
  - **Armazenamento:** Indicador de ocupação e espaço livre no armazenamento interno do dispositivo.
  - **Atualização ao Vivo (Live):** Badge pulsante "AO VIVO" com opção de pausar e atualizar manualmente.
