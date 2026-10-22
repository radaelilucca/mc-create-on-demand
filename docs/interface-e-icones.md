# Interface, prévia de materiais e ícones de modo

Os seletores mantêm os `IconButton` e tooltips do Create. Cada modo tem seu próprio símbolo; ícone e tooltip mudam juntos no clique e são inicializados a partir do modo atual da tela ou do gauge.

| Tela | Modo | Símbolo |
|---|---|---|
| Stock Keeper | Todos: fabricáveis primeiro | Células de crafting com seta de prioridade para cima. |
| Stock Keeper | Itens em estoque | Caixa com tampa e fecho. |
| Stock Keeper | Itens fabricáveis | Grade de crafting. |
| Gauge | Manter estoque | Caixa com seta de reposição para cima. |
| Gauge | Manter estoque e aceitar pedidos | Folha de pedido com marca de confirmação. |

## Materiais na confirmação

O painel Request production mostra cada material no formato **ícone do item — quantidade × nome**. Com materiais ausentes, mostra apenas a lista **Missing materials**. Sem faltas, mostra **Items to craft**, usando os lotes calculados pelo planejador e ordenados pelas dependências explícitas: componentes antes dos consumidores e produto final por último. Componentes reaproveitados do estoque não entram nessa lista; rendimentos arredondados aparecem com a quantidade efetivamente fabricada.

O ícone usa o renderizador de `ItemStack` do Minecraft, preservando a aparência dos itens de mods e seus componentes/NBT. Nomes longos quebram dentro da coluna de texto, sem invadir a margem da janela; passar o mouse sobre a linha abre o tooltip nativo do item. Ícone e texto ficam centralizados na mesma linha.

As linhas de materiais têm altura mínima de 22 pixels e são separadas por traços de um pixel em cinza claro com transparência. A altura aumenta quando o nome precisa de mais linhas. Os separadores aparecem somente entre materiais. Um traço com metade da opacidade separa o formulário da tabela, somente quando houver materiais para exibir.

Quando o conteúdo ultrapassa o espaço disponível, surge uma barra de rolagem. A lista aceita a roda do mouse, clique na trilha e arraste do indicador. A rolagem usa a altura real das linhas, é limitada ao conteúdo e permite alcançar o último item inteiro. O recorte da lista é independente do cabeçalho, dos campos e dos botões do rodapé. Alterar a quantidade ou o produtor retorna a lista ao início; a roda sobre esses campos continua operando os controles do Create.

A moldura `AllGuiTextures.SCHEDULE`, os botões, a seleção de produtor e o controle de quantidade continuam usando os elementos existentes do Create. A tabela é desenhada dentro desse painel, sem substituir o menu do Stock Keeper.

## Implementação

A geometria dos cinco símbolos fica em `common/src/main/java/com/radaeli/createondemand/client/DemandIcon.java`, sem dependências de Minecraft. Cada loader mantém um adaptador `DemandIcons` que fornece `ScreenElement` aos botões nativos do Create. Os símbolos usam pixels claros, sombra de um pixel e coordenadas inteiras no espaço nativo de 16×16, incluindo a sombra. A identificação depende do desenho, sem exigir distinção de cores.

`DemandStockKeeperMixin` e `DemandGaugeScreenMixin` escolhem o ícone inicial e chamam `setIcon` ao alternar o modo. O seletor do gauge alterna somente os dois modos atuais e envia seus nomes estáveis (`STOCK`/`ORDERS`) ao servidor; ordinais antigos são rejeitados para não habilitar pedidos por engano. O ícone do Follow Demand foi removido; consulte seu [registro histórico](historico/follow-demand.md).

`DemandEngine.craftingOrder` ordena os produtores planejados pelas conexões e soma a produção do mesmo produto. Cada `DemandService` envia a sequência no campo `ToCraft` da prévia; ambos os `DemandPanel` usam o mesmo renderizador de linhas para essa sequência ou para `Missing`. A tabela fica vazia enquanto a prévia estiver sendo recalculada.

## Validação — 21/10/2026

- `buildAll --offline`: aprovado nos dois loaders, incluindo os cenários do núcleo. Log local: `build/mode-icons-build.log`.
- Os desenhos são conferidos em uma prévia gerada a partir das mesmas coordenadas do Java: `build/ui-icons/preview.png`.
- Verificados desenhos distintos e limites de 16×16, incluindo as sombras.
- Revisão das duas telas dentro do Minecraft e em diferentes escalas de GUI ainda pendente. A prévia isolada não comprova a interação em jogo.

A tabela de materiais foi compilada nos dois loaders com `buildAll --offline`; log local: `build/missing-items-table-build.log`. A revisão em jogo deve conferir listas curtas e longas, nomes extensos, o último item após rolar, tooltips e seleção entre múltiplos produtores em diferentes escalas de GUI. Essa revisão visual ainda está pendente.

O novo conteúdo **Items to craft** foi validado no núcleo e na prévia serializada da cadeia física, com `buildAll runNeoForgeGameTests runForgeGameTests -PdemandGameTests --offline` e 11 GameTests aprovados em cada loader. Log: `build/default-orders-crafting-preview-validation.log`. Isso confirma cálculo e envio da lista; a interação visual continua pendente.

Reinicie o cliente de desenvolvimento ou substitua o JAR e reinicie o jogo para carregar os novos elementos.
