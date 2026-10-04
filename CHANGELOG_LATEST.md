### 🚀 Novidades e Melhorias da Versão

- **Desligamento e Bloqueio de Tela via Root no Toque Duplo:**
  - O gesto de duplo toque na tela inicial agora desliga o display usando comando root (`input keyevent 26`) caso o aparelho possua acesso Superusuário (Root).
  - Preserva integralmente a animação circular de desligamento da tela (`ScreenOffEffect`), apagando o visor com a transição nativa e suave do sistema sem cortes abruptos.
  - Fallback transparente: se o dispositivo não possuir root ou a execução via shell falhar, o launcher recorre automaticamente ao serviço de acessibilidade existente (`ViagaraAccessibilityService`).
  - O menu de configurações agora reconhece a disponibilidade de root e não exige ativação prévia do serviço de acessibilidade para o duplo toque quando o root estiver ativo no aparelho.

- **Configurações e Monitoramento de Bateria:**
  - Nova aba de Configurações na tela de Bateria.
  - Opção para redefinir o monitoramento de bateria automaticamente ao carregar até uma porcentagem configurável.
  - Personalização das métricas exibidas na notificação em tempo real.
