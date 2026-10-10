# Diretrizes e Regras do Projeto Viagara Launcher

## Regra Obrigatória: Bump de Versão/Release para Disparar Builds
Sempre que for realizado um commit que gera uma mudança visual no aplicativo ou alguma correção finalizada, **DEVE-SE** incrementar o número de release/versão (`versionCode` e `versionName` no arquivo `app/build.gradle.kts`) e **OBRIGATORIAMENTE incluir o número da versão na mensagem/comentário do commit** (exemplo: `0.60.12` ou `v0.60.12`). O GitHub Actions inspeciona a mensagem do commit (`check-version`) e só inicia a build se encontrar o padrão de versão no texto do commit.

## Regra Obrigatória: Changelogs Resumidas e Compactas Pré-Build
Sempre que forem realizadas novas implementações, correções ou modificações no código, o arquivo `CHANGELOG_LATEST.md` na raiz do projeto (e seu espelho em `app/src/main/assets/CHANGELOG_LATEST.md`) **DEVE** ser atualizado antes de efetuar o commit e disparar a build do GitHub Actions.

- **Formato Estrito:** As changelogs **DEVEM SER RESUMIDAS E COMPACTAS**. É estritamente proibido incluir textos longos, parágrafos explicativos prolixos ou detalhes técnicos excessivos. Foque apenas no que mudou de forma direta, clara e concisa (tópicos curtos).
- Essa informação é lida pelo sistema de atualização do launcher (`UpdateManager`) e exibida no balão flutuante translúcido com blur ("O que há de novo") para o usuário.

## Regra Obrigatória: Proibição Estrita de Builds Locais e Gradle
NUNCA faça build local nem execute comandos `./gradlew` (tais como `./gradlew assembleDebug`, `./gradlew compileDebugKotlin`, `./gradlew build`, etc.) no ambiente local/Termux. A compilação e geração dos APKs é SEMPRE e EXCLUSIVAMENTE realizada pelo GitHub Actions.

O ambiente local destina-se apenas a edição de arquivos, análises estáticas de código, git e commits.

## Regra Obrigatória: Acompanhamento de Builds do GitHub Actions
Sempre que for realizado um commit/push que inicie uma build no GitHub Actions, a execução do workflow **DEVE OBRIGATORIAMENTE ser acompanhada até o final** (via API do GitHub ou logs de workflow runs) para verificar se a compilação, os testes unitários e a geração da Release/APK foram concluídos com sucesso (`completed` / `success`). Caso ocorra qualquer falha (`failure`), a causa do erro deve ser inspecionada e corrigida imediatamente.
