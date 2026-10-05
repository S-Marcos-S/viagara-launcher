### 🚀 Novidades e Melhorias da Versão v0.59.54

- **Novo Monitor Ultraleve de Recursos em Segundo Plano (`BackgroundAnomalyWatcher`):**
  - **Eficiência Energética Extrema (Impacto Zero no Deep Sleep):** Implementado sistema adaptativo de detecção de anomalias que preserva 100% o sono profundo do aparelho. Enquanto a tela está desligada, o processador repousa completamente sem wakelocks ou alarmes contínuos. Ao religar ou desbloquear o aparelho, é calculada a fotografia diferencial de dados e processamento ocorrida durante o período de repouso.
  - **Detecção de Tráfego Abusivo de Internet em Background:** Monitora o tráfego de dados por UID utilizando chamadas in-memory diretas do kernel/eBPF (`TrafficStats`), alertando quando um aplicativo em segundo plano transfere volumes excessivos de dados móveis ou Wi-Fi (com a tela acesa ou apagada).
  - **Detecção de Abuso Contínuo de Processador (CPU):** Rastreamento de processos em segundo plano com uso elevado e sustentado de CPU por ciclos consecutivos, evitando falsos-positivos de picos momentâneos.
  - **Detecção Event-Driven de Memória RAM sob Pressão:** Integrado aos eventos nativos do sistema (`ComponentCallbacks2.onTrimMemory`), alertando quando processos de segundo plano retêm alto consumo de memória RAM enquanto o sistema operacional enfrenta restrição de memória.

- **Notificações Acionáveis com Ações Rápidas (`BackgroundAnomalyNotificationManager` & `BackgroundAnomalyReceiver`):**
  - **Canal de Alta Prioridade:** Notificações detalham o nome do app, a métrica exata e a duração do evento (ex: "Transferiu 48 MB em segundo plano enquanto a tela esteve apagada por 15 min").
  - **Ações Imediatas com 1 Toque:** Ações na própria notificação para `[Forçar parada]` (via root imediato ou tela de configurações do sistema), `[Inspecionar]` (abre direto o Gerenciador de Tarefas no app em questão) e `[Silenciar (1h)]`.

- **Painel de Configuração e Histórico no Gerenciador de Tarefas (`BackgroundAnomalySettingsDialog` & `TaskManagerScreen`):**
  - **Acesso Rápido na Barra Superior:** Adicionado botão com ícone de notificações no topo do Gerenciador de Tarefas para alternar o monitoramento, configurar sensibilidade (Alta, Equilibrada, Baixa), escolher quais recursos vigiar e consultar a lista de anomalias recentes.

- **Integração na Tela de Estatísticas de Bateria (`BatteryStatsScreen` & `BatteryStatsViewModel`):**
  - **Novo Card de Alertas em Segundo Plano na Visão Geral:** Adicionado o card `BackgroundAnomalyCard` na aba Visão Geral, permitindo ligar e desligar com um toque as notificações de consumo abusivo em segundo plano e acessar rapidamente o diálogo de configuração e histórico.
  - **Acesso Rápido na Barra Superior da Bateria:** Inserido botão de ação com ícone de notificação na barra superior (`TopAppBar`) da tela de bateria para gerenciar os alertas a qualquer momento.
