### 🚀 Novidades e Melhorias da Versão 0.59.29

- **Gerenciador de Tarefas Completo em Tela Inteira (Estilo Windows Task Manager):**
  - Adicionada a nova opção **"Gerenciador de tarefas"** no menu inferior da Tela Inicial, posicionada logo abaixo de "Configurações" para usuários com privilégios de superusuário (Root).
  - Tela cheia nativa integrada com o design **Material Expressive** (Material 3 avançado com bordas dinâmicas, superfícies tonais e microinterações fluidas).

- **Aba de Processos (Processes):**
  - **Categorização em Primeiro e Segundo Plano:** Lista agrupada de aplicativos ativos com status dinâmico (`FOCO` para o aplicativo em primeiro plano e `2º Plano` para processos em background).
  - **Métricas por Processo:** Exibição em tempo real de consumo de CPU (%) e memória RAM (MB) consumida por cada processo e aplicativo.
  - **Inspeção de Aplicativo com 1 Toque:** Toque em qualquer aplicativo para abrir a tela de diagnóstico avançado com métricas completas de memória (Dalvik, Native, Graphics), processos filhos, conexões de rede em tempo real, permissões perigosas, limpeza de cache e gravação de logs.
  - **Ação Rápida de Finalização:** Botão direto para finalizar processos indesejados (`kill -9`) ou forçar parada.
  - **Processos do Sistema:** Seção colapsável opcional para visualizar daemons e serviços do sistema Android.
  - **Barra de Pesquisa Expressiva:** Filtro em tempo real por nome do app, pacote ou PID.

- **Aba de Desempenho (Performance):**
  - **Processador (CPU):** Gráfico histórico em tempo real com curva Bézier e gradiente suave dos últimos 30 segundos, exibição da frequência por núcleo individual (Core 0 a Core 7 ao vivo), frequência máxima, contagem de threads e tempo de atividade (Uptime) do sistema.
  - **Memória (RAM):** Gráfico histórico dinâmico de consumo, memória total física, memória em uso, disponível e telemetria de zRAM / Swap ativa.
  - **Rede & Internet:** Gráfico de tráfego de dados, velocímetro instantâneo de Download e Upload (↓ / ↑), total de dados trafegados e tipo de conexão ativa (Wi-Fi, Dados Móveis).
  - **Unidade Gráfica (GPU):** Telemetria de GPU via sysfs (Qualcomm Adreno / ARM Mali) com leitura de frequência de clock e carga.
  - **Armazenamento:** Indicador de ocupação e espaço livre no armazenamento interno do dispositivo.
  - **Atualização ao Vivo (Live):** Badge pulsante "AO VIVO" com opção de pausar e atualizar manualmente.

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

### 🚀 Novidades e Melhorias da Versão 0.59.15

- **Eliminação de Lags e Engasgos na Rolagem do Alfabeto:**
  - **Isolamento de Recomposição:** Encapsuladas as checagens de visibilidade da tela inicial (`showHome`) e bloqueio de toques (`blockHomeTouches`) com `derivedStateOf` em `HomeRoute`, impedindo que a mudança de letras durante o scrub recomponha a tela inicial inteira e seus widgets.
  - **Otimização do Container da Gaveta de Apps:** Ajustada a medição e posicionamento da gaveta para manter dimensões consistentes e alternar a transparência na fase de desenho (`graphicsLayer`), eliminando saltos de layout `0x0` na abertura rápida.
  - **Remoção de Nós Mortos nas Linhas da Lista (`AppRow`):** Eliminado o `DropdownMenu` obsoleto e seus 8 parâmetros não utilizados de cada linha da lista de aplicativos, reduzindo centenas de alocações inúteis de nós de composição, ícones e strings por segundo durante rolagens rápidas.
  - **Ativação de `largeHeap`:** Habilitado `android:largeHeap="true"` no manifesto para evitar pressão de coleta de lixo (GC) e descarte do cache de ícones (`IconCache`) após o celular permanecer ocioso na tela inicial.

### 🚀 Novidades e Melhorias da Versão 0.59.14

- **Reversão das Modificações de Desfoque (Blur) na Lista de Aplicativos:**
  - Revertidas todas as alterações recentes e commits relacionados à implementação de blur na gaveta de aplicativos e no papel de parede.
  - Removido o `WallpaperBlurManager` e algoritmo de Stack Blur (`FastBlur`).
  - Restaurado o comportamento estável e original da lista de aplicativos com escurecimento de fundo via opacidade configurável (*Aparência → Escurecer papel de parede na lista de apps*).
  - Removida a opção de alternância de blur das configurações e recursos de texto correspondentes.

### 🚀 Novidades e Melhorias da Versão 0.59.12

- **Restauração do Timing Original de Desligamento de Tela (`ScreenOffEffect`):**
  - Revertida a antecipação de disparo do comando de bloqueio de tela para o momento de conclusão total da animação de colapso circular (`progress.animateTo` a 100%).
  - Mantida a estabilidade da sobreposição preta até o despertar do dispositivo (`ON_START` / `ON_RESUME`), prevenindo a reexibição prematura da tela inicial.

### 🚀 Novidades e Melhorias da Versão 0.59.11

- **Sincronização do Desligamento da Tela contra Flash da Tela de Bloqueio (`ScreenOffEffect`):**
  - Ajustado o ponto de disparo do comando de bloqueio e desligamento de tela (`GLOBAL_ACTION_LOCK_SCREEN`) para iniciar de forma sincronizada na reta final da animação de colapso circular (`progresso >= 85%`).
  - Ao antecipar o comando nos últimos ~60ms enquanto o círculo fecha até o centro, o `PowerManager` do Android e o hardware do display iniciam o corte de energia do visor simultaneamente ao término da animação.
  - Isso elimina a condição de corrida de milissegundos no sistema operacional onde a tela de bloqueio nativa (`Keyguard`) conseguia renderizar um frame no topo do overlay antes do painel físico apagar.
  - Transição de desligamento suave, estável e completamente escura, sem flashes da tela de bloqueio.

### 🚀 Novidades e Melhorias da Versão 0.59.10

- **Correção da Piscada (Flash) ao Desligar a Tela com Toque Duplo (`ScreenOffEffect`):**
  - Corrigido o problema onde a tela inicial reaparecia brevemente (piscava) logo após a conclusão da animação de colapso circular antes de desligar completamente o visor.
  - O estado da animação de desligamento (`lockTargetOffset`) agora permanece ativo e sólido em preto durante toda a transição de desligamento e bloqueio (`ON_PAUSE` e `ON_STOP`), evitando que a camada preta seja descartada enquanto o painel do dispositivo ainda está finalizando o desligamento.
  - A remoção da sobreposição preta e o restabelecimento da tela inicial agora ocorrem exclusivamente quando o dispositivo é reativado/desbloqueado pelo usuário (`ON_START` / `ON_RESUME`).
  - Adicionado timeout de contingência para garantir que, caso o serviço de bloqueio do sistema operacional não processe o desligamento em tempo hábil, a interface retorne ao estado normal com segurança.
  - A animação do efeito de desligamento (`ScreenOffEffect`) foi isolada com chave estável para evitar reinicializações espúrias durante recomposições de ciclo de vida.

