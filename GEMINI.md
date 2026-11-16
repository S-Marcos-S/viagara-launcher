# Diretrizes e Regras do Projeto Viagara Launcher

## Regra Obrigatória: Validação Prévia em Branch de Testes (Proteção da Branch Main)
Para evitar poluir a branch `main` com commits que possam quebrar a compilação ou falhar na geração de APKs:
- **Toda alteração de código ou que envolva nova build deve ser testada primeiro na branch `test`:** É estritamente proibido enviar commits de novas implementações ou correções diretamente para a `main` sem validação prévia.
- **Commits triviais ou de documentação pura:** Mudanças que afetam apenas documentação (`README.md`, `docs/`, etc.) e que não alteram código funcional nem disparam nova build podem ser commitadas normalmente (veja a regra abaixo).
- **Fluxo de Trabalho Obrigatório para Builds:**
  1. Desenvolva as mudanças e utilize a branch de testes (`git checkout -b test` ou `git switch test`).
  2. Faça o bump de versão em `app/build.gradle.kts` e atualize `CHANGELOG_LATEST.md` (e `app/src/main/assets/CHANGELOG_LATEST.md`).
  3. Realize o commit contendo a versão na mensagem e faça o push para a branch `test` (`git push origin test --force` ou `git push origin test`).
  4. O GitHub Actions executará automaticamente o workflow de build (compilação e testes unitários) e publicará uma nova release de teste (`Pre-release` no GitHub, sem destaque, visível apenas para usuários que ativaram a opção de testador beta nas configurações da launcher).
  5. Acompanhe a execução da build até a conclusão (`gh run watch`). Caso ocorra qualquer falha, investigue e corrija na própria branch `test`.
  6. **PERGUNTA OBRIGATÓRIA AO USUÁRIO APÓS BUILD DE TESTE:** Após a build ser feita com sucesso na branch `test` e a release de teste estar disponível, **o agente DEVE SEMPRE perguntar ao usuário** se deseja subir as alterações para a branch `main` e lançar uma release estável oficial. NUNCA faça o merge ou push para a `main` automaticamente sem a confirmação explícita do usuário.
  7. **SOMENTE APÓS A CONFIRMAÇÃO DO USUÁRIO:** promova as alterações para a branch `main`, incremente para a próxima versão pública oficial `X.(Y+1).0` (ou `(X+1).0.0` se Y=9), incremente o `versionCode`, consolide o `CHANGELOG_LATEST.md` e envie o push para gerar a Release e APK oficial estável:
     ```bash
     git checkout main
     git merge test
     # Definir próximo versionName X.Y.0 e incrementar versionCode em app/build.gradle.kts
     # Atualizar CHANGELOG_LATEST.md (e app/src/main/assets/CHANGELOG_LATEST.md)
     git commit -am "release: vX.Y.0"
     git push origin main
     # Sincronizar branch test com main
     git checkout test
     git merge main --ff-only
     git push origin test
     ```

## Regra Obrigatória: Estratégia Padronizada de Versionamento (Releases Públicas vs. Builds de Teste)
Para garantir clareza entre releases estáveis para o público e builds de teste beta, adota-se rigorosamente a convenção:
- **Releases Públicas na Branch `main` (Sempre terminadas em `.0`):**
  - Formato estrito: `X.Y.0` (exemplo: `1.1.0`, `1.2.0`, `1.3.0`, etc.).
  - A cada nova release pública na `main`, incrementa-se o dígito menor (*minor*): `1.1.0` -> `1.2.0` -> `1.3.0` ... -> `1.9.0`.
  - **Virada para Versão Maior (*major*):** Ao atingir a versão `1.9.0`, a próxima release pública salta obrigatoriamente para `2.0.0` (reiniciando minor e patch para `0`). E assim sucessivamente (`2.9.0` -> `3.0.0`).
  - Releases na `main` geram tags como `vX.Y.0` e a tag flutuante `latest`, sendo publicadas como releases oficiais (`prerelease: false`).
