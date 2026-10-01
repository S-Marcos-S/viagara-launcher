# 💾 Camada de Dados, Persistência e Gerenciamento de Memória do Viagara Launcher

Este documento aborda a arquitetura de persistência, modelos de dados, serialização, repositórios de sistema e técnicas de gerenciamento de memória e cache de bitmaps implementadas no **Viagara Launcher**.

---

## 📑 Sumário

1. [Jetpack DataStore Preferences](#1-jetpack-datastore-preferences)
2. [Tabela de Chaves de Preferência (`Prefs.Keys`)](#2-tabela-de-chaves-de-preferência-prefskeys)
3. [Modelos de Dados e Estratégias de Serialização](#3-modelos-de-dados-e-estratégias-de-serialização)
4. [Repositório de Aplicativos (`AppRepository`)](#4-repositório-de-aplicativos-apprepository)
5. [Mecanismo de Pacotes de Ícones (`IconPackRepository`)](#5-mecanismo-de-pacotes-de-ícones-iconpackrepository)
6. [Gerenciamento de Memória de Bitmaps (`LruCache` Byte-Bounded)](#6-gerenciamento-de-memória-de-bitmaps-lrucache-byte-bounded)

---

## 1. Jetpack DataStore Preferences

O Viagara Launcher utiliza **Jetpack DataStore Preferences** como fonte única da verdade para todas as configurações, ordenação de itens e customizações.

```
                  ┌──────────────────────┐
                  │    Compose UI        │
                  └──────────▲───────────┘
                             │ (StateFlow / Flow)
                  ┌──────────┴───────────┐
                  │      Prefs.kt        │
                  │  (DataStore Manager) │
                  └──────────▲───────────┘
                             │ (Transactional edits)
                  ┌──────────┴───────────┐
                  │ victoria_prefs.proto │
                  │  (Disco / Arquivo)   │
                  └──────────────────────┘
```

### Por que DataStore ao invés de SharedPreferences?
- **Segurança Assíncrona e Não Bloqueante:** Todas as leituras e gravações operam sob Dispatchers de I/O em Kotlin Coroutines, eliminando travamentos da thread principal (`ANRs`).
- **Emissão Reativa com `Flow`:** Modificações nos valores de configuração disparam recomposições atômicas apenas nos nós que consomem aquele dado.
- **Garantia Transacional:** Gravações com `.edit { ... }` são tratadas de maneira estritamente transacional, impedindo estados de arquivo corrompidos caso o app seja encerrado durante a escrita.

---

## 2. Tabela de Chaves de Preferência (`Prefs.Keys`)

O arquivo [`Prefs.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/data/Prefs.kt) define todas as chaves persistentes do launcher:

| Chave | Tipo | Valor Padrão | Finalidade |
|---|---|---|---|
| `hidden_apps` | `Set<String>` | `emptySet()` | Conjunto de chaves de apps ocultados da lista A-Z |
| `favorites_order` | `String` | Lista padrão | Ordem sequencial de apps favoritos e tokens de pastas |
| `folders_json` | `String` | `[]` | JSON com array de pastas criadas pelo usuário |
| `name_overrides_json` | `String` | `{}` | Mapeamento JSON de rótulos personalizados de apps |
| `icon_overrides_json` | `String` | `{}` | Mapeamento JSON de ícones personalizados por app |
| `icon_size_dp` | `Int` | `56` | Tamanho base dos ícones em dp |
| `label_size_sp` | `Int` | `15` | Tamanho dos rótulos de texto em sp |
| `item_spacing_dp` | `Int` | `4` | Espaçamento vertical entre linhas de favoritos |
| `side_padding_dp` | `Int` | `20` | Margem lateral padrão das bordas |
| `font` | `String` | `"SYSTEM"` | Família de fonte selecionada (`SYSTEM`, `SANS_SERIF`, etc.) |
| `hide_status_bar` | `Boolean` | `false` | Se a barra de status do sistema deve ser oculta |
| `dim_wallpaper_alpha` | `Float` | `0.0f` | Nível de escurecimento sobreposto ao papel de parede |
| `haptics_enabled` | `Boolean` | `true` | Habilita/desabilita feedback háptico |
| `double_tap_to_lock` | `Boolean` | `false` | Ativa gesto de desligamento de tela por toque duplo |
| `edge_side` | `String` | `"RIGHT"` | Posição do alfabeto na tela (`LEFT`, `RIGHT`, `BOTH`) |
| `always_show_az` | `Boolean` | `true` | Se a régua de letras fica visível permanentemente |
| `align_right` | `Boolean` | `false` | Modo destro: alinha ícones e textos à direita |
| `icon_pack_package` | `String` | `""` | Nome de pacote do tema de ícones ativo |
| `now_playing_enabled` | `Boolean` | `false` | Ativa o widget reprodutor de mídia Now Playing |
| `show_app_notifications` | `Boolean` | `true` | Exibe balões e contadores de notificações sob os apps |
| `clock_style` | `String` | `"CLASSIC"` | Estilo do relógio da tela inicial |
| `widget_id` | `Int` | `0` | ID do widget do Android vinculado ao slot |
| `widget_height_dp` | `Int` | `140` | Altura em dp alocada para o widget |
| `dynamic_button_enabled` | `Boolean` | `true` | Habilita o botão dinâmico de ação |
| `dynamic_button_click_app` | `String` | `""` | App executado no toque simples do botão dinâmico |
| `dynamic_button_swipe_up_app` | `String` | `""` | App executado ao puxar o botão dinâmico para cima |
| `dynamic_button_swipe_down_app` | `String` | `""` | App executado ao puxar o botão dinâmico para baixo |

---

## 3. Modelos de Dados e Estratégias de Serialização

### Modelo `AppInfo` ([`AppInfo.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/data/AppInfo.kt))
Representa um aplicativo instalador com intenção de inicialização:
- `componentName`: `ComponentName` oficial do Android.
- `label`: Nome padrão fornecido pelo manifesto do aplicativo.
- `key`: Chave achatada única (`componentName.flattenToShortString()`).

### Modelo `Folder` ([`Folder.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/data/Folder.kt))
- `id`: UUID identificador único gerado no momento da criação.
- `name`: Nome da pasta.
- `apps`: Lista sequencial de chaves (`AppInfo.key`) contidas na pasta.
- `icon`: Configuração de ícone customizado ou padrão.

### Tokenização dos Favoritos:
Na preferência `favorites_order`, itens são armazenados como linhas separadas por `\n`:
- Se a linha iniciar com `folder:`, trata-se do token de uma pasta (ex: `folder:8f9a2b...`).
- Caso contrário, trata-se da chave do aplicativo (`dev.viagaralauncher/.MainActivity`).

---

## 4. Repositório de Aplicativos (`AppRepository`)

O [`AppRepository.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/data/AppRepository.kt) conecta o launcher ao serviço `PackageManager` do sistema operacional:

```kotlin
val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
val resolveInfos = packageManager.queryIntentActivities(intent, 0)
```

- **Permissão `QUERY_ALL_PACKAGES`:** Indispensável para que o launcher consiga enumerar todos os apps instalados no Android 11+ (API 30+).
- **Monitoramento de Instalações e Remoções:** Registra um `BroadcastReceiver` com filtros para `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED` e `ACTION_PACKAGE_CHANGED`, atualizando o catálogo instantaneamente quando novos apps são instalados ou desinstalados.

---

## 5. Mecanismo de Pacotes de Ícones (`IconPackRepository`)

O [`IconPackRepository.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/data/IconPackRepository.kt) provê suporte a temas de ícones de terceiros compatíveis com o padrão do Nova/Apex:

1. **Descoberta de Pacotes:** Consulta pacotes instalados que declarem intents de suporte a temas de ícones (`org.adw.launcher.THEMES`, `com.novalauncher.THEME`, etc.).
2. **Análise de `appfilter.xml`:** Faz o parsing eficiente via `XmlResourceParser` dos mapeamentos entre o `ComponentName` de cada aplicativo e o identificador do recurso desenhável (drawable).
3. **Resolução de Desenho:** Extrai o recurso drawable diretamente do pacote do tema sem necessidade de cópia prévia de arquivos.

---

## 6. Gerenciamento de Memória de Bitmaps (`LruCache` Byte-Bounded)

Ícones de aplicativos em dispositivos móveis modernos possuem resoluções de até 192x192 ou 256x256 pixels em densidades `xxxhdpi`. Em formato ARGB_8888, cada ícone ocupa cerca de **400 KB a 500 KB** de memória RAM.

### Solução Arquitetural ([`AppIcon.kt`](file:///data/data/com.termux/files/home/storage/kotlin_projects/launcher/victoria-launcher/app/src/main/java/dev/viagaralauncher/ui/common/AppIcon.kt)):

```kotlin
val maxMemory = Runtime.getRuntime().maxMemory()
val cacheSize = (maxMemory / 8).toInt().coerceAtMost(32 * 1024 * 1024) // Até 32 MB

val iconCache = object : LruCache<String, Bitmap>(cacheSize) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        return bitmap.byteCount
    }
}
```

### Decisões Críticas de Engenharia:
1. **Cache Delimitado por Bytes (Não por Contagem):**
   - Um cache delimitado por contagem (ex: "máximo 100 ícones") é perigoso porque a chave de cache inclui o tamanho rasterizado em pixels: `"$key#${sizePx}px"`.
   - Se o usuário move o controle deslizante de tamanho de ícones nas configurações, um cache por contagem armazenaria versões duplicadas de tamanhos diferentes, estourando a memória (`OOM`). O limite por bytes descarta automaticamente entradas antigas assim que o limite seguro é atingido.
2. **Pré-aquecimento Assíncrono (Pre-warming):**
   - Ao abrir o aplicativo, uma coroutine em segundo plano (`Dispatchers.IO`) pré-carrega e renderiza no cache primeiro todos os ícones da lista de favoritos e, em seguida, os aplicativos em ordem alfabética.
   - Quando o usuário rola a lista, os bitmaps já estão prontos na memória, alcançando taxa constante de **60 a 120 FPS**.
