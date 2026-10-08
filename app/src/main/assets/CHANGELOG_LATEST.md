### 🔧 Correções Técnicas v0.59.95

- **Segurança de threads:** Race condition nos arrays de balística eliminada com lock dedicado.
- **Bounds do FFT:** Clamp de bin corrigido para respeitar o `captureSize` real do dispositivo.
- **Docstring atualizada:** Daemon documentado com valores reais (14 bandas, alpha=0.35, gain=1.25×).
- **Import morto removido:** `kotlinx.coroutines.delay` não utilizado eliminado do Manager.