### 🚀 Novidades e Melhorias da Versão 0.58.1

- **Correção e Redesign da Tela de Editar Layout (`HomeScreen`):**
  - **Respeito aos Insets da Barra de Status:** Adicionado recuo superior automático baseado em `WindowInsets.statusBars`, evitando que os botões e textos do modo de edição fiquem sob a barra de notificações, notch ou recorte da câmera.
  - **Eliminação do Espaçamento Rígido Lateral:** Removido o padding desnecessário de 72dp no canto direito que espremia as informações no topo.
  - **Alças de Ajuste e Espaçamento com Alto Contraste:** As alças de arrasto de padding lateral, vertical e de altura de widgets agora utilizam o tema do sistema (`surfaceContainer`, `onSurface` e `dynamicBorderColor`).
  - **Espaçamento Inferior Seguro:** Adicionada folga na base da lista para que as últimas alças não sejam encobertas pela barra de navegação.

- **Solicitação de Inicializador Padrão na Primeira Abertura (`SetDefaultLauncherDialog`):**
  - **Detecção Automática de Primeiro Acesso:** Ao abrir o Victoria Launcher pela primeira vez, a aplicação detecta se já é o inicializador padrão do sistema antes de exibir qualquer diálogo.
  - **Design Translúcido em Vidro Fosco (Frosted Glass & Blur):** Diálogo flutuante moderno com suporte a desfoque de fundo nativo (`FLAG_BLUR_BEHIND` e `blurBehindRadius = 32`), cantos arredondados de 24dp e contorno suave tingido pelo tema dinâmico Material You.
  - **Integração com Android Q+ (RoleManager) e Legados:** Acionamento do seletor nativo do sistema via `RoleManager.ROLE_HOME` em aparelhos com Android 10+, com fallback para configurações de apps padrão em versões anteriores.

- **Opção de Definir Inicializador Padrão nas Configurações (`SettingsScreen`):**
  - Nova opção na seção *Comportamento* exibindo o status em tempo real e permitindo alterar a qualquer momento.

### 🚀 Novidades e Melhorias da Versão 0.58.0

- **Novo Botão Flutuante de Pesquisa na Lista de Aplicativos (`AppListScreen`):**
  - **Aparecimento Inteligente Durante a Rolagem:** O botão de pesquisa permanece discretamente oculto no topo da lista e surge com uma animação fluida de mola (`Spring.DampingRatioMediumBouncy`) e fade quando o usuário começa a rolar a lista de aplicativos, permanecendo acessível durante a navegação e recolhendo-se suavemente ao retornar ao início da lista.
  - **Posicionamento Idêntico ao Botão Dinâmico da Tela Inicial:** Alinhado rigorosamente nas mesmas coordenadas e medidas do botão de ação dinâmico da tela inicial (`dynamicBtnAlignment`, recuo lateral calibrado para o lado ativo do alfabeto e espaçamento seguro acima da barra de navegação).
  - **Design Squircle e Física de Compressão Tátil:** Construído com recipiente em cantos arredondados de 22dp, fundo translúcido tingido pelo Material You, contorno sutil na cor primária, compressão elástica com amortecimento ao toque e vibração háptica (`HapticUtil.tick`).
  - **Acionamento Suave:** Ao tocar no botão, a lista de aplicativos é suavemente dispensada e a nova tela de pesquisa universal é aberta com transição de alta performance.

- **Nova Tela de Pesquisa Universal Dinâmica e Completa (`SearchScreen`):**
  - **Animação Suave e Profissional:** Entrada com deslizamento vertical e desvanecimento suave (`slideInVertically` + `fadeIn`), proporcionando abertura instantânea sem engasgos.
  - **Barra de Pesquisa com Foco Automático:** Campo de texto arredondado com abertura opcional e automática do teclado do sistema, ícone de pesquisa destacado e botão de limpeza rápida de texto ('X').
  - **Filtros por Categorias em Chips Interativos:** Chips horizontais para alternar rapidamente entre *Todos*, *Apps*, *Contatos*, *Configurações* e *Web*.
  - **Múltiplos Provedores de Pesquisa Integrados:**
    - *Aplicativos:* Busca instantânea insensível a acentos (normalização Unicode NFD), com classificação ponderada por início de nome, palavras compostas e pacote.
    - *Contatos:* Busca 100% offline e privada de contatos e telefones cadastrados no dispositivo via `ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI`, com inicial colorida no avatar e botão dedicado para efetuar ligação imediata (`ACTION_DIAL`).
    - *Configurações do Celular:* Atalhos rápidos com suporte bilíngue (PT/EN) para mais de 20 telas essenciais do sistema Android (Wi-Fi, Bluetooth, Bateria, Tela, Som, Armazenamento, Notificações, Segurança, Acessibilidade, Desenvolvedor, Modo Avião, etc.).
    - *Calculadora Integrada:* Reconhecimento automático de expressões aritméticas (adição, subtração, multiplicação, divisão, porcentagem) com exibição imediata do resultado e toque para copiar para a área de transferência.
    - *Pesquisa na Web e na Play Store:* Ações rápidas para buscar diretamente no navegador padrão através do buscador configurado ou pesquisar novos aplicativos na Google Play Store.
  - **Design Translúcido com Desfoque em Vidro Fosco (Frosted Glass & Blur):**
    - Substituído o fundo cinza sólido anterior por uma superfície translúcida elegante com ativação nativa do desfoque de fundo do sistema (`FLAG_BLUR_BEHIND` e `blurBehindRadius = 45`), desfocando o papel de parede em tempo real com estética premium de vidro fosco.
    - Barra de pesquisa, chips de categorias e cartões de resultados redesenhados com transparência vítrea refinada e contornos sutis de 1dp, garantindo contraste impecável e integração harmônica com qualquer tema e papel de parede.
  - **Tratamento Elegante e Seguro de Permissões de Usuário:**
    - Caso a permissão de leitura de contatos (`READ_CONTACTS`) ainda não tenha sido concedida, um card não intrusivo e elegante surge na tela de pesquisa explicando a finalidade e oferecendo um botão *"Ativar"* que abre diretamente o diálogo padrão de permissões do Android.
    - Caso o usuário não conceda ou prefira manter a permissão negada, a pesquisa continua funcionando perfeitamente para aplicativos, configurações, calculadora e web, sem mensagens intrusivas ou travamentos.

