package com.radaeli.createondemand.forge1201.mixin;

import com.radaeli.createondemand.forge1201.demand.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = StockKeeperRequestScreen.class, remap = false)
public abstract class DemandStockKeeperMixin extends AbstractSimiContainerScreen<StockKeeperRequestMenu> implements DemandClient.Host {
    @Shadow StockTickerBlockEntity blockEntity;
    @Shadow public List<List<BigItemStack>> displayedItems;
    @Shadow public List<StockKeeperRequestScreen.CategoryEntry> categories;
    @Shadow public EditBox searchBox;
    @Shadow public AddressEditBox addressBox;
    @Shadow public boolean refreshSearchNextTick;
    @Shadow int windowWidth;
    @Shadow private Couple<Integer> getHoveredSlot(int x, int y) { throw new AssertionError(); }
    @Shadow private void clampScrollBar() { throw new AssertionError(); }
    @Shadow public abstract boolean isSchematicListMode();
    @Unique private DemandPanel onDemand$panel;
    protected DemandStockKeeperMixin(StockKeeperRequestMenu menu, Inventory inv, Component title) { super(menu, inv, title); }
    @Override public DemandPanel demandPanel() { return onDemand$panel; }
    @Inject(method = "init", remap = true, at = @At("TAIL"))
    private void onDemand$init(CallbackInfo ci) {
        onDemand$panel = new DemandPanel((Screen) (Object) this, blockEntity, () -> refreshSearchNextTick = true, () -> addressBox.getValue());
        IconButton view = new IconButton(getGuiLeft() + windowWidth + 3, getGuiTop() + 18, DemandIcons.view(onDemand$panel.view));
        view.setToolTip(onDemand$panel.viewName()); view.withCallback(() -> { onDemand$panel.cycleView(); view.setIcon(DemandIcons.view(onDemand$panel.view)); view.setToolTip(onDemand$panel.viewName()); }); addRenderableWidget(view);
        IconButton orders = new IconButton(getGuiLeft() + windowWidth + 3, getGuiTop() + 40, AllIcons.I_SCROLL);
        orders.setToolTip(DemandPanel.text("orders")); orders.withCallback(() -> onDemand$panel.showOrders()); addRenderableWidget(orders);
    }
    // Create returns early for blank searches and absent stock snapshots. Publish on every exit.
    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void onDemand$catalog(boolean reset, CallbackInfo ci) {
        if (onDemand$panel == null || isSchematicListMode() || onDemand$panel.view == 1) return;
        displayedItems = new ArrayList<>(List.of(onDemand$panel.combine(displayedItems, searchBox.getValue())));
        categories.clear(); clampScrollBar();
    }
    @Inject(method = "containerTick", remap = true, at = @At("TAIL"))
    private void onDemand$tick(CallbackInfo ci) { if (onDemand$panel != null) onDemand$panel.tick(); }
    @Inject(method = "renderForeground", at = @At("HEAD"), cancellable = true)
    private void onDemand$render(GuiGraphics graphics, int mx, int my, float partial, CallbackInfo ci) {
        if (onDemand$panel != null && onDemand$panel.open()) {
            onDemand$panel.render(graphics, mx, my, partial); ci.cancel();
        }
    }
    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void onDemand$mark(GuiGraphics g, float scale, BigItemStack entry, boolean hovered, boolean order, CallbackInfo ci) {
        if (!order && onDemand$panel != null && onDemand$panel.highlighted(entry.stack)) {
            g.pose().pushPose(); g.pose().translate(0, 0, 211);
            int color = 0xFF90D67D;
            g.fill(0, 0, 18, 1, color); g.fill(0, 17, 18, 18, color);
            g.fill(0, 1, 1, 17, color); g.fill(17, 1, 18, 17, color);
            g.pose().popPose();
        }
        if (!order && onDemand$panel != null && onDemand$panel.craftable(entry.stack)) {
            g.pose().pushPose(); g.pose().translate(0, 0, 210);
            if (entry.count == 0) g.renderItem(entry.stack, 1, 1);
            g.drawString(font, "+", 13, 0, 0x55BB77, true); g.pose().popPose();
        }
    }
    @Inject(method = "mouseClicked", remap = true, at = @At("HEAD"), cancellable = true)
    private void onDemand$click(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (onDemand$panel.open()) { cir.setReturnValue(onDemand$panel.click(mx, my, button)); return; }
        if (button != 0 || isSchematicListMode()) return;
        Couple<Integer> hovered = getHoveredSlot((int) mx, (int) my);
        int group = hovered.getFirst(), index = hovered.getSecond();
        if (group < 0 || group >= displayedItems.size() || index < 0 || index >= displayedItems.get(group).size()) return;
        BigItemStack item = displayedItems.get(group).get(index);
        if ((item.count == 0 || Screen.hasControlDown()) && onDemand$panel.craftable(item.stack)) {
            searchBox.setFocused(false); addressBox.setFocused(false); onDemand$panel.fabricate(item.stack); cir.setReturnValue(true);
        }
    }
    @Inject(method = "keyPressed", remap = true, at = @At("HEAD"), cancellable = true)
    private void onDemand$key(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (onDemand$panel.open()) cir.setReturnValue(onDemand$panel.key(key, scan, mods));
    }
    @Inject(method = "charTyped", remap = true, at = @At("HEAD"), cancellable = true)
    private void onDemand$char(char c, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (onDemand$panel.open()) cir.setReturnValue(onDemand$panel.character(c, mods));
    }
    @Inject(method = "mouseScrolled", remap = true, at = @At("HEAD"), cancellable = true)
    private void onDemand$scroll(double mx, double my, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (onDemand$panel.open()) cir.setReturnValue(onDemand$panel.scroll(mx, my, dy));
    }
    @Inject(method = "mouseDragged", remap = true, at = @At("HEAD"), cancellable = true)
    private void onDemand$drag(double mx, double my, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (onDemand$panel.open()) cir.setReturnValue(onDemand$panel.drag(mx, my, button));
    }
}
