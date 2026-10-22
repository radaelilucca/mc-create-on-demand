# Fabricação sob demanda

## Configuração

Na tela de receita de qualquer Factory Gauge, o botão lateral alterna:

- **Manter estoque (Maintain stock):** funcionamento original do Create, escolhido quando o jogador quer desabilitar pedidos naquele gauge.
- **Manter estoque e aceitar pedidos (Maintain stock and accept orders):** padrão para gauges novos e configurações sem modo salvo; preserva a reposição automática e também publica a receita no Stock Keeper da mesma rede.

Escolhas já salvas como `STOCK` são preservadas. A mudança de padrão não reconfigura gauges que o jogador já deixou nesse modo. Gauges desativados e reutilizados também começam aceitando pedidos.

O slider original controla o estoque nos dois modos, nas unidades escolhidas no Create. Por exemplo, uma meta de 10 itens continua disparando fabricação automaticamente, mesmo com pedidos habilitados e sem nenhuma solicitação manual. O rendimento pode deixar sobras: uma receita que produz quatro itens por lote pode terminar com 12. A receita precisa de produto, rendimento, endereço de processamento e conexões de ingredientes válidos. Gauges usados como monitores não precisam de receita. Gauges em modo de reposição de inventário não publicam receitas.

O grafo de fabricação segue exatamente as conexões configuradas. Somente produtores com pedidos habilitados participam da fabricação recursiva de componentes. Um ingrediente cujo produtor está no modo normal é tratado como material a repor; sua reposição nativa pelo slider continua funcionando. Não existe descoberta automática de receitas pelo JEI nem fabricação provocada apenas pela falta de estoque de um consumidor.

O modo Follow Demand foi removido. Seu funcionamento anterior está registrado em [Histórico do Follow Demand](historico/follow-demand.md). Configurações antigas desse modo passam para Manter estoque; configurações antigas que aceitavam pedidos continuam aceitando. O valor do slider é preservado. Pedidos manuais que dependiam de um produtor no modo removido ficam suspensos para revisão, e pedidos internos antigos deixam de disparar produção. Pendências já enviadas continuam sendo acompanhadas até o retorno.

## Stock Keeper

O botão lateral alterna **Todos**, **Estoque** e **Fabricáveis**. Na visão combinada, produtos fabricáveis aparecem primeiro, por nome, com uma marca verde. O estoque continua indicando apenas itens reais. Nas visões Todos e Fabricáveis, produtos publicados aparecem mesmo com estoque zero e busca vazia; clicar neles abre a fabricação. A visão Estoque continua mostrando apenas itens armazenados.

- Clique normal solicita uma retirada enquanto houver estoque.
- Sem estoque, clicar no produto fabricável abre a confirmação.
- Ctrl+clique permite solicitar fabricação mesmo havendo estoque.

A confirmação mostra o produto e a quantidade. Se faltar algum material, a tabela mostra somente **Missing materials / Materiais faltantes**. Se a cadeia puder ser fabricada, mostra **Items to craft / Itens a fabricar**, com os componentes antes dos consumidores e o produto final por último. As quantidades representam a produção efetiva dos lotes, incluindo excedentes; componentes já disponíveis não aparecem como novos crafts. A ordem segue as conexões dos gauges, não a ordem alfabética. As duas listas usam ícone, quantidade × nome, separadores sutis, tooltip e rolagem. Uma linha ainda mais discreta separa o formulário da tabela e aparece somente quando há uma lista de materiais.

O seletor de produtor aparece apenas quando existem vários gauges para o mesmo produto. A quantidade aceita digitação e rolagem do mouse; Shift acelera a rolagem. Pedidos de fabricação não usam o endereço do Stock Keeper: a fábrica devolve os produtos à rede pelo seu próprio circuito.

**Fabricar 16** significa produzir 16 unidades novas do produto final. Componentes existentes são aproveitados. Rendimentos indivisíveis podem produzir excedentes, que ficam na rede. A confirmação aceita pedidos sem todas as matérias-primas: eles aguardam reposição.

O painel **Pedidos** permite acompanhar os pedidos ativos e cancelar. Ao concluir o retorno ou cancelar, o pedido desaparece automaticamente da lista. O autor e administradores da rede podem consultar e cancelar seus pedidos. Cancelamento interrompe novos disparos, mas não recupera ingredientes que já foram enviados. Produtos desses disparos continuam entrando no estoque.

**Concluído** confirma que a quantidade nova solicitada retornou à rede do gauge produtor. O produto permanece no estoque da rede; o serviço não gera outro pacote de saída. Na primeira abertura seguinte do Stock Keeper da mesma rede, o autor vê uma borda verde nos produtos recém-fabricados. O destaque dura somente essa abertura; consultas periódicas e redimensionamento da tela não o consomem novamente. Se a produção terminar com a tela já aberta, o destaque aguarda a próxima abertura. Avisos ainda não vistos persistem com o mundo. Máquinas, rotação, armazenamento, Packagers e transporte continuam necessários.

