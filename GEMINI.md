# Diretrizes e Regras do Projeto Viagara Launcher

## Regra Obrigatória: Validação Prévia em Branch de Testes (Proteção da Branch Main)
Para evitar poluir a branch `main` com commits que possam quebrar a compilação ou falhar na geração de APKs:
- **Toda alteração deve ser testada primeiro na branch `test`:** É estritamente proibido enviar commits de novas implementações ou correções diretamente para a `main` sem validação prévia.
- **Fluxo de Trabalho Obrigatório:**
  1. Desenvolva as mudanças e utilize a branch de testes (`git checkout -b test` ou `git switch test`).
  2. Faça o bump de versão em `app/build.gradle.kts` e atualize `CHANGELOG_LATEST.md` (e `app/src/main/assets/CHANGELOG_LATEST.md`).
  3. Realize o commit contendo a versão na mensagem e faça o push para a branch `test` (`git push origin test --force` ou `git push origin test`).
  4. O GitHub Actions executará automaticamente o workflow de build (compilação e testes unitários) para a branch `test`.
  5. Acompanhe a execução da build até a conclusão (`gh run watch`). Caso ocorra qualquer falha, investigue e corrija na própria branch `test`.
  6. **SOMENTE APÓS A BUILD PASSAR COM SUCESSO (`completed` / `success`) na branch `test`:** faça o merge para a branch `main` e envie o push para gerar a Release e APK oficial:
     ```bash
     git checkout main
     git merge test --ff-only
     git push origin main
     ```

## Regra Obrigatória: Bump de Versão/Release para Disparar Builds
Sempre que for realizado um commit que gera uma mudança visual no aplicativo ou alguma correção finalizada, **DEVE-SE** incrementar o número de release/versão (`versionCode` e `versionName` no arquivo `app/build.gradle.kts`) e **OBRIGATORIAMENTE incluir o número da versão na mensagem/comentário do commit** (exemplo: `0.60.12` ou `v0.60.12`). O GitHub Actions inspeciona a mensagem do commit (`check-version`) e só inicia a build se encontrar o padrão de versão no texto do commit.

## Regra Obrigatória: Changelogs Resumidas e Compactas Pré-Build
Sempre que forem realizadas novas implementações, correções ou modificações no código, o arquivo `CHANGELOG_LATEST.md` na raiz do projeto (e seu espelho em `app/src/main/assets/CHANGELOG_LATEST.md`) **DEVE** ser atualizado antes de efetuar o commit e disparar a build do GitHub Actions.

- **Formato Estrito:** As changelogs **DEVEM SER RESUMIDAS E COMPACTAS**. É estritamente proibido incluir textos longos, parágrafos explicativos prolixos ou detalhes técnicos excessivos. Foque apenas no que mudou de forma direta, clara e concisa (tópicos curtos).
- Essa informação é lida pelo sistema de atualização do launcher (`UpdateManager`) e exibida no balão flutuante translúcido com blur ("O que há de novo") para o usuário.

## Regra Obrigatória: Proibição Estrita de Builds Locais e Gradle
NUNCA faça build local nem execute comandos `./gradlew` (tais como `./gradlew assembleDebug`, `./gradlew compileDebugKotlin`, `./gradlew build`, etc.) no ambiente local/Termux. A compilação e geração dos APKs é SEMPRE e EXCLUSIVAMENTE realizada pelo GitHub Actions.

O ambiente local destina-se apenas a edição de arquivos, análises estáticas de código, git e commits.

## Regra Obrigatória: Verificação Rigorosa e Estrita de Importações (Zero Tolerância para Imports Ausentes)
Sempre que qualquer arquivo de código for criado ou editado e utilizar qualquer classe, objeto, função, extensão ou recurso externo (como `R.string`, `ClockNotificationManager`, componentes Compose, utilitários, etc.), **DEVE-SE OBRIGATORIAMENTE conferir a lista de imports do topo do arquivo** antes de salvar e commitar.
- **Tolerância Zero:** Erros por falta de importação são inaceitáveis. Toda referência externa introduzida precisa ter seu respectivo `import` explicitamente verificado e confirmado no topo do arquivo.

## Regra Obrigatória: Acompanhamento de Builds do GitHub Actions
Sempre que for realizado um commit/push que inicie uma build no GitHub Actions, a execução do workflow **DEVE OBRIGATORIAMENTE ser acompanhada até o final** (via API do GitHub ou logs de workflow runs) para verificar se a compilação, os testes unitários e a geração da Release/APK foram concluídos com sucesso (`completed` / `success`). Caso ocorra qualquer falha (`failure`), a causa do erro deve ser inspecionada e corrigida imediatamente.
