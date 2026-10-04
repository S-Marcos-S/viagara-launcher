### 🚀 Novidades e Melhorias da Versão

- **Desligamento e Bloqueio de Tela via Root no Toque Duplo:**
  - O gesto de duplo clique na tela inicial agora desliga o display através de comando root (`input keyevent 26`) caso o aparelho possua acesso Superusuário (Root).
  - Preserva integralmente a animação circular de desligamento da tela (`ScreenOffEffect`), apagando o visor com a transição nativa e suave do sistema sem cortes abruptos.
  - Fallback transparente: se o dispositivo não possuir root ou a execução via shell falhar, o launcher recorre automaticamente ao serviço de acessibilidade existente (`ViagaraAccessibilityService`).
  - O menu de configurações agora reconhece a disponibilidade de root e não exige ativação prévia do serviço de acessibilidade caso o root esteja ativo no aparelho.

- **Nova Aba de Configurações na Bateria:**
  - Configurações adicionais para gerenciamento de bateria e notificações de consumo.
