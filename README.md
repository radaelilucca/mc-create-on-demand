# Create: On Demand

Request on-demand crafting from your existing Create factory through the Stock Keeper.

Create: On Demand uses the recipes and ingredient connections on your Factory Gauges to produce the items you need, including their components.

## Features

- Request any published product, even when its stock is zero.
- Craft connected components and reuse ingredients already in storage.
- Review **Items to craft** in production order, or **Missing materials** when supplies are short. Orders can wait for replenishment.
- Send completed orders to the address entered in the Stock Keeper. Leave it blank to keep products in storage, highlighted on your next opening.
- Track production and packaging progress, and cancel remaining work.

## Getting started

1. Configure recipes on your Factory Gauges and connect their ingredient gauges. New gauges use **Maintain stock and accept orders** by default.
2. Build and power the machines. Route ingredient packages to their processing addresses and return the products to storage monitored by the producing gauge's network.
3. Open the Stock Keeper. Click a craftable product with zero stock, or **Ctrl + left-click** to request fresh crafting while stock is available.
4. Enter a delivery address in the Stock Keeper if you want the products packaged after crafting. Choose a quantity, review the materials, and confirm. If several gauges produce the same item, choose the producer.

Requesting 16 items means crafting **16 new items**. Recipe batches may produce extras, which stay in storage. Once the full requested quantity returns, addressed orders are packaged for your existing transport system. The saved address stays with each order; changing the field later does not change that order.

The original stock slider keeps working with orders enabled. Choose **Maintain stock** to use only normal stock replenishment on a gauge.

## Supported versions

| Minecraft | Loader | Create |
|---|---|---|
| 1.21.1 | NeoForge 21.1.236 | 6.0.10 |
| 1.20.1 | Forge 47.4.0 | 6.0.8 |

Install the matching JAR alongside Create on the client and server.

## License

GNU Affero General Public License v3.0. See [LICENSE](LICENSE).
