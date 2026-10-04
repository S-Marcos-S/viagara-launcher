### 🚀 Novidades e Melhorias da Versão

- **Desligamento e Bloqueio de Tela via Root no Toque Duplo:**
  - O gesto de duplo toque na tela inicial agora desliga o display usando comando root (`input keyevent 26`) caso o aparelho possua acesso Superusuário (Root).
  - Preserva integralmente a animação circular de desligamento da tela (`ScreenOffEffect`), apagando o visor com a transição nativa e suave do sistema sem cortes abruptos.
  - Fallback transparente: se o dispositivo não possuir root ou a execução via shell falhar, o launcher recorre automaticamente ao serviço de acessibilidade existente (`ViagaraAccessibilityService`).
  - O menu de configurações agora reconhece a disponibilidade de root e não exige ativação prévia do serviço de acessibilidade para o duplo toque quando o root estiver ativo no aparelho.

- **Configurações e Monitoramento de Bateria:**
  - Nova aba de Configurações na tela de Bateria ao lado da aba Root.
  - Opção para redefinir o monitoramento de bateria automaticamente ao carregar até uma porcentagem configurável (ex: 80%, 90% ou 100%).
  - Personalização completa das métricas e seções exibidas na notificação em tempo real de consumo de bateria.

- **Otimização de Performance e Rolagem do Alfabeto (120 FPS):**
  - Remoção do atraso artificial de 30ms no arrasto pelo alfabeto na gaveta de aplicativos, permitindo resposta instantânea e fluida sincronizada com telas de alta taxa de atualização (90Hz / 120Hz).
  - Eliminação de milhares de alocações de objetos `Rect` e contenções de concorrência por segundo no `AppLaunchTransitionManager` durante a rolagem rápida de aplicativos, atualizando coordenadas in-place e aliviando completamente o Garbage Collector do Android.

