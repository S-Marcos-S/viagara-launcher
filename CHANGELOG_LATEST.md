### Novidades e Melhorias da Versão 0.59.30

- **Animações Nativas de Expansão e Compactação (App Launch & Exit) no Estilo Padrão do Android:**
  - **Transição de Janela Fluida e Orgânica (`ActivityOptions.makeScaleUpAnimation`):** Ao tocar para abrir qualquer aplicativo em qualquer ponto da launcher, a janela agora se expande suavemente a partir das coordenadas e dimensões exatas do ícone tocado em direção à tela cheia, utilizando o mecanismo nativo do Android WindowManager.
  - **Retorno Preciso e Efeito de Compactação Nativo (`intent.sourceBounds`):** Ao fechar o aplicativo (seja por gesto de voltar, botão de início ou gesto de retornar à Home), o Android WindowManager e o Predictive Back utilizam as coordenadas de origem registradas no Intent para encolher e compactar a janela de volta exatamente sobre o ícone que disparou a abertura, conferindo consistência espacial e fidelidade visual idêntica à do Pixel Launcher e do sistema Android oficial.
  - **Rastreamento de Coordenadas Globais em Tempo Real:** Captura contínua dos limites absolutos na janela (`boundsInWindow()`) de todos os elementos interativos através de `Modifier.onGloballyPositioned`:
    - **Favoritos da Tela Inicial (`FavoriteRow`):** Ícone em grade ou lista.
    - **Itens de Pastas Integradas e Janela Flutuante (`FolderRow` e `FolderFloatingDialog`):** Ícones nos modos de pasta em linha e pop-up flutuante.
    - **Gaveta de Aplicativos (`AppListScreen`):** Todas as linhas e ícones da lista alfabética.
    - **Pesquisa (`SearchScreen`):** Itens de aplicativos resultantes da pesquisa rápida.
    - **Botão de Ação Dinâmica (`DynamicActionButton`):** Disparo por toque simples ou gestos direcionais de deslizar para cima/baixo.
  - **Feedback Tátil e Físico de Toque com Molas (*Spring Press Scale*):** Adicionada animação de compressão elástica suave com amortecimento orgânico (`scale ~ 0.94f - 0.95f` com `Spring.DampingRatioMediumBouncy`) ao pressionar ícones de favoritos, gaveta de apps, resultados de busca e pastas, proporcionando resposta física instantânea antes da expansão da janela.
  - **Gerenciamento de Ciclo de Vida da Janela Principal (`VictoriaApp`):** Implementado registro de `ActivityLifecycleCallbacks` com referência fraca (`WeakReference<Activity>`) para garantir acesso seguro e ininterrupto à `decorView` do topo da hierarquia de janelas durante o disparo de qualquer transição.
