### 🚀 Novidades e Correções da Versão 0.59.26

- **Telemetria de Rede Multi-Camadas em Tempo Real:**
  - Implementada arquitetura de monitoramento multi-camadas que combina **I/O do processo direto do kernel (`/proc/<pid>/io`)**, **estatísticas de sockets TCP (`ss -tipn`)**, **Netstats com suporte multi-linha (`dumpsys netstats detail`)** e tabelas `xt_qtaguid`.
  - **Velocidade de Download e Upload Garantida:** A taxa instantânea de transferência (ex: `↓ 3.8 MB/s` / `↑ 210 KB/s`) agora é calculada através das chamadas de sistema I/O dos processos ativos (`rchar`/`wchar`) e contadores do kernel, eliminando qualquer atraso de sincronização do banco de dados do sistema.

- **Captura Total para Google Play Store & Download Manager:**
  - Acompanhamento simultâneo de todo o tráfego de download gerado pela Play Store (`com.android.vending`), subprocessos como `com.android.vending:download_service` e o Provedor de Downloads do sistema (`com.android.providers.downloads`).
