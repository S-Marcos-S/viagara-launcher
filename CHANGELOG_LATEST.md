### 🚀 Novidades da Versão v0.59.80

- **Proteção por Geração no Supervisor:** Tokens de geração (`currentGeneration`) associados a cada ciclo de execução evitam que supervisores antigos ou exceções tardias realizem cleanup sobre daemons novos.
- **Rastreamento e Isolamento de Processos Root:** Isolamento estrito de instâncias e PIDs; daemons antigos são descartados sem interferir em execuções ativas subsequentes.
