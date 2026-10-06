# Viagara Launcher

Um launcher minimalista e elegante para Android baseado em lista — uma alternativa de código aberto inspirada no [Niagara Launcher](https://niagaralauncher.app).

<p align="center">
  <img src="docs/screenshots/screenshot_home.png" width="24%" alt="Tela Inicial" />
  <img src="docs/screenshots/screenshot_search.png" width="24%" alt="Pesquisa Universal" />
  <img src="docs/screenshots/screenshot_processes.png" width="24%" alt="Gerenciador de Processos" />
  <img src="docs/screenshots/screenshot_performance.png" width="24%" alt="Monitor de Desempenho" />
</p>

---

## 📱 Recursos e Funcionalidades / Features

- **Design Minimalista & Eficiente:** Lista vertical ergonômica de aplicativos projetada para uso confortável com uma mão.
- **Barra Alfabética Inteligente (Scrubber):** Navegação rápida com curva de onda suave e atalho de estrela (`★`) no topo para acesso instantâneo à tela inicial e aos aplicativos favoritos.
- **Catálogo Oficial de Papéis de Parede:**
  - Galeria integrada com papéis de parede verticais em alta definição no formato ultra-leve WebP.
  - Filtro interativo por categorias: **OLED / Preto Puro**, **Espaço**, **Natureza**, **Abstrato** e **Minimalista**.
  - Pré-visualização em tela cheia e aplicação direta com um toque para **Tela inicial**, **Bloqueio** ou **Ambas**.
  - Pipeline automático no GitHub Actions para curadoria periódica de novos papéis de parede sem inflar o tamanho do APK.
  - Atalho rápido para selecionar imagens locais do aparelho ou Google Fotos.
- **Personalização de Cores & Temas:**
  - Seletor minimalista animado nas configurações com suporte a **Dinâmico do Sistema (Material You)**, **OLED Preto Puro**, **Escuro Moderno** e **Claro Clean**.
  - Adaptação imediata das cores da interface às mudanças de papel de parede.
- **Notificações Integradas com Lista de Mensagens:**
  - Prévias de notificações exibidas diretamente abaixo do nome do aplicativo.
  - Diálogo expansível translúcido com desfoque nativo (*frosted glass*), histórico de mensagens agrupadas (WhatsApp, Telegram, etc.) e abas para conversas múltiplas.
  - Ação rápida para responder/abrir e opção para dispensar.
- **Widgets & Now Playing:** Suporte a widgets padrão do Android e controle integrado de mídia em tempo real.
- **Customização Abrangente:** Suporte a pacotes de ícones externos, ícones temáticos monocromáticos / OLED, renomeação de apps, ocultação de aplicativos e pastas organizadas.
- **Atualizador Embutido:** Sistema de verificação e download de atualizações via GitHub Releases diretamente pelo launcher com balão flutuante de novidades.
- **Totalmente Localizado:** Interface completa em Português (Brasil - pt-BR / pt) e Inglês.

---

## 📥 Instalação / Download

Baixe a versão mais recente em formato APK diretamente na seção de **[Releases](https://github.com/S-Marcos-S/viagra-launcher/releases)** deste repositório.

Após instalar, defina o Viagara Launcher como inicializador padrão:
**Configurações → Aplicativos → Aplicativos padrão → App de início (Home)**.

> **Requisitos:** Android 8.0 (API 26) ou superior.

---

## 🛠️ Compilação / Build

O projeto utiliza compilação automatizada via **GitHub Actions**. Para compilar localmente para desenvolvimento:

```sh
# Compilar APK Debug
./gradlew assembleDebug

# Compilar APK Release
./gradlew assembleRelease
```

O arquivo gerado estará disponível em `app/build/outputs/apk/`.

Para notas detalhadas sobre contribuição e arquitetura, consulte [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) e [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 🤝 Créditos / Credits

- **[Niagara Launcher](https://niagaralauncher.app)** — Inspiração para o conceito ergonômico minimalista baseado em lista vertical e scrubber alfabético.
- **[Wallhaven](https://wallhaven.cc)** e seus respectivos criadores/artistas — Fonte e curadoria dos papéis de parede em alta definição disponibilizados no catálogo oficial.
- **[LogFox](https://github.com/F0x1d/LogFox)** — Referência e base para as rotinas de captura contínua de logs do sistema, visualizador de registros e rastreador de crashes.
- **[BatStats](https://github.com/mlm-games/BatStats)** — Referência e base para o coletor/parser de telemetria da bateria (`dumpsys batterystats`), análise de consumo e monitor de drenagem em tempo real.

---

## 📄 Licença / License

Distribuído sob a licença [GPL-3.0-or-later](LICENSE).
