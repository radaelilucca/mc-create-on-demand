package com.radaeli.createondemand.forge1201.validation;

import com.mojang.brigadier.Command;
import com.radaeli.createondemand.demand.*;
import com.radaeli.createondemand.demand.DemandEngine.Mode;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import java.util.*;

/** Explicit opt-in test command. This source set is never included in release JARs. */
@EventBusSubscriber(modid = "create_on_demand")
public final class DemandUiFixture {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ondemand_ui").requires(s -> s.hasPermission(2)).executes(context -> {
            var player = context.getSource().getPlayerOrException(); var level = player.serverLevel();
            BlockPos base = player.blockPosition().above(4); UUID network = UUID.randomUUID();
            for (int x = -2; x <= 6; x++) for (int z = -2; z <= 5; z++) level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            BlockPos tickerPos = base, boardPos = base.east(3), storage = base.east(5);
            level.setBlockAndUpdate(storage, Blocks.CHEST.defaultBlockState()); ((ChestBlockEntity) level.getBlockEntity(storage)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
            level.setBlockAndUpdate(storage.north(), AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(storage.north().above(), AllBlocks.STOCK_LINK.getDefaultState().setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
            ((PackagerLinkBlockEntity) level.getBlockEntity(storage.north().above())).behaviour.freqId = network;
            level.setBlockAndUpdate(tickerPos, AllBlocks.STOCK_TICKER.getDefaultState());
            ((StockTickerBlockEntity) level.getBlockEntity(tickerPos)).behaviour.freqId = network;
            level.setBlockAndUpdate(tickerPos.west(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
            level.setBlockAndUpdate(boardPos.south(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(boardPos, AllBlocks.FACTORY_GAUGE.getDefaultState().setValue(FactoryPanelBlock.FACE, AttachFace.WALL).setValue(FactoryPanelBlock.FACING, Direction.NORTH));
            FactoryPanelBlockEntity board = (FactoryPanelBlockEntity) level.getBlockEntity(boardPos);
            var slots = FactoryPanelBlock.PanelSlot.values(); for (int i = 0; i < 3; i++) board.addPanel(slots[i], network);
            FactoryPanelBehaviour logs = board.panels.get(slots[0]), planks = board.panels.get(slots[1]), slabs = board.panels.get(slots[2]);
            logs.setFilter(new ItemStack(Items.OAK_LOG)); planks.setFilter(new ItemStack(Items.OAK_PLANKS)); slabs.setFilter(new ItemStack(Items.OAK_SLAB));
            planks.recipeOutput = 4; planks.recipeAddress = "planks"; slabs.recipeOutput = 6; slabs.recipeAddress = "slabs";
            planks.targetedBy.put(logs.getPanelPosition(), new FactoryPanelConnection(logs.getPanelPosition(), 1)); logs.targeting.add(planks.getPanelPosition());
            slabs.targetedBy.put(planks.getPanelPosition(), new FactoryPanelConnection(planks.getPanelPosition(), 3)); planks.targeting.add(slabs.getPanelPosition());
            ((DemandGauge) planks).demandMode(Mode.ORDERS); ((DemandGauge) slabs).demandMode(Mode.ORDERS); board.notifyUpdate();
            player.teleportTo(base.getX() + .5, base.getY(), base.getZ() - 2.5);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(tickerPos));
            return Command.SINGLE_SUCCESS;
        }));
    }
}
