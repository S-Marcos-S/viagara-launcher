### 🚀 Novidades e Melhorias da Versão 0.59.20

- **Editor Independente de Espaçamento Lateral do Alfabeto:**
  - Adicionada alça dedicada no modo de edição de layout para ajustar exclusivamente o espaçamento lateral da coluna do alfabeto (`↔ Espaçamento lateral do alfabeto · Xdp`).
  - O editor geral de "Espaçamento lateral" agora ajusta exclusivamente os elementos da tela inicial (favoritos, relógio, widgets, gaveta de apps), sem mais afetar ou empurrar involuntariamente a barra do alfabeto.
  - Sincronização inteligente dos pontos de toque da borda (`EdgeTouchZone`) e do botão de ação com o novo espaçamento customizado do alfabeto.
  - Suporte completo no botão "Alinhar elementos" para restauração rápida das margens e alinhamentos ao padrão (20dp).

- **Otimização Extrema da Build de CI/CD (Build Exclusiva Release):**
  - Removida a compilação redundante de APK de depuração (Debug APK) no GitHub Actions, gerando agora unicamente o APK Release assinado.
  - Redução substancial no tempo total de build e entrega das atualizações, eliminando processamento desnecessário de compilação, DEX e packaging duplicados.
