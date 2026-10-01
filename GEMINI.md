# Diretrizes e Regras do Projeto Viagara Launcher

## Regra Obrigatória: Atualização de Changelog Pré-Build
Sempre que forem realizadas novas implementações, correções ou modificações no código, o arquivo `CHANGELOG_LATEST.md` na raiz do projeto **DEVE** ser atualizado com um resumo claro e estruturado de todas as mudanças realizadas antes de efetuar o commit e disparar a build do GitHub Actions.

Essa informação é lida pelo sistema de atualização do launcher (`UpdateManager`) e exibida no balão flutuante translúcido com blur ("O que há de novo") para o usuário.
