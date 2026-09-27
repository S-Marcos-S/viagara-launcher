### 🚀 Novidades e Correções da Versão 0.59.25

- **Correção Crítica de Compatibilidade Shell (Toybox Grep):**
  - Resolvido problema em que o utilitário nativo de terminal do Android (`/system/bin/toybox grep`) falhava ao executar comandos com escapes de regex incompatíveis (`\b`, `\s`), o que impedia a captura dos dados de CPU, rede e conexões.
  - Scripts do Inspetor Root padronizados para garantir 100% de compatibilidade em qualquer versão do Android.

- **Telemetria Completa de Download da Play Store (Dual-UID Tracking):**
  - Implementado monitoramento dual entre o processo da Google Play Store (`com.android.vending`) e o Gerenciador de Downloads do Android (`com.android.providers.downloads`).
  - Velocidade de download em tempo real (ex: `↓ 3.2 MB/s`) e consumo total de dados agora são exibidos dinamicamente durante qualquer download ou atualização de aplicativo.

- **Medição Robusta de CPU em Tempo Real (`top` Dinâmico):**
  - Algoritmo aprimorado de leitura de processos com identificação dinâmica de colunas de tempo e processamento, capturando instantaneamente subprocessos como `com.android.vending:download_service` e processos nativos.

- **Detecção Precisa de Aplicativos em Primeiro e Segundo Plano:**
  - Validação estrita da janela em foco (`mCurrentFocus`, `mFocusedApp`) que previne que aplicativos em segundo plano ou na tela de Recentes sejam marcados incorretamente como ativos na tela.