- **Configurações Dedicadas de Pesquisa nas Configurações da Launcher (`SearchSettingsScreen`):**
  - Nova seção em *Configurações → Pesquisa*, permitindo personalizar integralmente a experiência:
    - Alternar a exibição do botão flutuante de busca na lista de apps.
    - Ativar ou desativar fontes individuais de pesquisa (Aplicativos, Contatos, Configurações do celular, Web, Play Store).
    - Status visual em tempo real da permissão de contatos com atalho para concessão.
    - Seletor de mecanismo de pesquisa na Web (Google, DuckDuckGo, Bing ou Ecosia).
    - Opção para abrir ou não o teclado automaticamente ao entrar na pesquisa.

---

### 🚀 Novidades e Melhorias da Versão 0.57.3

- **Novo Estilo de Relógio: Reflexão Diária + Métricas do Sistema (`DAILY_REFLECTION_STATS`):**
  - **Cards Técnicos Compactos na Mesma Linha:** Adicionados 4 chips de telemetria discretos e alinhados harmoniosamente lado a lado logo abaixo do relógio e da frase de reflexão diária (reaproveitando os mesmos chips visuais do HUD Futurista com tamanho e paddings otimizados):
    - *Memória RAM:* Exibe a porcentagem e gigabytes livres (toque abre as configurações de aplicativos/memória).
    - *Temperatura da Bateria:* Monitora em tempo real a temperatura da bateria em graus Celsius (toque abre as opções de bateria do Android).
    - *Porcentagem da Bateria:* Mostra o nível exato da bateria e ícone de recarga ativa quando conectado à energia (⚡/🔋).
    - *Armazenamento Interno:* Acompanha o consumo e espaço restante do armazenamento interno do dispositivo (toque abre o gerenciador de armazenamento).
  - **Zero Consumo de Bateria:** Atualização sincronizada com o tique de minuto do próprio relógio (`rememberSystemStats(currentTime)`) e eventos de broadcast do sistema operacional, sem nenhum loop ativo, serviço em segundo plano ou consumo de bateria adicional.
  - **Design Unificado e Responsivo:** Preserva toda a estética minimalista e alinhamento do relógio de reflexão diária (largura intrínseca da data, divisória e citação do dia), integrando perfeitamente os 4 chips compactos ao visual da tela inicial e ao seletor de estilos.
  - **Ocultamento Inteligente na Base:** Assim como no estilo clássico de reflexão, a frase na base da tela inicial é ocultada automaticamente quando este estilo está ativo para evitar duplicidade.

---

### 🚀 Novidades e Melhorias da Versão 0.57.2

- **Refinamento Visual e Alinhamento do Widget de Frase Diária (`DAILY_REFLECTION`):**
  - **Data Limitada Rigorosamente à Largura do Relógio:** A data agora utiliza medição intrínseca mínima (`IntrinsicSize.Min`) e preenchimento total (`fillMaxWidth`), fazendo com que ocupe exatamente a mesma largura dos dígitos do relógio abaixo dela, perfeitamente alinhada às bordas (à esquerda ou à direita no modo invertido).
  - **Formatação Compacta e Elegante:** Implementada máscara de data otimizada (`EEE, d 'de' MMM` em português e `EEE, MMM d` em outros idiomas), em caixa alta e sem pontos de abreviação (ex: `SEX, 18 DE SET`), garantindo harmonia visual e eliminando quebras de linha.
  - **Remoção da Tag "Reflexão":** Removido o badge com a palavra "REFLEXÃO" tanto do widget do relógio quanto do card inferior na tela inicial e da pré-visualização no seletor de estilos, proporcionando um design muito mais limpo e minimalista.
  - **Harmonia de Espaçamentos e Tipografia:** Redução da distância entre data e hora para 2dp, ajuste na altura e opacidade da divisória vertical (52dp, 18% de opacidade), espaçamentos laterais calibrados para 12dp e tamanho da fonte da citação ajustado para 13sp para leitura confortável.

---

### 🚀 Novidades e Melhorias da Versão 0.57.1

- **Ajuste de Posicionamento e Elevação da Frase de Reflexão Diária:**
  - **Elevação Harmônica na Tela Inicial:** A frase do dia agora fica posicionada significativamente mais para cima na tela inicial, eliminando o espaçamento excessivo que havia anteriormente abaixo dos aplicativos favoritos.
  - **Acesso Natural com Deslize Suave:** Com o espaçamento superior refinado para 28dp (equivalente a uma distância equilibrada logo abaixo dos favoritos), um leve gesto de subida revela imediatamente a frase e seu autor sem necessidade de arrastar excessivamente a tela.
  - **Card Compacto e Elegante:** Otimização dos paddings internos e entrelinhas do bloco de reflexão, garantindo leitura agradável e espaçamento seguro acima da barra de navegação.

---

### 🚀 Novidades e Melhorias da Versão 0.57.0

- **Sistema Local de Frases Diárias de Reflexão (100% Offline e Sem Consumo de Bateria):**
  - **Curadoria Temática por Dias da Semana:** Implementado o novo `DailyQuoteManager` com acervo offline de frases e pensamentos de grandes autores (como Sêneca, Marco Aurélio, Aristóteles, Confúcio, Machado de Assis, Fernando Pessoa, Mário Quintana, etc.), distribuídas e filtradas de acordo com o espírito de cada dia:
    - *Segunda-feira:* Frases focadas em novos começos, coragem, iniciativa, energia e determinação para iniciar a semana.
    - *Terça-feira:* Constância, disciplina, foco em hábitos sustentáveis e execução prática.
    - *Quarta-feira:* Equilíbrio no meio da jornada, clareza mental, resiliência e perspectiva.
    - *Quinta-feira:* Perseverança, sabedoria, aprendizado e superação na reta final.
    - *Sexta-feira:* Gratidão, celebração dos passos dados, contentamento e leveza.
    - *Sábado:* Desaceleração, presença, descanso ativo, afetos e contemplação.
    - *Domingo:* Paz interior, renovação profunda de energias, silêncio e esperança para o novo ciclo.
  - **Exibição por Padrão na Parte Inferior da Tela Inicial:** Por padrão, a frase de reflexão do dia posiciona-se discretamente na base da tela inicial. O usuário precisa realizar o gesto suave de puxar a tela para cima (`drag up`) para revelar a frase e seu autor.
  - **Novo Estilo de Relógio / Widget "Reflexão Diária" (`DAILY_REFLECTION`):**
    - Criado um novo estilo visual de relógio que combina o display de hora imponente no padrão HUD Futurista (`54sp`) com a frase de reflexão do dia disposta harmoniosamente ao lado.
    - **Ocultamento Inteligente na Base:** Ao selecionar esse novo widget de relógio, a frase na parte inferior da tela inicial é automaticamente removida para evitar duplicidade e manter a tela limpa.
  - **Interatividade Direta:** Toque sobre a frase na tela inicial ou no widget copia o texto e autor instantaneamente para a área de transferência com feedback háptico e confirmação em tela.
  - **Correções de Sintaxe e Estabilidade:** Ajuste no fechamento de escopos de blocos visuais e correção do identificador de pré-visualização do relógio de reflexão no seletor de estilos.

