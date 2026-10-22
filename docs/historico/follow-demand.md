# Follow Demand — funcionamento anterior à remoção

Registro feito em 21/10/2026, antes de remover o modo. Este documento descreve a implementação anterior, não uma opção disponível na versão atual.

## Os três modos anteriores

`DemandEngine.Mode` tinha `STOCK`, `DEMAND` e `ORDERS`, nessa ordem. `STOCK` preservava a automação original; `DEMAND` seguia consumidores e não publicava o produto no Stock Keeper; `ORDERS` fazia o mesmo e também aceitava pedidos manuais. O modo era gravado pelo nome em `DemandMode`, e a alteração pela interface enviava o ordinal.

Nos dois últimos modos, `DemandGaugeMixin` cancelava `FactoryPanelBehaviour.tickRequests` para gauges de produção. Assim, o serviço era o único responsável pelo disparo nesses gauges. Gauges em modo de reposição de inventário não passavam por essa substituição.

## Reposição e demanda de consumidores

A cada 20 ticks, `DemandService.tick` atualizava as receitas e calculava, para cada produtor habilitado:

```java
int target = gauge.getAmount()
        * (gauge.upTo ? 1 : gauge.getFilter().getMaxStackSize());
int downstream = 0;
for (FactoryPanelBehaviour consumer : loaded.values()) {
    if (!consumer.satisfied && !consumer.promisedSatisfied && !consumer.redstonePowered)
        for (FactoryPanelConnection connection : consumer.targetedBy.values())
            if (connection.from.equals(gauge.getPanelPosition()))
                downstream += connection.amount;
}
target = Math.max(target, downstream);
int deficit = Math.max(0, target - gauge.getLevelInStorage() - gauge.getPromised());
```

O valor do slider era respeitado nas unidades configuradas pelo Create: itens com `upTo`, ou pilhas caso contrário. A demanda dos consumidores podia elevar essa meta mesmo com o slider em zero. Somente consumidores carregados, não satisfeitos, não cobertos por promises e sem bloqueio de redstone participavam desse cálculo.

Havendo déficit e nenhum pedido ativo com aquele produtor como raiz, o serviço criava um pedido interno, com autor UUID zero e quantidade limitada a 65.536. Esse pedido não aparecia no Stock Keeper, mas utilizava a mesma fila, reservas, despacho e acompanhamento de retorno dos pedidos manuais.

## Cadeias de produção

O planejador seguia as conexões explícitas entre gauges. Receitas de componentes em `DEMAND` ou `ORDERS` podiam ser fabricadas recursivamente, após aproveitar o estoque existente; um componente em `STOCK` era tratado como material a repor quando faltava. Não havia descoberta de receitas pelo JEI.

Rendimentos eram arredondados para lotes inteiros. O serviço enviava ingredientes com `PackageOrderWithCrafts`, respeitava redstone, chunks carregados e intervalo do Create, e registrava promises. Componentes precisavam retornar à rede antes da etapa seguinte. A chegada do produto final creditava o pedido; as sobras permaneciam no estoque.

## Interface e persistência anteriores

O botão do gauge alternava três modos. O ícone `FOLLOW_DEMAND` mostrava dois produtores ligados por uma seta; `ORDERS` usava uma folha de pedido com confirmação. A fila persistia o indicador `Internal`, snapshots de receitas, quantidades despachadas e pendências. Mudanças de receita ou modo suspendiam pedidos manuais e cancelavam pedidos internos.

## Mudança solicitada

Remover o modo exclusivo de seguir demanda, seu ícone e sua reposição baseada em consumidores. Manter somente **Maintain stock** e **Maintain stock and accept orders**. A reposição automática volta ao fluxo nativo do Create nos dois modos, usando o slider original. O serviço fica responsável apenas pelos pedidos manuais e por suas cadeias de componentes habilitados.

Na migração, `DEMAND` passa ao modo normal, sem habilitar pedidos implicitamente; `ORDERS` continua aceitando pedidos, agora com reposição nativa. Pedidos internos antigos deixam de gerar novos despachos. Materiais já enviados continuam seu processamento e retorno.
