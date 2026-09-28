### Novidades e Melhorias da Versão 0.59.32

- **Refinamento da Animação de Fechamento de Aplicativos:**
  - **Eliminação da Oscilação Final do Ícone (*Wobble Removal*):** Removida a animação residual de acomodação elástica e deslocamento artificial do ícone ao concluir o retorno à tela inicial, garantindo que o fechamento da janela do aplicativo convirja de forma firme, estável e direta para a posição de repouso do ícone na lista, sem tremores ou oscilações indesejadas.
  - **Manutenção Integral do Handshake `GestureNavContract`:** Preservada a comunicação de alta precisão com o SystemUI (`gesture_nav_contract_v1`), enviando as coordenadas exatas da grade para que a transição de janela do sistema operacional continue ocorrendo de forma limpa e contínua.
