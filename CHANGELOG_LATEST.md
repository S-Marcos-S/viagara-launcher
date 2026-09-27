### 🚀 Novidades e Melhorias da Versão 0.59.16

- **Bloqueio Modal de Toques no Alfabeto (Comportamento estilo Niagara Launcher):**
  - **Bloqueio de Cliques com Segundo Dedo:** Adicionada camada modal de proteção que intercepta e consome toques secundários enquanto o usuário estiver rolando ou segurando a barra alfabética, impedindo a abertura acidental de aplicativos ou configurações com outro dedo.
  - **Desativação de Interatividade das Linhas (`AppRow`):** Modificadores de clique e gravação de toque foram desativados dinamicamente durante o estado de *scrubbing*, garantindo que toques simultâneos sejam ignorados.
  - **Prevenção de Conflitos de Toque:** Toques secundários no próprio `EdgeTouchZone` são consumidos para evitar interrupções ou perda de foco do gesto ativo.

- **Otimização de Performance e Fluidez da Rolagem:**
  - **Scroll com Cadência Estável (Throttle de Layout):** Implementado controle de cadência inteligente no `listState.scrollToItem` (~30ms) durante o arraste rápido pelo alfabeto. Isso elimina a sobrecarga de disparar dezenas de recriações completas de layout em fração de segundo no Compose, mantendo o movimento visual contínuo e responsivo sem engasgos ou quedas de quadros.
  - **Pouso Instantâneo no Toque ou Soltura:** Cliques pontuais no alfabeto ou a finalização do gesto mantêm resposta imediata (0ms) na letra de destino.
