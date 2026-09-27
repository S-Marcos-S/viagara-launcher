### 🚀 Novidades e Melhorias da Versão 0.59.28

- **Captura e Gravação de Logs (Logcat) por Aplicativo com Root:**
  - Adicionada a nova opção **"Capturar logs (Root)"** ao clicar e segurar em qualquer aplicativo (na Tela Inicial, na Gaveta de Apps e dentro de Pastas Flutuantes). Disponível exclusivamente para usuários com acesso root.
  - **Filtro Específico e Isolado:** Captura estritamente os logs do aplicativo alvo através do seu UID e PIDs dedicados, descartando ruídos de outros aplicativos ou serviços do sistema operacional.
  - **Notificação de Gravação em Andamento:** Ao iniciar a gravação, uma notificação contínua é exibida permitindo **Salvar log** ou **Apagar** (descartar).
  - **Salvamento Direto na Pasta Downloads:** Ao salvar, o arquivo `.txt` do log é gerado em `Downloads/` com indexação imediata no armazenamento do dispositivo. A notificação de gravação é automaticamente substituída por uma nova notificação de conclusão informando o caminho do arquivo e oferecendo ações rápidas para **Compartilhar**, **Ver arquivo** ou **Apagar**.
  - **Detecção Automática de Falhas (Crash Catch):** Caso o aplicativo gravado sofra um erro fatal (`FATAL EXCEPTION`, `SIGSEGV`, `SIGABRT` ou encerramento inesperado), o log é salvo automaticamente no mesmo instante e o usuário é notificado com as opções de compartilhamento e análise imediata.

- **Desinstalação de Aplicativos Direto pelo Menu de Contexto:**
  - Adicionada a opção **"Desinstalar"** ao clicar e segurar em qualquer aplicativo com confirmação e ações inteligentes.
  - Em dispositivos com Root, o aplicativo é desinstalado silenciosamente via linha de comando (`pm uninstall` e `pm uninstall --user 0`).
  - Para aplicativos do sistema, exibe diálogo protetor com aviso de risco de instabilidade antes de efetuar a desinstalação via root.
  - Fallback automático para o instalador padrão do Android (`PackageInstaller`) em dispositivos sem root.
