### 🚀 Novidades da Versão v0.59.79

- **Protocolo de Suspensão em Background:** Implementados comandos `P` (Pause), `R` (Resume) e `S` (Stop) via socket, cessando captura FFT e evitando consumo de CPU ou acúmulo no buffer.
- **Prevenção de Condições de Corrida:** Máquina de estados com guarda `Mutex` síncrono blindando transições simultâneas entre `start()` e `stop()`.
- **Encerramento Limpo e Específico:** Rastreamento do PID exato do daemon com envio de `SIGTERM` e fallback para `SIGKILL`, eliminando uso de `pkill` genérico.
- **Normalização Logarítmica em Decibéis:** Mapeamento de faixa dinâmica de 48 dB para resposta orgânica de amplitude sonora.
