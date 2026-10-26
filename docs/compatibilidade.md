# Compatibilidade e arquitetura

| Módulo | Minecraft | Loader | Create | Java |
|---|---|---|---|---|
| `forge1201` | 1.20.1 | Forge 47.4.0 | 6.0.8-289 | 17 |
| `neoforge` | 1.21.1 | NeoForge 21.1.236 | 6.0.10-280 | 21 |

O identificador do mod é `create_on_demand` e o pacote base é `com.radaeli.createondemand`.

`common` contém o planejador, reservas e estados em Java 17. Cada loader mantém seus próprios adaptadores de itens, NBT/components, persistência, eventos, rede e interface.

O mod estende o Factory Gauge e o Stock Keeper originais do Create. Não registra blocos, itens ou receitas. Os quatro Mixins tratam modos e identidade dos gauges, observação de retorno, configuração de modo e interface do Stock Keeper.

A fila e os avisos de conclusão são armazenados em `data/create_on_demand_orders.dat`. Se esse arquivo ainda não existir, dados antigos de `larger_recipe_gauge_orders.dat` são importados uma única vez. O arquivo antigo é preservado. Configurações já gravadas nos campos `DemandId` e `DemandMode` dos gauges continuam legíveis. A extração do projeto não altera arquivos de mundos existentes.

O esquema atual dos pedidos é a versão 5 e inclui `Address` e `Packaged`. Pedidos sem esses campos continuam sem envio final. A marca `Dispatching` protege ingredientes e empacotamento final contra repetição após interrupção. O modo salvo `DEMAND` volta a `STOCK`; `ORDERS` preserva o aceite de pedidos e usa a reposição nativa. A migração preserva o valor do slider e pendências já despachadas, ignora pedidos internos antigos e suspende pedidos manuais que dependiam do modo removido. Os pacotes de alteração de modo usam nomes em vez de ordinais; cliente e servidor precisam usar a implementação atual.

Gauges novos ou sem `DemandMode` salvo usam `ORDERS`. O modo `STOCK` explícito e o antigo `DEMAND` continuam sendo lidos como somente estoque. A prévia inclui `ToCraft` em ordem de dependências para as duas versões, preservando a identidade completa de cada item e sua rede.

A licença AGPL-3.0 da base foi preservada.