- **Builds de Teste na Branch `test` (Sempre incrementando o patch `Z >= 1`):**
  - Formato estrito: `X.Y.Z` onde $Z \ge 1$ (exemplo: partindo da pública `1.2.0`, as builds de teste em `test` serão `1.2.1`, `1.2.2`, `1.2.3`, etc.).
  - As tags no GitHub Actions na branch `test` recebem o sufixo `-beta` (exemplo: `v1.2.1-beta`) e são marcadas como `prerelease: true`.
  - NUNCA use terminação `.0` para builds na branch `test`.
- **Transição e Promoção de `test` para `main` (Release Oficial):**
  1. Quando as builds de teste forem validadas e o usuário aprovar a publicação oficial:
  2. Calcula-se a próxima versão pública na `main`: se a última foi `X.Y.0`, a nova será `X.(Y+1).0` (ou `(X+1).0.0` se $Y = 9$).
  3. Incrementa-se o `versionCode` (`versionCode = max(main, test) + 1`).
  4. Consolida-se o `CHANGELOG_LATEST.md` (e `app/src/main/assets/CHANGELOG_LATEST.md`).
  5. Commita-se com a mensagem contendo a versão (ex: `release: vX.Y.0`) e faz-se o push para a branch `main`.
  6. Acompanha-se a execução do workflow `build.yml` na `main` até a publicação com sucesso.
  7. Sincroniza-se a branch `test` com a `main` (`git checkout test && git merge main --ff-only && git push origin test`) para que o próximo ciclo de testes parta de `X.Y.0` e sua primeira build de teste seja `X.Y.1`.

## Regra Obrigatória: Bump de Versão/Release Apenas para Mudanças Relevantes (Proibido para Mudanças Bestas/Triviais)
- **Mudanças Bestas / Triviais (NÃO gerar build):**
  - Para alterações pequenas, triviais ou que não alteram o funcionamento do aplicativo em si — como edições em documentação (`README.md`, pasta `docs/`, `CONTRIBUTING.md`), comentários no código, formatação, correção de digitação (typos), `.gitignore` ou scripts auxiliares:
    - **NUNCA** faça bump de versão (`versionCode` e `versionName` em `app/build.gradle.kts`).
    - **NUNCA** altere o `CHANGELOG_LATEST.md`.
    - **NUNCA** inclua número de versão na mensagem do commit.
    - Faça apenas um **commit normal** (exemplo: `docs: update README header` ou `chore: fix typo`).
    - Como o workflow do GitHub Actions (`check-version`) só dispara a compilação se detectar um padrão de versão na mensagem do commit, sem a versão ele irá pular a build (`Skipping build`), evitando gastar minutos de CI e poluir as Releases com APKs desnecessários.
- **Mudanças Relevantes no Aplicativo (GERAR build):**
  - Sempre que for realizado um commit que traga uma **nova funcionalidade/feature**, **correção de bug real**, **refatoração funcional** ou **mudança visual direta no aplicativo**:
    - **DEVE-SE** incrementar o número de release/versão (`versionCode` e `versionName` no arquivo `app/build.gradle.kts`).
    - **DEVE-SE** atualizar o arquivo `CHANGELOG_LATEST.md` (resumido e compacto).
    - **OBRIGATORIAMENTE** incluir o número da versão na mensagem/comentário do commit (exemplo: `0.60.12` ou `v0.60.12`).
    - Seguir rigorosamente o fluxo de validação prévia na branch `test` antes da `main`.

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

## Regra Obrigatória: Idioma Padrão em Workflows, Commits e Comentários de Código (Somente em Inglês)
- **Workflows do GitHub Actions:** Nomes de workflows, jobs, steps, variáveis e descrições nos arquivos `.github/workflows/*.yml` devem ser escritos estritamente em inglês.
- **Mensagens de Commit:** Todas as mensagens de commit (incluindo escopos convencionais como `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `ci:`) devem ser escritas obrigatoriamente e exclusivamente em inglês.
- **Comentários no Código:** Todos os comentários em arquivos de código-fonte (Kotlin, Java, Gradle scripts, etc.) devem ser escritos unicamente em inglês técnico.
