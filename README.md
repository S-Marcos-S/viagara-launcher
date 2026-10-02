# Viagara Launcher

Um launcher minimalista e elegante para Android baseado em lista — uma alternativa de código aberto inspirada no [Niagara Launcher](https://niagaralauncher.app).

<p align="center">
  <img src="docs/screenshots/screenshot_home.png" width="24%" alt="Tela Inicial" />
  <img src="docs/screenshots/screenshot_search.png" width="24%" alt="Pesquisa Universal" />
  <img src="docs/screenshots/screenshot_processes.png" width="24%" alt="Gerenciador de Processos" />
  <img src="docs/screenshots/screenshot_performance.png" width="24%" alt="Monitor de Desempenho" />
</p>

---

## 📱 Funcionalidades / Features

- **Design Minimalista & Eficiente:** Lista vertical de aplicativos com foco em usabilidade com uma mão.
- **Barra Alfabética com Estrela para Favoritos (Scrubber):** Navegação rápida com curva de onda elástica e atalho de estrela (`★`) no topo para retornar instantaneamente à tela inicial e aos favoritos.
- **Notificações Integradas com Lista de Mensagens:**
  - Exibição de prévias de notificações diretamente abaixo dos nomes dos aplicativos.
  - Diálogo expansível translúcido com desfoque nativo (*frosted glass*), exibindo todo o histórico de mensagens agrupadas (WhatsApp, Telegram, etc.) e abas para múltiplas conversas.
  - Ação de abertura direta e opção para dispensar.
- **Atualizador Integrado Direto pelo App:** Download de novas versões e atualizações com indicador de progresso em tempo real diretamente via GitHub Releases.
- **Widgets & Now Playing:** Suporte a widgets do Android e controle de mídia integrado em tempo real.
- **Customização Completa:** Suporte a pacotes de ícones, alteração de nomes de apps, ocultação de aplicativos e pastas.
- **Totalmente Localizado:** Suporte completo para Português (Brasil - pt-BR / pt) e Inglês.

---

## 📥 Instalação / Download

Baixe a versão mais recente em formato APK diretamente na seção de **[Releases](https://github.com/S-Marcos-S/viagra-launcher/releases)** deste repositório.

Após instalar, defina o Viagara Launcher como inicializador padrão:
**Configurações → Aplicativos → Aplicativos padrão → App de início (Home)**.

> **Requisitos:** Android 8.0 (API 26) ou superior.

---

## 🛠️ Compilação / Build

Para compilar o projeto localmente, são necessários o JDK 17 e o Android SDK com a plataforma 35 instalada.

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

- **[LogFox](https://github.com/F0x1d/LogFox)** — Referência e base para as rotinas de captura contínua de logs do sistema, visualizador de registros e rastreador de crashes.
- **[BatStats](https://github.com/mlm-games/BatStats)** — Referência e base para o coletor/parser de telemetria da bateria (`dumpsys batterystats`), análise de consumo e monitor de drenagem em tempo real.

---

## 📄 Licença / License

Distribuído sob a licença [GPL-3.0-or-later](LICENSE).
