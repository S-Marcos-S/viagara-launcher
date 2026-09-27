### 🚀 Novidades e Correções da Versão 0.59.27

- **Correção Definitiva de Detecção de Processos & Falsos Positivos em Segundo Plano:**
  - Corrigido o problema em que aplicativos inativos ou fechados apareciam erroneamente como "EM SEGUNDO PLANO".
  - O script de inspeção e os analisadores de processos agora filtram estritamente comandos de interpretadores (`su`, `sh`, `bash`, `toybox`, `grep`) e validam que os PIDs e argumentos pertençam legitimamente ao UID do aplicativo inspecionado.
  - Aplicativos que não possuem processos em execução agora são corretamente identificados com o status **PARADO / INATIVO**.

- **Velocidade de Download e Upload em Tempo Real em Local Separado:**
  - Adicionado painel dedicado e destacado para as taxas instantâneas de transferência de rede (**Download** e **Upload**).
  - Indicadores ao vivo com badges dinâmicos (`AO VIVO` / `INATIVO`) e formatação em alta precisão (`↓ 3.8 MB/s` e `↑ 210 KB/s`).

- **Consumo de Dados Nativo com Seletor de Período (Hoje / 7 Dias / Total):**
  - Implementada integração direta com o sistema nativo de estatísticas de rede do Android (`NetworkStatsManager` via `PACKAGE_USAGE_STATS`), garantindo medição precisa e oficial do consumo de dados (Wi-Fi + Dados Móveis).
  - Adicionado seletor interativo em abas ("Hoje", "7 Dias" e "Total"), permitindo alternar instantaneamente para ver quanto o app consumiu no dia de hoje, na última semana ou no total acumulado.
  - Aba "Rede" atualizada com detalhamento comparativo completo do histórico de consumo e conexões ativas.
