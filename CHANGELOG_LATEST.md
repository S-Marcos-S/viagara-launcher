### 🚀 Novidades e Correções da Versão 0.59.24

- **Telemetria de Rede Moderna (eBPF & Netstats):**
  - Corrigida a coleta de tráfego de internet em dispositivos com Android 9+, substituindo a leitura obsoleta do kernel `/proc/uid_stat` pela consulta ao subsistema eBPF (`dumpsys netstats detail` e `TrafficStats`).
  - **Velocidade em Tempo Real:** Adicionado indicador dinâmico de taxa de transferência instantânea em Download e Upload (ex: `↓ 3.4 MB/s`, `↑ 150 KB/s`).

- **Monitoramento em Tempo Real & Auto-Refresh:**
  - O painel do Inspetor Root agora atualiza dinamicamente a cada 2 segundos enquanto estiver aberto na tela, com selo pulsante **"AO VIVO"**.
  - Acompanhe downloads da Google Play Store, picos de processamento e alocação de memória ao vivo.

- **Telemetria de CPU em Tempo Real via `top`:**
  - Corrigido o cálculo de uso do processador utilizando `top -b -n 1 -q` e `ps -o ARGS`, evitando o truncamento do nome do pacote pelo kernel Linux (`comm` de 15 chars) e refletindo a carga real da CPU.

- **Memória RAM Direta do Kernel (`smaps_rollup`):**
  - Implementada leitura de alta precisão via `/proc/<pid>/smaps_rollup`, capturando PSS, Heap Dalvik/ART anônimo e alocações nativas/código para todos os processos vivos do aplicativo, com suporte completo a aplicativos multi-processo (como Google Play Store).

- **Detecção Rigorosa de Foco (Primeiro vs Segundo Plano):**
  - Corrigido o identificador de status para validar se a atividade em foco (`mCurrentFocus`, `topResumedActivity`) pertence especificamente ao pacote inspecionado. Aplicativos minimizados ou em tela de recentes agora são corretamente classificados como **"EM SEGUNDO PLANO"**.
