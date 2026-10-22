# Desenvolvimento e validação

## Comandos

Execute uma tarefa Gradle por vez. São necessários Java 17 e Java 21.

```powershell
.\gradlew.bat buildAll
.\gradlew.bat verifyAll
.\gradlew.bat :neoforge:demandTest :forge1201:demandTest
.\gradlew.bat runNeoForgeClient
.\gradlew.bat runForgeClient
.\gradlew.bat runNeoForgeGameTests -PdemandGameTests
.\gradlew.bat runForgeGameTests -PdemandGameTests
```

`-PdemandGameTests` inclui somente nas execuções de desenvolvimento os testes de servidor e o comando `/ondemand_ui`. Use esse comando apenas em um mundo vazio de testes; ele monta uma rede de demonstração ao redor do jogador. Classes e estruturas de teste ficam fora dos JARs distribuídos.

As saídas ficam em `neoforge/build/libs` e `forge1201/build/libs`; os mundos de desenvolvimento são separados em `<módulo>/runs`.

As tarefas do VS Code usam o cache local `.gradle-create-on-demand`. Para reproduzir a mesma configuração no terminal PowerShell, execute `$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-create-on-demand'` antes do Gradle. A primeira execução em um cache vazio precisa baixar o Gradle e as dependências; o timeout de download do wrapper é de 60 segundos. Os caches não fazem parte dos fontes do projeto.

## Cobertura

O núcleo verifica rendimentos, dependências, ciclos, reservas entre pedidos, reposição, cancelamento, retorno parcial e conclusão sem retirada do produto. Os GameTests usam somente o Factory Gauge original: fabricação física em crafters, cadeia de dois estágios, persistência, identidade e movimentação, catálogo do Stock Keeper, limpeza da fila, destaque de uma abertura e cancelamento autorizado.

Builds, testes automatizados, carregamento dos dois mods juntos e revisão visual em Minecraft têm evidências separadas. Compilação não comprova interação na tela.

## Evidências da extração — 21/10/2026

| Verificação | Resultado |
|---|---|
| Build Forge e NeoForge | Aprovado; JARs com `create_on_demand`, sem itens, receitas ou classes dos gauges maiores. |
| Núcleo compartilhado | Cenários aprovados em Java 17 e Java 21, incluídos em `buildAll`. |
| On Demand sozinho | 8 GameTests aprovados por loader, incluindo produção física e cadeia com retorno à rede. |
| On Demand com Larger Recipe Gauges | Ambos carregaram e os 8 GameTests de demanda passaram por loader. No Forge, o JAR auxiliar dos gauges foi montado com classes mapeadas para o ambiente de desenvolvimento. |
| Importação dos pedidos anteriores | Aprovada nos dois mundos de teste; o novo arquivo foi salvo e o hash do arquivo antigo permaneceu igual. |
| Inicialização do cliente NeoForge | Aprovada com o cache próprio das tarefas do VS Code em 21/10/2026; Create e On Demand carregaram, com inicialização de áudio e recursos gráficos concluída. Log: `build/neoforge-client-local-cache.log`. |
| Interface e interação manual | Não repetidas após a extração. Abertura de telas, escalas de GUI e multiplayer real continuam precisando de revisão manual. |

Logs locais em `build/extraction-final-build.log`, `build/extraction-neoforge-tests.log`, `build/extraction-forge-tests.log` e `build/extraction-combined-<loader>-tests.log`. Os registros de importação estão nos logs conjuntos; `build/import-test-files.json` registra os hashes anteriores. Esses arquivos são evidências locais ignoradas pelo Git.

Ao atualizar uma instalação que usava a versão com demanda embutida, substitua também o JAR do Larger Recipe Gauges pela versão que contém somente os gauges maiores. Instale o On Demand correspondente no cliente e no servidor antes de abrir o mundo; não mantenha a implementação antiga de demanda junto com a extraída.

## Dois modos de operação — 21/10/2026

O comportamento anterior foi registrado em [Histórico do Follow Demand](historico/follow-demand.md) antes da remoção. A versão atual mantém apenas Manter estoque e Manter estoque e aceitar pedidos. Os dois deixam a reposição para o Create e preservam o slider original; o serviço não agenda manutenção de estoque nem demanda automática de consumidores.

`buildAll runNeoForgeGameTests runForgeGameTests -PdemandGameTests --offline` aprovado, com cenários do núcleo nos dois JDKs e **11 GameTests por loader**. Log: `build/two-gauge-modes-validation.log`. As novas verificações usam Packagers e Mechanical Crafters reais para manter uma meta de 10, produzir quatro unidades adicionais por pedido manual e repor após uma retirada de oito unidades; uma variante solicita durante produção automática em andamento. Conferem também a ausência de lotes duplicados, persistência, migração dos modos e rejeição de pacotes de modo antigos.

A prévia estática dos cinco ícones foi atualizada. Revisão das duas telas dentro do Minecraft e em múltiplas escalas de GUI ainda pendente; os GameTests são evidência de servidor, não de interação visual.

## Pedidos habilitados por padrão e prévia de fabricação — 21/10/2026

`buildAll runNeoForgeGameTests runForgeGameTests -PdemandGameTests --offline` aprovado nos dois loaders. Log: `build/default-orders-crafting-preview-validation.log`. Os testes do núcleo cobrem a ordem pelas dependências mesmo quando a descoberta dos lotes vem em ordem diferente, componentes compartilhados, aproveitamento de estoque e quantidades arredondadas pelo rendimento.

Os **11 GameTests por loader** também verificam o padrão `ORDERS` em um gauge recém-colocado, leitura sem modo salvo, preservação do `STOCK` explícito e da migração do modo removido, além de uma prévia serializada `ToCraft` com quatro tábuas antes de um botão na cadeia física de fabricação. Os cenários anteriores de manutenção do slider e pedidos simultâneos à reposição continuam passando.

O mundo `neoforge/runs/client/saves/New World` foi inspecionado somente por leitura. Seu registro já continha as receitas de tábuas de pinheiro, gravetos e picareta na mesma rede; o jogador confirmou que os produtos voltaram a aparecer após conferir a busca. O catálogo e a sincronização de pesquisa foram preservados. Os servidores de teste usaram os mundos separados em `runs/gametest`.

A revisão visual das listas **Items to craft** e **Missing materials**, incluindo rolagem, nomes longos e escalas de GUI, ainda precisa ser feita dentro do Minecraft. Os JARs foram atualizados sem alterar a versão do mod.

## Preparação da versão 0.0.1 — 21/10/2026

`0.0.1` é a primeira versão do Create: On Demand. A numeração herdada da cópia do Larger Recipe Gauges não faz parte do histórico de releases deste mod. O changelog próprio registra as funcionalidades do projeto independente, e o README público descreve em inglês o uso, os modos e as versões suportadas.

`buildAll --offline` aprovado com os testes do núcleo nos dois JDKs. Log: `build/release-0.0.1-build.log`. Os JARs em `forge1201/build/libs` e `neoforge/build/libs` foram conferidos quanto a nome, versão nos metadados, licença empacotada e ausência de classes de teste; hashes em `build/release-0.0.1-artifacts.json`.

Os 11 GameTests de cada loader passaram antes desta alteração de versão, conforme a evidência da seção anterior; não foram repetidos para a mudança de metadados e documentação. A revisão visual permanece pendente. Esta pasta não tem repositório Git inicializado; nenhum commit, tag, push ou publicação foi realizado.
