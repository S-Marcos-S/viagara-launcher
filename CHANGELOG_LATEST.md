### 🚀 Novidades e Melhorias da Versão 0.59.18

- **Ajuste Fino e Zoom no Ícone do Viagra Launcher:**
  - Removido o recorte residual de borda clara na parte inferior da imagem original.
  - Aplicado zoom inteligente (~10-12%) e enquadramento ideal no ícone, fazendo com que o logo preencha perfeitamente a máscara de exibição sem sobrar nenhuma faixa branca indesejada na base.
  - Regeneradas todas as densidades de drawables e mipmaps (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).

- **Otimização do Fluxo de Integração Contínua (CI/CD):**
  - Removido o job redundante e demorado de verificação paralela do GitHub Actions.
  - Testes unitários integrados diretamente no job de compilação, reduzindo o tempo de entrega do APK em mais de 50%.
