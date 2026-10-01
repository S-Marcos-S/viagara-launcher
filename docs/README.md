# 📚 Central de Documentação do Viagara Launcher

Bem-vindo à documentação oficial do **Viagara Launcher**, um launcher minimalista, ergonômico e de código aberto para Android construído em **Kotlin** e **Jetpack Compose**.

![Viagara Launcher Banner](banner.png)

---

## 🧭 Guias e Documentos Técnicos

A documentação do projeto é modular e está organizada nas seguintes seções:

| Documento | Descrição |
|---|---|
| [**Arquitetura do Sistema (`ARCHITECTURE.md`)**](ARCHITECTURE.md) | Visão aprofundada da arquitetura em camadas, estrutura de pacotes, ciclo de vida de janelas, arbitragem de toques, recomposição e permissões. |
| [**Guia Completo de Funcionalidades (`FEATURES_GUIDE.md`)**](FEATURES_GUIDE.md) | Manual detalhado de todas as funções do usuário: Scrubber alfabético com física gaussiana, alinhamento unificado, botão dinâmico, relógio Niagara, gaveta de apps, pastas e widgets. |
| [**Design System e Cores Dinâmicas (`THEMING_AND_UI.md`)**](THEMING_AND_UI.md) | Engenharia de cores Material You / Monet, tingimento dinâmico de superfícies translúcidas (`dynamicSurfaceColor`), bordas sutis, desfoque de janela (`FLAG_BLUR_BEHIND`) e tipografia. |
| [**Sistema de Atualizações Integradas (`UPDATE_SYSTEM.md`)**](UPDATE_SYSTEM.md) | Arquitetura do `UpdateManager`, integração com a API do GitHub Releases, notificações não intrusivas, download seguro via MediaStore e diálogo de novidades. |
| [**Dados, Persistência e Memória (`DATA_AND_STORAGE.md`)**](DATA_AND_STORAGE.md) | Jetpack DataStore Preferences, chaves reativas, serialização de pastas, repositórios de sistema e gerenciamento de bitmaps em cache `LruCache` delimitado por bytes. |
| [**Guia de Contribuição (`CONTRIBUTING.md`)**](CONTRIBUTING.md) | Requisitos de compilação (JDK 17, SDK 35), padrões de código, traduções e diretrizes de pull request. |

---

## 🎯 Rotas de Leitura Recomendadas

### 🛠️ Para Desenvolvedores e Novos Contribuidores:
1. Comece pelo [Guia de Contribuição (`CONTRIBUTING.md`)](CONTRIBUTING.md) para configurar o ambiente e compilar o projeto.
2. Leia a [Arquitetura do Sistema (`ARCHITECTURE.md`)](ARCHITECTURE.md) para compreender os padrões não óbvios e restrições de desempenho.
3. Consulte o [Guia de Dados e Persistência (`DATA_AND_STORAGE.md`)](DATA_AND_STORAGE.md) antes de adicionar novas configurações ou modelos.

### 🎨 Para Designers e Especialistas em UI/UX:
1. Leia o [Design System e Cores Dinâmicas (`THEMING_AND_UI.md`)](THEMING_AND_UI.md) para entender a integração com o Material You.
2. Explore o [Guia de Funcionalidades (`FEATURES_GUIDE.md`)](FEATURES_GUIDE.md) para analisar as micro-interações, físicas de borracha e alinhamentos harmônicos.

---

## ⚙️ Especificações Rápidas

- **Linguagem:** Kotlin 2.0+
- **Interface:** Jetpack Compose (Material 3)
- **SDK Mínimo:** Android 8.0 (API 26)
- **SDK Compilação / Alvo:** Android 15 (API 35)
- **Estrutura:** Módulo único (`app`)
- **Licença:** [GPL-3.0-or-later](../LICENSE)
