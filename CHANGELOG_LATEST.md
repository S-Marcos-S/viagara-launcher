### 🚀 Novidades da Versão v0.59.53

- **Central de Ações do Aplicativo no Inspetor Root:**
  - Fusão das seções "Memória" e "Processos" em uma visão unificada e integrada.
  - Nova aba "Ações" reunindo todas as operações disponíveis para o app:
    - Abrir aplicativo
    - Forçar parada via root
    - Limpar caches interno e externo
    - Abrir detalhes do aplicativo nas configurações do sistema
    - Desinstalar aplicativo via root
    - Impedir uso em segundo plano (`appops RUN_IN_BACKGROUND ignore`)

- **Rolagem e Indicador de Widgets na Tela Inicial:**
  - Rolagem cíclica infinita entre os widgets (do primeiro direto para o último e vice-versa).
  - O indicador de páginas (dots) agora não consome espaço no layout nem desloca os aplicativos favoritos para baixo ao surgir na tela.

- **Telemetria de Rede e Correções na Bateria:**
  - Nova opção de telemetria ao vivo de consumo de rede (Download e Upload) na notificação de bateria, configurável na aba de Configurações.
  - Correção na lista de consumo por aplicativo: remoção da duplicidade de rótulo em primeiro plano e restauração da exibição correta do tempo de serviço em segundo plano.