---

### 🚀 Novidades e Melhorias da Versão 0.56.4

- **Correção Definitiva para Adição de Widgets com Configuração Privada (SecurityException):**
  - **Uso do Fluxo Nativo do `AppWidgetHost` (`startAppWidgetConfigureActivityForResult`):** Corrigido o erro fatal (`SecurityException: Permission Denial ... not exported`) que ocorria ao tentar adicionar certos widgets de terceiros (como Battery Guru e outros aplicativos) cujas atividades de configuração não são exportadas (`android:exported="false"`).
  - **Delegação Autorizada pelo Sistema:** O launcher agora delega a inicialização de telas de configuração de widgets ao framework do Android através do `AppWidgetHost`, permitindo que o sistema operacional crie um `IntentSender` com privilégios adequados para abrir telas de configuração privadas e cross-profile.
  - **Compatibilidade com Android 14+ (API 34/35):** Inclusão de `ActivityOptions` com `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`, garantindo conformidade com as restrições mais recentes do sistema operacional para início de atividades via `PendingIntent`.
  - **Tratamento Resiliente e Sem Falhas (Zero Crash):** Caso a atividade de configuração de um widget falhe ao ser aberta por qualquer inconsistência do aplicativo de origem, o erro é capturado e tratado de forma silenciosa, concluindo o vínculo do widget com suas definições padrão em vez de interromper a launcher.
  - **Configuração de Widgets Existentes no Menu de Contexto:** Aprimorada também a opção *Configurações* ao manter pressionado um widget na tela inicial para utilizar o `AppWidgetHost`, permitindo reconfigurar qualquer widget adicionado sem falhas.
  - **Blindagem na Criação e Atualização de Visualizações (`WidgetSlot`):** Proteção adicional contra exceções de inflação e renderização de layouts remotos de widgets de terceiros instáveis.

---

### 🚀 Novidades e Melhorias da Versão 0.56.3

- **Restauração da Rolagem Vertical de Conteúdo nos Widgets:**
  - **Correção da Rolagem Interna (Cima/Baixo):** Restaurada a capacidade de rolar o conteúdo interno de widgets roláveis (como listas de tarefas, e-mails, notícias, eventos de agenda e calendário).
  - **Arbitragem Não-Conflitante com a Tela Inicial:** Reativada a desautorização de interceptação (`requestDisallowInterceptTouchEvent(true)`) em `LongPressFrameLayout`, impedindo que os gestos verticais do Compose na tela inicial (`HomeScreen.draggable`) roubem e cancelem o fluxo de toque do widget interno.
  - **Harmonia entre Deslize Lateral e Rolagem Vertical:** O carrossel de múltiplos widgets continua respondendo perfeitamente ao deslize horizontal para troca de páginas (`PointerEventPass.Initial`), enquanto gestos de rolagem vertical fluem 100% livres para o widget ativo.

---

### 🚀 Novidades e Melhorias da Versão 0.56.2

- **Correção da Remoção Individual de Widgets do Carrossel:**
  - **Eliminação da Remoção Indevida dos Dois Widgets:** Corrigido o problema em que, ao haver dois widgets empilhados e solicitar a remoção de um deles, ambos pareciam sumir da tela.
  - **Transição Imediata para Modo de Widget Único:** Ao remover um widget de uma pilha de dois, o launcher transita instantaneamente para a renderização direta de widget único (`SingleWidgetView`), sem estados de índice fora de limites (`out-of-bounds`) no carrossel (`HorizontalPager`).
  - **Identificação Exata no Menu de Contexto (`activeWidgetId`):** As opções de remoção, redimensionamento, configuração e detalhes do app direcionam com exatidão matemática o widget ativo no momento, desregistrando apenas o seu ID correspondente no `AppWidgetHost` e nas preferências.

- **Ajuste no Tempo do Indicador de Bolinhas (Dots Indicator):**
  - **Desaparecimento Ágil (800ms):** Reduzido o tempo de exibição das bolinhas após a rolagem para 800ms (anteriormente 2.2s), acompanhado de uma animação suave de fade out (`250ms`).
  - **Pílula com Contraste Aprimorado:** O indicador agora conta com um fundo translúcido sutil que assegura ótima visibilidade das bolinhas ativas e inativas em qualquer papel de parede.

- **Nova Tela Seletora de Widgets dos Aplicativos (Visual e Organizada):**
  - **Pré-visualizações Gráficas Reais:** Cada widget agora exibe sua prévia gráfica autêntica fornecida pelo app (`loadPreviewImage`), com proporções nítidas e fallback harmonioso para o ícone quando a prévia não existir.
  - **Organização por Aplicativo em Acordeão:** Lista organizada em cartões expansíveis/recolhíveis por aplicativo, exibindo o ícone oficial, o nome do aplicativo e o total de widgets disponíveis.
  - **Barra de Pesquisa Instantânea:** Filtro em tempo real por nome de aplicativo ou nome de widget, com expansão automática dos resultados correspondentes.
  - **Distintivos de Dimensão e Descrições:** Badges visuais indicando a grade de tamanho (ex: `4 × 2`, `2 × 2`) e descrições detalhadas da função de cada widget no Android 12+.
  - **Botão de Retorno na Barra Superior:** Acesso rápido para voltar à tela inicial a qualquer momento.

---

### 🚀 Novidades e Melhorias da Versão 0.56.1

- **Correção da Rolagem / Deslize Horizontal entre Widgets no Carrossel:**
  - **Intercepção Direta de Gestos no Nível do Container (`PointerEventPass.Initial`):** Resolvido o problema em que o deslize lateral sobre os widgets não trocava de página. As visualizações de widgets do Android (`AppWidgetHostView`) consomem nativamente eventos de toque durante a fase principal do Compose, impedindo o detector padrão do pager de receber os movimentos de arrasto. Implementado interceptor de alta prioridade que detecta o deslize horizontal com precisão matemática antes do consumo pelas views filhas.
  - **Transição e Arraste em Tempo Real:** O carrossel acompanha o movimento do dedo em tempo real (`dispatchRawDelta`) com física de inércia (`VelocityTracker`) e animação de desaceleração suave (`animateScrollToPage`), garantindo troca instantânea com resposta tátil impecável.
  - **Remoção de Bloqueios Involuntários de Toque:** Eliminada a chamada antecipada a `requestDisallowInterceptTouchEvent(true)` no início do toque em `LongPressFrameLayout`, restaurando a capacidade do Compose de alternar os widgets fluidamente.
  - **Preservação Integral de Toques e Botões Internos:** Toques simples em botões de players, relógios e atalhos de widgets continuam funcionando perfeitamente sem falso disparo de rolagem, assim como o pressionamento longo para abrir as opções do widget.

---