## Arquitetura

`common` contém o planejador e o controle da fila em Java 17, sem tipos de Minecraft ou APIs de loader. Os módulos Forge e NeoForge adaptam identidade dos itens, NBT/components, persistência, interfaces e rede.

As receitas armazenam a identidade persistente do gauge, redes, entradas e rendimento. A identificação respeita NBT no Forge e components no NeoForge. Movimentar um gauge preserva sua identidade; cópias seguras para schematics não a preservam.

Pedidos guardam um snapshot das receitas utilizadas. Mudança de receita, modo ou rede e remoção de produtor suspendem o pedido. Corrija a configuração, cancele o pedido antigo e crie outro; não há retomada automática de um snapshot alterado.

O serviço executa no servidor, em intervalos de um segundo, e respeita o intervalo de pedidos do Create. Não carrega chunks. O registro de gauges é alimentado pelos próprios gauges ativos e persistido no mundo; chunks nunca visitados após a instalação são descobertos quando carregarem.

A manutenção de estoque executa o `tickRequests` nativo nos dois modos; o serviço não cria mais pedidos internos de reposição nem eleva a meta para atender consumidores. Para um lote manual, o serviço aguarda promises existentes do produto e registra suas próprias promises após o envio. Atualiza o monitor nativo para que a reposição considere imediatamente esse trabalho em andamento. O estoque mínimo e o pedido manual não disputam dois agendadores de reposição.

O planejador calcula rendimentos inteiros, reúne demanda de componentes compartilhados e detecta ciclos. A fila reserva contabilmente estoque para pedidos anteriores e acompanha produção já enviada. As reservas não impedem retiradas por jogadores ou outros sistemas: se o material desaparecer, a fabricação aguarda reposição.

Para cada lote, todos os ingredientes e Packagers são verificados antes do envio. O envio e o contexto de Mechanical Crafting usam a logística do Create. Promises nativas são registradas e as entradas de produtos na rede creditam os lotes pendentes. O produto final retorna ao armazenamento monitorado pelo circuito configurado pelo jogador. A entrada dos itens credita a produção pendente; na verificação periódica de um segundo, o pedido passa a Concluído quando toda a quantidade tiver retornado. Estoque anterior e entradas em outras redes não concluem o pedido.

Antes do envio de ingredientes, uma intenção de despacho é salva. Após confirmar o envio, o resultado é salvo. Intenções interrompidas ficam suspensas após recarregar o mundo; confira pacotes e máquinas antes de solicitar novamente. Não há repetição automática de um despacho ambíguo. A persistência dos chunks e a dos dados de pedidos não formam uma transação atômica.

As interfaces são painéis na tela existente do Stock Keeper, sem substituir seu menu. Reutilizam a moldura `AllGuiTextures.SCHEDULE`, `IconButton`, `ScrollInput`, `SelectionScrollInput`, `Label` e `NoShadowFontWrapper` do Create. A moldura mantém bordas em escala original, conteúdo recortado no interior e botões no rodapé. O título usa a fonte normal sem sombra e os textos do corpo usam alto contraste sobre fundo escuro. O servidor valida menu aberto, proximidade, Stock Keeper, acesso às redes envolvidas, quantidade e produtor.

## Validação

- `gradlew :neoforge:demandTest :forge1201:demandTest`: cenários do núcleo em Java 21 e Java 17.
- `gradlew buildAll`: compila e empacota ambos os loaders; inclui os testes do núcleo.
- `gradlew runNeoForgeGameTests -PdemandGameTests`: testes de identidade e fabricação no NeoForge.
- `gradlew runForgeGameTests -PdemandGameTests`: a mesma validação no Forge.

Os 11 GameTests por loader incluem reposição nativa com meta de 10 nos dois modos, pedido manual com estoque existente e reposição após retirada, pedido durante reposição automática em andamento, migração dos modos antigos, rejeição de pacotes numéricos antigos, limpeza da fila, destaque na primeira abertura, exclusão com permissões e produção pendente, conclusão por retorno à rede, produção física com Packagers e Mechanical Crafters, cadeia de dois estágios, retorno à rede sem retirada do produto, persistência da fila, suspensão de intenções interrompidas, movimentação de gauges e publicação de receitas usando o MenuProvider real do Stock Keeper. A validação de sessão usa o tipo registrado do menu, porque o Create pode construir uma classe diferente no servidor.

Para revisão manual, `gradlew runClient -PdemandGameTests` disponibiliza o comando de teste `/ondemand_ui`, que monta uma pequena rede de demonstração ao redor do jogador. Use-o somente em um mundo de teste vazio. Esses comandos e estruturas são excluídos dos JARs do mod.

Validação em jogo deve cobrir retirada e crafting via JEI, pedidos concorrentes, falta/reposição de materiais, reinício, cancelamento e receitas do Factory Gauge original. Interface e transporte físico precisam de evidência própria; compilação e testes do planejador não comprovam essas etapas.
