### Novidades e Melhorias da Versão 0.59.31

- **Implementação do Protocolo `GestureNavContract` e Animação de Retorno do Ícone (Estilo Niagara Launcher):**
  - **Suporte ao Handshake de Gestos do Sistema (`GestureNavContract`):**
    - Implementada a captura e resposta ao contrato de navegação por gestos do Android (`gesture_nav_contract_v1`) em `MainActivity` (`onCreate` e `onNewIntent`).
    - Ao realizar o gesto de deslizar para cima para voltar à tela inicial, o launcher intercepta a requisição do SystemUI e envia as coordenadas exatas (`RectF`) do ícone do aplicativo fechado via `Messenger` (`gesture_nav_contract_icon_position`), permitindo que o sistema operacional encolha e realize o morphing da janela diretamente sobre o ícone na tela inicial.
  - **Animação Física de Reentrada e Fixação do Ícone na Lista (*Icon Settle Spring*):**
    - Quando o aplicativo é fechado e a tela inicial volta ao primeiro plano (seja via gesto de navegação, botão de voltar ou navegação por 3 botões), o ícone do aplicativo correspondente realiza uma animação orgânica de reentrada com física de mola elástica (`Spring.DampingRatioMediumBouncy` e `Spring.StiffnessLow`), partindo de uma leve expansão e deslocamento dinâmico até travar suavemente no seu devido lugar na lista.
    - Suporte integrado em todas as superfícies da interface:
      - **Favoritos da Tela Inicial (`FavoriteRow`):** Animação fluida da linha e do ícone no retorno.
      - **Pastas em Linha e Flutuantes (`FolderRow` e `FolderFloatingDialog`):** Atualização dinâmica de coordenadas de todos os membros.
      - **Gaveta de Aplicativos (`AppListScreen`):** Animação de fixação do ícone na lista alfabética.
      - **Pesquisa (`SearchScreen`) e Botão de Ação Dinâmica (`DynamicActionButton`).**
  - **Abertura Expressiva com Máscara e Contexto de Atividade (`makeClipRevealAnimation`):**
    - Atualizada a inicialização de aplicativos em `AppRepository` para utilizar `ActivityOptions.makeClipRevealAnimation`, criando o efeito de revelação circular/retangular a partir do ícone tocado.
    - Disparo de `startActivity` a partir do contexto da `Activity` em primeiro plano (`MainActivity`), impedindo que o WindowManager descarte o bundle de animação personalizada.
