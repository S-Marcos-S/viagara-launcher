### 🚀 Novidades e Melhorias da Versão 0.59.33

- **Central de Logs, Diagnóstico e Crashes Avançada (Motor LogFox Integrado):**
  - Implementação completa dos recursos do aplicativo open source **LogFox** diretamente no Victoria Launcher, transformando o launcher em uma ferramenta de diagnóstico e captura de logs de nível profissional.
  - Acesso direto via menu de contexto de aplicativos na Gaveta de Apps e na Tela Inicial ("Central de Logs & Crashes (LogFox)") e botão dedicado no cabeçalho do Inspetor de Processos (Root Inspector).

- **Console de Logs ao Vivo (Live Console):**
  - **Transmissão Contínua de Logcat:** Leitura de alta vazão via stream com suporte a UID e Epoch (`-v uid -v epoch`) ou Threadtime.
  - **Identificação Automática de Pacotes:** Resolução em tempo real de UIDs numéricos e `u0_a...` para o nome de pacote do aplicativo com cache LRU ultrarrápido e mapa de UIDs conhecidos do sistema.
  - **Filtros por Nível de Log (Log Level):** Chips seletores com contadores e cores dinâmicas Material 3 para Todos, Verbose (`V`), Debug (`D`), Info (`I`), Warning (`W`), Error (`E`) e Fatal (`F`).
  - **Barra de Pesquisa Expressiva:** Busca por tag, mensagem, PID ou pacote com alternador para sensibilidade a maiúsculas/minúsculas (`Aa`).
  - **Controles em Tempo Real:** Botão de pausa/retomada de transmissão, rolagem automática para o final e limpeza instantânea do buffer.
  - **Inspeção de Linha de Log:** Toque em qualquer linha para abrir a folha de detalhes com timestamp em milissegundos, UID, PID, TID, nível, tag, mensagem completa e opções para copiar mensagem, copiar linha ou filtrar pela tag.

- **Detector em Tempo Real de Crashes & ANRs:**
  - **Captura Multimodal de Falhas:** Monitoramento constante em segundo plano de Java Crashes (`FATAL EXCEPTION:` no `AndroidRuntime`), falhas nativas JNI / Sigfaults (`DEBUG`) e congelamentos de tela ANR (`ActivityManager`).
  - **Histórico Persistente de Crashes:** Armazenamento local de relatórios detalhados com aplicativo, tipo de falha, resumo do erro e stacktrace completo.
  - **Notificações Heads-Up de Alta Prioridade:** Alertas imediatos ao ocorrer um crash com ações interativas de 1 toque: "Ver Stacktrace", "Copiar Stacktrace" para a área de transferência e "Compartilhar Relatório".

- **Gravação de Sessões & Exportação Estilo LogFox (ZIP com Telemetria do Aparelho):**
  - **Gravação Alvo ou Global:** Grave logs isolados de um aplicativo específico ou capture todos os registros do sistema operacional.
  - **Controles da Notificação em Primeiro Plano:** Notificação em tempo real exibindo linhas capturadas, status e botões de Salvar, Pausar/Retomar e Descartar.
  - **Exportação para ZIP com Informações do Dispositivo:** Gera arquivos `.zip` em `Downloads/VictoriaLogs/` contendo o log gravado (`recorded.txt`) e um snapshot completo de hardware (`device.txt`: SDK, versão do Android, patch de segurança, fabricante, modelo, placa, produto, ABIs suportadas e fingerprint), imediatamente indexados pelo scanner de mídia.

- **Sistema de Filtros Personalizados (Include & Exclude Rules):**
  - Crie regras personalizadas de Whitelist (Incluir) ou Blacklist (Excluir) por tag, pacote de aplicativo, texto da mensagem, PID ou níveis de log específicos.
  - Chaves liga/desliga para habilitar ou desabilitar cada filtro instantaneamente com persistência em armazenamento local.

- **Gerenciador de Terminais & Concessão de Permissão com 1 Toque:**
  - Suporte a Superusuário (Root) e modo direto via permissão `android.permission.READ_LOGS`.
  - Botão de 1 toque para conceder automaticamente a permissão `READ_LOGS` usando Root (`pm grant`).
  - Botão para copiar o comando exato de ADB para a área de transferência para aparelhos sem root.

- **Correções de Compilação & Integração da UI:**
  - Correção na sincronização de funções assíncronas no `LogRecordingsManager`.
  - Resolução de importações e referências nos componentes Compose (`HomeScreen`, `AppListScreen` e `AppLogViewerDialog`).
  - Suporte à abertura da Central de Logs com identificadores de pacotes avulsos ou não indexados.

