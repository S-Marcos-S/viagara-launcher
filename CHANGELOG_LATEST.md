### 🚀 Novidades e Melhorias da Versão 0.59.23

- **Novo Inspetor e Monitor de Aplicativos via Root:**
  - Adicionada opção exclusiva para usuários com acesso Root ao clicar e segurar qualquer aplicativo: **Inspecionar App (Root)**.
  - Tela translúcida profissional com efeito de desfoque fosco (*frosted glass blur*) exibindo diagnóstico avançado em tempo real.

- **Diagnóstico Completo de Sistema & Hardware:**
  - **Status de Execução:** Identifica com precisão se o app está em **Primeiro Plano** (com atividade aberta na tela), em **Segundo Plano** (serviço ativo ou processo em cache) ou **Inativo / Parado**.
  - **Telemetria de CPU & Memória:** Monitoramento do percentual de uso do processador e divisão minuciosa da memória RAM PSS (*Dalvik/ART Heap*, *Native Heap* e *Gráficos/GPU*).
  - **Tráfego de Rede & Conexões Ativas:** Monitoramento do volume de dados trafegados (*Download / Upload*) e listagem de conexões TCP ativas com servidores externos em tempo real.
  - **Processos & Serviços:** Listagem de todos os PIDs ativos com botão individual para encerrar processos via root, além da identificação de serviços de segundo plano.
  - **Mapeamento de Permissões:** Exibição clara e categorizada das permissões concedidas e negadas, destacando acessos críticos (Câmera, Localização, Microfone, Armazenamento).

- **Ações Rápidas de Administração Root:**
  - Botão de **Forçar Parada** (`am force-stop`) direto pelo diálogo.
  - Botão de **Limpeza de Cache Silenciosa** via Superusuário.
  - Botão para **Iniciar Aplicativo** e atualização sob demanda.