### 🚀 Novidades e Melhorias da Versão 0.56.0

- **Suporte a Múltiplos Widgets na Tela Inicial (Carrossel / Stack Inteligente):**
  - **Empilhamento de Widgets com Visual Limpo:** Agora é possível adicionar múltiplos widgets à tela inicial sem poluir o visual. Os widgets ficam organizados em um carrossel em pilha ocupando uma única área (slot), mantendo apenas um widget visível por vez, preservando a estética limpa e minimalista do Victoria Launcher.
  - **Transição Suave por Deslize Horizontal:** Deslize o dedo para os lados sobre o widget para alternar fluidamente entre os widgets configurados.
  - **Animações de Troca Fluidas:** Transição moderna com interpolação de escala, profundidade e fade suave de opacidade (`scale` e `alpha`) durante o deslize entre widgets adjacentes.
  - **Indicador Dinâmico em Bolinhas (Dots Indicator):**
    - Pequenas bolinhas elegantes posicionadas discretamente logo abaixo do widget indicam a quantidade total de widgets na tela inicial.
    - A bolinha correspondente ao widget ativo assume uma tonalidade mais escura e destaque em formato pílula alongada.
    - Animação contínua e suave da bolinha acompanhando a fração exata do movimento do dedo na tela.
    - Ocultamento inteligente: as bolinhas somem suavemente após 2.2 segundos de inatividade e permanecem visíveis durante o modo de edição para fácil navegação e gerenciamento.
  - **Gestão Individual e Controles Independentes:**
    - Ao pressionar e segurar qualquer widget, o menu de contexto permite configurar o widget exibido no momento, abrir as informações do app de origem, redimensionar a altura do container ou remover especificamente aquele widget do carrossel.
    - Nova opção *"Adicionar widget a esta pilha"* no menu de contexto, permitindo empilhar novos widgets a qualquer momento.
    - Limpeza de recursos: ao remover um widget, seu ID é desregistrado com segurança do `AppWidgetHost` do sistema Android para não deixar processos órfãos consumindo bateria ou memória.
  - **Arbitragem de Toques Inteligente (`LongPressFrameLayout`):**
    - Toques comuns, botões internos de widgets e rolagem vertical dos favoritos continuam funcionando perfeitamente; o deslize horizontal é acionado de forma suave após ultrapassar o touch slop quando o widget interno não consumir a rolagem lateral.

---

### 🚀 Novidades e Melhorias da Versão 0.55.0

- **Novo Widget "HUD Futurista Pro" (Tech HUD Pro):**
  - **Estética Cyberpunk Expandida:** Introduzido um novo estilo de relógio na tela inicial no estilo HUD futurista, com cabeçalho `SYS // HUD PRO`, tipografia cyberpunk de alto contraste e layout de telemetria estendido.
  - **Novo Card de Armazenamento Interno (ROM):** Integrado um card dedicado de armazenamento (`ROM XG`) ao lado dos cards de memória RAM, temperatura da bateria e carga/carregamento, permitindo monitorar o espaço livre do dispositivo diretamente na tela inicial.
  - **Atalho Interativo para Armazenamento:** Toque no card de armazenamento abre instantaneamente as configurações de armazenamento do dispositivo Android.
  - **Badge Compacto de CPU (Arquitetura Eficiente):** Badge minimalista ao lado do relógio indicando a contagem de núcleos do processador (`CPU • X CORES`) sem sobrecarga operacional.
  - **Eficiência Energética Absoluta (Zero Impacto na Bateria):** A telemetria de armazenamento (`StatFs`) e CPU utiliza leitura instantânea nativa sob demanda e orientada a eventos (`rememberSystemStats`), sem serviços em segundo plano, sem wakelocks e com consumo nulo de energia. O monitoramento dinâmico a cada segundo do uso por núcleo do processador não foi adotado por violar as restrições de sandbox do Android (bloqueio de `/proc/stat` para apps comuns desde o Android 8+) e para preservar rigorosamente a autonomia da bateria do usuário.

---

### 🚀 Novidades e Melhorias da Versão 0.54.1

- **Correção da Instalação do APK ("Arquivo Inválido") e Ajuste de VersionCode:**
  - **Resolução de Conflito de Downgrade:** Elevado o `versionCode` para 61 (v0.54.1). Após o revert de alterações anteriores que havia retornado temporariamente o versionCode para 59, dispositivos que já haviam instalado a versão com código 60 via Root sofriam bloqueio do instalador do sistema Android por tentativa de downgrade ("O pacote parece ser inválido" / "O arquivo é inválido"). A nova versão garante atualização limpa e direta.
  - **Refatoração Segura da Limpeza de APKs (`cleanupDownloadedApk`):** Eliminada a varredura cega que excluía indiscriminadamente qualquer arquivo com o prefixo do app na pasta Downloads pública e no MediaStore. Agora o launcher rastreia e remove com precisão cirúrgica apenas a URI exata do pacote baixado internamente pelo sistema de atualização, impedindo que downloads manuais ou arquivos em andamento pelo navegador sejam truncados ou corrompidos para 0 bytes.
  - **Metadados Precisos de Versão:** Registro correto da versão e commit do pacote baixado nas preferências para validação pós-instalação.

---

### 🚀 Novidades e Melhorias da Versão 0.53.1

- **Indicador de Acesso Root nas Configurações do Aplicativo:**
  - **Status Visual de Superusuário:** Adicionada uma nova linha informativa na seção *Sobre* (ao final da tela de Configurações), identificando se o dispositivo possui acesso Root disponível no sistema.
  - **Badge Adaptativo e Detecção:** Exibe distintivo dinâmico (`ROOT ATIVO` / `SEM ROOT`) com detecção automática de binários `su` (Magisk, KernelSU, APatch), fornecendo confirmação clara sobre a disponibilidade de recursos avançados, como a instalação direta de atualizações sem verificação do Google Play Protect.

---

### 🚀 Novidades e Melhorias da Versão 0.53.0

