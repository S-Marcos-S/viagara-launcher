# Diretrizes e Regras do Projeto Viagara Launcher

## Regra Obrigatória: Changelogs Resumidas e Compactas Pré-Build
Sempre que forem realizadas novas implementações, correções ou modificações no código, o arquivo `CHANGELOG_LATEST.md` na raiz do projeto **DEVE** ser atualizado antes de efetuar o commit e disparar a build do GitHub Actions.

- **Formato Estrito:** As changelogs **DEVEM SER RESUMIDAS E COMPACTAS**. É estritamente proibido incluir textos longos, parágrafos explicativos prolixos ou detalhes técnicos excessivos. Foque apenas no que mudou de forma direta, clara e concisa (tópicos curtos).
- Essa informação é lida pelo sistema de atualização do launcher (`UpdateManager`) e exibida no balão flutuante translúcido com blur ("O que há de novo") para o usuário.

## Regra Obrigatória: Proibição Estrita de Builds Locais e Gradle
NUNCA faça build local nem execute comandos `./gradlew` (tais como `./gradlew assembleDebug`, `./gradlew compileDebugKotlin`, `./gradlew build`, etc.) no ambiente local/Termux. A compilação e geração dos APKs é SEMPRE e EXCLUSIVAMENTE realizada pelo GitHub Actions.

O ambiente local destina-se apenas a edição de arquivos, análises estáticas de código, git e commits.