- **Limpeza Automática do APK Pós-Atualização:**
  - **Eliminação de Resíduos de Armazenamento:** Após o download e instalação da nova versão do Victoria Launcher, o arquivo APK baixado é automaticamente removido da pasta de Downloads do usuário.
  - **Remoção Imediata no Modo Root:** Ao atualizar diretamente via Root, o arquivo APK em Downloads e os arquivos temporários são apagados no mesmo instante em que a instalação é confirmada.
  - **Suporte Nativo ao Sistema no Modo Convencional:** No modo convencional com o instalador do sistema, implementado o receptor [`MyPackageReplacedReceiver`](file:///c:/Users/Marcos/projects/launcher/victoria-launcher/app/src/main/java/dev/victorialauncher/update/MyPackageReplacedReceiver.kt) ouvindo o evento nativo `ACTION_MY_PACKAGE_REPLACED`. Combinado com a rotina de inicialização em `VictoriaApp.onCreate()`, o aplicativo compara os metadados da versão e exclui o APK antigo com segurança do `MediaStore.Downloads` (Android 10+) e da pasta física de Downloads.

- **Instalação Silenciosa via Root (Bypass do Google Play Protect):**
  - **Detecção Automática de Root:** O launcher identifica automaticamente a disponibilidade de superusuário no dispositivo (Magisk, KernelSU, APatch).
  - **Instalação Direta sem Telas Intermediárias:** Ao acionar a instalação com Root ativo, o launcher executa silenciosamente o comando `pm install -r -d -g -t` via `su` a partir de diretório protegido (`/data/local/tmp`).
  - **Bypass Completo do Google Play Protect:** Como a instalação atua diretamente no `PackageManagerService` em nível de sistema, dispensa totalmente a interface do instalador de pacotes do Android e pula qualquer tela ou bloqueio de verificação do Google Play Protect ("App não reconhecido").
  - **Controle no Diálogo "O que há de novo":** Adicionada chave seletora com switch integrado permitindo escolher entre *"Instalar via Root (Sem Play Protect)"* ou o instalador padrão.
  - **Fallback Resiliente:** Caso o usuário recuse o pedido de superusuário ou aconteça qualquer imprevisto, o instalador convencional do sistema Android é aberto imediatamente sem interromper o processo.

---

### 🚀 Novidades e Melhorias da Versão 0.52.0

- **Novos Relógios Técnicos com Telemetria do Sistema em Tempo Real:**
  - **4 Novos Estilos de Relógio na Tela Inicial:**
    - **HUD Futurista (Tech HUD):** Estética cyberpunk/sci-fi com cabeçalho de status do sistema (`SYS_OK`), indicador de pulso ativo, chips translúcidos com bordas adaptativas e badges dedicados para memória RAM livre, temperatura da bateria em °C e porcentagem de carga com ícone de carregamento.
    - **Monitor do Sistema (System Monitor):** Cartão de vidro fosco translúcido com cantos arredondados, barra de progresso horizontal em tempo real do uso da memória RAM (com porcentagem e valores em GB usados/totais), e indicadores de energia e temperatura.
    - **Minimalista com Métricas (Minimal Specs):** Design minimalista e limpo no consagrado padrão Niagara, exibindo hora em tipografia destacada e uma linha sutil com separadores em ponto (`•`) contendo RAM livre, temperatura e nível de bateria.
    - **Terminal Geek (Retro Terminal):** Estética retrô de terminal de linha de comando (CLI) em tipografia monoespaçada, com prompt de status (`> victorialauncher --status`), barra de progresso em caracteres ASCII (`[====....]`), porcentagem de uso de RAM e telemetria de energia.
  - **Eficiência Energética Absoluta e Zero Impacto na Bateria:**
    - Arquitetura 100% orientada a eventos (`event-driven`): sem loops em segundo plano, sem timers contínuos de verificação e sem wakelocks de CPU.
    - O consumo de RAM e os dados da bateria só são lidos quando o Android emite eventos nativos do sistema (`Intent.ACTION_BATTERY_CHANGED`, conexão/desconexão do carregador), no tick de mudança de minuto (`ACTION_TIME_TICK`) e no retorno à tela inicial (`ON_RESUME`).
    - Ativação sob demanda: os receptores de telemetria só são registrados no sistema se o usuário estiver utilizando um dos quatro relógios técnicos. Caso utilize um dos relógios clássicos ou analógicos, zero recursos de telemetria são executados.
  - **Atalhos e Interatividade Direta:**
    - Toque na hora abre o aplicativo de Relógio/Alarmes.
    - Toque na data abre o aplicativo de Calendário.
    - Toque nos chips ou dados de telemetria abre instantaneamente as Configurações de Bateria do sistema Android.
  - **Seletor de Estilos com Grade de Duas Colunas:**
    - Atualização da tela de escolha de relógios em *Configurações → Estilo do relógio*, exibindo miniaturas dinâmicas e fiéis de todos os 10 estilos de relógios disponíveis.
  - **Correção e Estabilidade:**
    - Inclusão do import de precisão matemática `roundToInt` no componente visual do Terminal Retrô.

---

### 🚀 Novidades e Melhorias da Versão 0.51.1

- **Correção da Escala Óptica e Enquadramento dos Ícones Temáticos:**
  - **Eliminação do Duplo Padding e Margens Transparentes Excessivas:** Corrigido o comportamento em que a imagem e os glifos dentro dos ícones temáticos ficavam visivelmente menores do que nos ícones normais. A especificação técnica de `AdaptiveIconDrawable` do Android define um canvas de 108dp com safe-zone central de ~44dp, o que introduzia margens transparentes vazias no glifo extraído antes mesmo da aplicação das margens do container squircle.
  - **Algoritmo de Auto-Enquadramento Óptico (`autoFrameGlyphBitmap`):** Implementado algoritmo de detecção de limites visíveis por varredura do canal alfa. O glifo útil é recortado e redimensionado para preencher de forma ideal 86% do espaço útil com interpolação bilinear de alta precisão (`FILTER_BITMAP_FLAG`), eliminando espaços mortos e mantendo a fidelidade geométrica sem distorções.
  - **Ajuste de Proporção Material 3 e Estilo Minimalista:**
    - *Material You:* O glifo interno agora ocupa 74% da largura do squircle de container (`Modifier.size((sizeDp * 0.74f).dp)`), resultando em um glifo ativo de ~36dp dentro de ícones de 56dp — proporção exatamente correspondente ao Pixel Launcher oficial no Android 14/15.
    - *Minimalista:* O glifo monocromático aproveita integralmente a grade do ícone (`Modifier.size(sizeDp.dp)`), equiparando o peso visual e a presença óptica aos ícones convencionais não tematizados.
    - *Monogramas Nítidos:* Aumento do corpo tipográfico do monograma fallback de 55% para 65%, garantindo legibilidade e proporção visual harmônica com os demais apps.
  - **Invalidação e Renovação Automática do Cache (`themed_v3`):** Chave de cache atualizada para descartar instantaneamente bitmaps de versões anteriores e forçar a regeneração de todos os ícones com a nova calibração óptica.

---

### 🚀 Novidades e Melhorias da Versão 0.51.0

- **Ícones Temáticos Dinâmicos do Monet para Todos os Aplicativos:**
  - **Geração Própria e Abrangente para 100% dos Aplicativos:** A Victoria Launcher agora gera ícones temáticos baseados na paleta dinâmica do Material You / Monet diretamente pelo motor da própria launcher. Elimina de vez a frustração dos ícones mistos no Android, garantindo que absolutamente todos os aplicativos instalados — modernos, antigos, corporativos, jogos ou ferramentas locais — recebam um ícone tematizado harmônico com o papel de parede.
  - **Suporte Oficial à Camada Monocromática (Android 13+):** Extração direta e renderização do vetor monocromático oficial fornecido pelos desenvolvedores de apps compatíveis, garantindo nitidez e proporção óptica oficiais.
  - **Algoritmo Inteligente de Extração de Glifos e Remoção de Fundo:**
    - Para aplicativos sem camada monocromática dedicada, a launcher analisa as camadas de primeiro plano (`foreground`) e ícones legados.
    - Implementada detecção de bordas e amostragem de cantos opacos: fundos sólidos e bordas genéricas são automaticamente isolados e removidos via distância euclidiana de cor (`tolerance`), preservando unicamente o emblema e a identidade visual do app.
  - **Máscara de Contraste e Luminância Adaptativa:**
    - Algoritmo de processamento em escala de cinza que detecta o contraste interno dos logotipos (`maxLum - minLum`). Logotipos com gradientes ou elementos sobrepostos mantêm seus relevos e diferenciações tonais através de ponderação quadrática de alfa, evitando que virem silhuetas planas ou blocos opacos.
  - **Monograma Material de Alta Definição (Fallback Universal):**
    - Em casos de aplicativos com ícones vazios, bitmaps uniformes sem contraste ou corrompidos, a launcher gera instantaneamente um monograma elegante com a inicial do aplicativo em tipografia bold centralizada, garantindo que nenhum ícone fique quebrado ou invisível.
  - **Dois Estilos Visuais Personalizáveis:**
    - *Material You (Padrão Oficial):* Recipiente adaptativo em formato squircle com cantos arredondados a 28%, preenchido com a tonalidade de container secundário do sistema (`secondaryContainer`) e glifo centralizado com destaque dinâmico Monet (`primary` / `onSecondaryContainer`). Idêntico à estética das versões mais recentes do Google Pixel e Android 14/15.
    - *Minimalista:* Glifo monocromático vazado e sem recipiente de fundo, tingido diretamente com a cor de destaque do wallpaper (`primary`), perfeito para os entusiastas da estética clean e refinada estilo Nothing OS ou Niagara.
  - **Desempenho Instantâneo e GPU Shaders:**
    - Os glifos monocromáticos são rasterizados uma única vez como máscaras de alfa em cache de memória delimitado por bytes (`IconCache`).
    - O tingimento dinâmico de cores do Monet e o desenho do recipiente são executados na GPU pelo Jetpack Compose (`BlendMode.SrcIn`). Alterar o papel de parede ou alternar entre tema claro e escuro atualiza todos os ícones instantaneamente em tempo real, sem necessidade de reprocessamento em disco ou lag.
  - **Aquecimento Assíncrono do Cache (`warmIconCache`):**
    - Processamento antecipado em segundo plano (`Dispatchers.Default`) em lotes de 16 ícones, priorizando favoritos da tela inicial e membros de pastas para que a abertura da lista de aplicativos e da tela inicial ocorra sem qualquer atraso ou queda de taxa de quadros (60/120 fps fluidos).
  - **Integração nas Configurações e Prévia em Tempo Real:**
    - Adicionada opção dedicada em *Configurações → Aparência → Ícones temáticos*, com chave seletora e escolha de estilo (Material You / Minimalista).
    - O cartão de prévia no topo da tela de configurações reflete imediatamente as mudanças em tempo real.
    - Suporte estendido a pastas da tela inicial, gaveta de aplicativos A-Z, janela flutuante de pastas, menus de contexto e telas de gerenciamento.

---

### 🚀 Novidades e Melhorias da Versão 0.50.0

- **Correção e Alinhamento Perfeito dos Widgets e Reprodutor de Mídia (Now Playing):**
  - **Eliminação do Afastamento Extra no Reprodutor de Mídia (`NowPlayingWidget`):** Removido o espaçamento horizontal interno duplicado que afastava a arte do álbum e os controles de mídia 14dp para dentro da tela. Agora a capa da faixa em reprodução inicia exatamente na mesma coordenada horizontal (`contentStart`) dos ícones de aplicativos e do relógio, garantindo alinhamento e simetria impecáveis.
  - **Suporte ao Modo Destro no Reprodutor:** Implementada adaptação dinâmica ao modo destro (`alignRight`), posicionando a arte do álbum à direita e os controles à esquerda com alinhamentos proporcionais.
  - **Supressão de Paddings Automáticos do Framework em Widgets (`VictoriaAppWidgetHostView`):** Implementada a classe especializada `VictoriaAppWidgetHostView` herdando de `AppWidgetHostView`, anulando os espaçamentos automáticos embutidos pelo sistema Android (API 14+) através de `setPadding(0, 0, 0, 0)`. Com isso, qualquer widget escolhido pelo usuário passa a ocupar com precisão cirúrgica o espaço entre `contentStart` e `contentEnd`.
  - **Ajuste Fino no Slot de Widgets (`WidgetSlot`):** Remoção de paddings residuais no container e no cartão placeholder de adição de widgets, sincronizando perfeitamente suas margens com os demais blocos da tela inicial.

- **Central de Documentação Completa e Profissional (`docs/`):**
  - **`docs/README.md`:** Portal unificado com índice, especificações do projeto e trilhas de leitura para desenvolvedores, designers e entusiastas.
  - **`docs/FEATURES_GUIDE.md`:** Manual exaustivo de todas as funcionalidades (Scrubber alfabético com física gaussiana, alinhamento harmônico, botão dinâmico com física `tanh`, 6 estilos de relógio, gaveta de apps, pastas e gestos globais).
  - **`docs/THEMING_AND_UI.md`:** Guia aprofundado do Material You / Monet, tingimento dinâmico de superfícies translúcidas (`dynamicSurfaceColor`), bordas com brilho ambiente (`dynamicBorderColor`), desfoque de janelas e tipografia.
  - **`docs/UPDATE_SYSTEM.md`:** Arquitetura do `UpdateManager`, integração com a API do GitHub Releases, notificações discretas do sistema, downloads resilientes via MediaStore e diretrizes de changelog.
  - **`docs/DATA_AND_STORAGE.md`:** Persistência no Jetpack DataStore, tabela completa de preferências, serialização de pastas, repositórios de apps/ícones e gerenciamento de memória em cache `LruCache` delimitado por bytes.
  - **`docs/ARCHITECTURE.md`:** Atualização e modernização abrangente de diagramas de arquitetura, ciclo de vida de janelas, arbitragem de toques em widgets e tabela de permissões.

- **Botão de Alinhamento Rápido na Tela de Edição do Layout:**
  - **Restauração de Alinhamento com 1 Toque:** Adicionado o botão *"Alinhar elementos"* na barra de ações superior do modo de edição/ordenação da tela inicial (*Editar layout*).
  - **Alinhamento com Base nos Aplicativos e na Borda:** Caso o usuário tenha desalinhado os componentes ao arrastar o puxador de espaçamento lateral ou os puxadores verticais, o botão restaura instantaneamente a distância padrão e simétrica dos aplicativos em relação à borda do celular (`sidePaddingDp = 20dp`), alinhando de imediato o alfabeto, os aplicativos, os widgets, o relógio e o reprodutor de mídia.
  - **Redefinição Harmônica dos Espaçamentos:** O botão redefine com precisão todos os espaçamentos padrão entre os blocos (`HomePaddings.Default`), garantindo simetria perfeita sem folgas excessivas.
  - **Feedback Háptico e Visual:** Acionamento com vibração háptica agradável e confirmação instantânea em tela (*"Todos os elementos foram alinhados com base nos aplicativos"*).

- **Substituição do Pop-up Automático de Atualização por Notificação do Sistema:**
  - **Experiência Não Intrusiva na Tela Inicial:** O diálogo/balão flutuante de mudanças ("O que há de novo") não surge mais de forma involuntária ou automática cobrindo a tela inicial ao detectar novas versões do aplicativo.
  - **Notificação Discreta e Informativa do Sistema:** Ao identificar que uma nova versão foi compilada ou lançada, o launcher agora emite uma notificação elegante na barra de status do Android informando a versão disponível (*"Victoria Launcher vX.Y.Z disponível"* - *"Toque para ver o que há de novo e atualizar"*).
  - **Acesso Direto sob Demanda:** Ao tocar na notificação, o aplicativo é trazido ao primeiro plano e o diálogo com o que há de novo e botão de download/instalação é exibido para o usuário.
  - **Integração Total com o Painel de Opções:** A verificação manual de atualizações e visualização das novidades permanecem sempre disponíveis no painel de opções da tela inicial (`HomeOptionsBottomSheet`).
  - **Compatibilidade com Android 13+:** Implementado canal de notificação dedicado (`victoria_launcher_updates`), tratamento seguro de permissões em tempo de execução (`POST_NOTIFICATIONS`) e controle inteligente de cache para evitar notificações repetidas da mesma versão.

- **Alinhamento Automático e Harmônico (Alfabeto, Aplicativos, Widgets e Relógio):**
  - **Grid de Alinhamento Unificado e Automático:** Implementado um sistema de alinhamento harmônico entre a borda do alfabeto (`EdgeScrubber`), a lista de aplicativos favoritos e pastas (`FavoriteRow` e `FolderRow`), os widgets (`WidgetSlot` e `NowPlayingBlock`) e o relógio da tela inicial (`NiagaraClockWidget`).
  - **Padrão Harmônico por Padrão:** Todos os componentes alinham-se automaticamente às mesmas diretrizes verticais:
    - *Margem externa da tela:* Relógio, widgets e ícones de aplicativos iniciam no espaçamento padrão do sistema (`sidePaddingDp`), e a coluna do alfabeto espelha exatamente a mesma distância na borda oposta da tela.
    - *Margem interna do alfabeto:* O relógio, os widgets, o reprodutor de mídia e as linhas de aplicativos respeitam automaticamente um espaçamento calculado com precisão (`sidePaddingDp + 32dp`), impedindo qualquer sobreposição com o alfabeto ou sua zona de toque.
  - **Sincronização Dinâmica com o Puxador de Ajuste (`SidePaddingHandle`):** Caso o usuário decida personalizar o recuo das bordas nas configurações ou no modo de edição arrastando a barra de ajuste lateral, **todos** os quatro elementos (alfabeto, aplicativos, widgets e relógio) adaptam-se em conjunto de forma automática e em tempo real.
  - **Suporte Abrangente ao Modo Destro (`alignRight`) e Posição do Alfabeto (`edgeSide`):** Seja com o layout padrão à esquerda, modo destro à direita, alfabeto à esquerda ou em ambos os lados, as margens `contentStart` e `contentEnd` ajustam-se dinamicamente com perfeita harmonia visual.

- **Cores Dinâmicas do Sistema (Material You / Monet) em Todas as Janelas e Diálogos:**
  - **Color Tinting Dinâmico de Superfície (`dynamicSurfaceColor`):** As superfícies das janelas e diálogos não ficam mais em cinza neutro estático. Implementado o algoritmo de mesclagem (`lerp`) que infunde a cor primária dinâmica do wallpaper (Monet) no container de vidro fosco translúcido (`FLAG_BLUR_BEHIND`), garantindo que o fundo translúcido assuma a tonalidade real do papel de parede (azul, verde, roxo, âmbar, etc.).
  - **Bordas Dinâmicas com Brilho do Wallpaper (`dynamicBorderColor`):** Contorno sutil com tonalidade adaptativa derivada da cor primária Monet, delimitando com elegância e sofisticação todas as janelas flutuantes.
  - **Pills Circulares e Ícones Tematizados no Painel de Opções (`HomeOptionsBottomSheet`):**
    - Todas as opções (Configurações, Editar tela inicial, Papel de parede, Adicionar widget, Botão dinâmico) agora contam com ícones aninhados em elegantes círculos com fundo dinâmico suave (`primary.copy(alpha = 0.14f)`) e ícones em destaque na cor primária Monet.
    - Puxador superior (drag handle) adaptado na cor dinâmica primária do sistema.
    - Fundo do painel inferior suavemente tingido na tonalidade do wallpaper com borda superior harmônica.
  - **Menu de Contexto de Aplicativos (`AppMenuDialog`):**
    - Ações aninhadas em círculos dinâmicos temáticos (com destaque em vermelho semântico de erro para desinstalação e primário para as demais ações).
    - Subtítulo do nome de pacote na cor primária e divisor estilizado com transparência dinâmica.
  - **Janela Flutuante de Pastas (`FolderFloatingDialog`):**
    - Badge de pasta em `primaryContainer`, botões de ação circulares (Configurações e Fechar) com fundo e ícones na cor primária, e subtítulo de contagem em destaque dinâmico.
  - **Diálogos de Edição (`EditAppDialog` e `FolderEditDialog`):**
    - Badge de cabeçalho com ícone de edição estilizado, botão de alterar ícone com borda dinâmica e botão de confirmação destacado em botão preenchido com a cor primária (`primary`).
  - **Diálogos de Notificações (`NotificationDetailDialog`) e Seleção de Pasta (`FolderPickerDialog`):**
    - Botões de ação em cores primárias dinâmicas, fundos adaptados ao papel de parede e divisores harmônicos.
  - **Suporte Abrangente a Versões do Android (8.1+ a 15):**
    - Suporte nativo ao Monet no Android 12+ via `dynamicDarkColorScheme`/`dynamicLightColorScheme`.
    - Extração automática de cores primárias do wallpaper via `WallpaperManager` no Android 8.1 a 11, garantindo que mesmo versões anteriores do sistema desfrutem de janelas tingidas na cor do papel de parede.
  - **Estabilidade & Compilação:** Correção de imports de dimensionamento de layout (`size`/`width`) no diálogo de edição de aplicativos (`EditAppDialog`).
