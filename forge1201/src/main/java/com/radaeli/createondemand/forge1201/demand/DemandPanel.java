package com.radaeli.createondemand.forge1201.demand;

import com.radaeli.createondemand.demand.DemandEngine.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.*;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Modal panels rendered by the existing Stock Keeper screen, keeping its menu alive. */
public final class DemandPanel {
    private final Screen host;
    private final StockTickerBlockEntity ticker;
    private final Runnable refresh;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private CompoundTag snapshot = new CompoundTag(), preview = new CompoundTag();
    private final List<CompoundTag> catalog = new ArrayList<>();
    private List<CompoundTag> producers = List.of();
    private static final AllGuiTextures FRAME = AllGuiTextures.SCHEDULE;
    private static final int INSET = 26, TEXT_COLOR = 0xF2F2F2, TITLE_COLOR = 0x30303A;
    private EditBox quantity;
    private ScrollInput quantityScroll;
    private Label producerLabel;
    private IconButton submit, delete;
    private UUID renderedOrder;
    private final List<ItemStack> highlights = new ArrayList<>();
    private final Set<UUID> deleting = new HashSet<>();
    private int panel, producer, age, scroll, x, y, h, orderSelection;
    private int previewQuantity;
    private int listTop, listBottom, maxScroll, scrollThumbTop, scrollThumbHeight, scrollGrab = -1;
    private UUID previewRoot;
    private String error = "";
    public int view;
    private final Minecraft mc = Minecraft.getInstance();
    private record ContentRow(Component label, ItemStack item) {
        ContentRow(Component label) { this(label, ItemStack.EMPTY); }
    }
    public DemandPanel(Screen host, StockTickerBlockEntity ticker, Runnable refresh) {
        this.host = host; this.ticker = ticker; this.refresh = refresh;
        send("open", new CompoundTag());
    }
    public static Component text(String key, Object... values) { return Component.translatable("gui.create_on_demand.demand." + key, values); }
    public boolean open() { return panel != 0; }
    public void tick() {
        if (++age % 40 == 0) send("catalog", new CompoundTag());
        for (AbstractWidget widget : widgets) if (widget instanceof AbstractSimiWidget simi) simi.tick();
        if (panel == 1 && quantity != null && age % 10 == 0 && previewQuantity != amount()) requestPreview();
    }
    public void receive(CompoundTag tag) {
        if (tag.getLong("Pos") != ticker.getBlockPos().asLong()) return;
        snapshot = tag; catalog.clear();
        for (Tag v : tag.getList("Catalog", Tag.TAG_COMPOUND)) catalog.add((CompoundTag) v);
        highlights.clear();
        for (Tag value : tag.getList("Highlights", Tag.TAG_COMPOUND)) highlights.add(stack(((CompoundTag) value).getString("Item")));
        Set<UUID> activeOrders = new HashSet<>();
        for (Tag value : tag.getList("Orders", Tag.TAG_COMPOUND)) activeOrders.add(((CompoundTag) value).getUUID("Id"));
        deleting.retainAll(activeOrders);
        if (tag.contains("Error")) deleting.clear();
        refresh.run();
        if (tag.contains("Preview") && panel == 1 && tag.getInt("Quantity") == amount()
                && !producers.isEmpty() && tag.getUUID("Root").equals(producers.get(producer).getUUID("Id"))) {
            preview = tag.getCompound("Preview"); previewQuantity = amount(); previewRoot = tag.getUUID("Root");
        }
        if (tag.getBoolean("Accepted")) { showOrders(); }
        if (tag.contains("Error")) error = tag.getString("Error");
    }
    public ItemStack stack(String encoded) {
        try { return ItemStack.of(TagParser.parseTag(encoded)); }
        catch (Exception ignored) { return ItemStack.EMPTY; }
    }
    public List<CompoundTag> producers(ItemStack item) {
        return catalog.stream().filter(t -> ItemStack.isSameItemSameTags(stack(t.getCompound("Output").getString("Item")), item))
                .sorted(Comparator.comparing(t -> t.getString("Location") + t.getUUID("Id"))).toList();
    }
    public boolean highlighted(ItemStack item) { return highlights.stream().anyMatch(stack -> ItemStack.isSameItemSameTags(stack, item)); }
    public boolean craftable(ItemStack item) { return !producers(item).isEmpty(); }
    public List<BigItemStack> combine(List<List<BigItemStack>> source, String query) {
        List<BigItemStack> result = new ArrayList<>();
        if (view != 2) for (List<BigItemStack> group : source) for (BigItemStack entry : group)
            if (result.stream().noneMatch(e -> ItemStack.isSameItemSameTags(e.stack, entry.stack))) result.add(entry);
        if (view != 1) for (CompoundTag recipe : catalog) {
            ItemStack item = stack(recipe.getCompound("Output").getString("Item"));
            if (item.isEmpty() || !matches(item, query)) continue;
            if (result.stream().noneMatch(e -> ItemStack.isSameItemSameTags(e.stack, item))) result.add(new BigItemStack(item, 0));
        }
        if (view == 2) {
            for (BigItemStack entry : result) entry.count = ticker.getLastClientsideStockSnapshotAsSummary() == null ? 0
                    : ticker.getLastClientsideStockSnapshotAsSummary().getCountOf(entry.stack);
        }
        result.sort(Comparator.<BigItemStack, Boolean>comparing(e -> !craftable(e.stack))
                .thenComparing(e -> e.stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    private static boolean matches(ItemStack item, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        if (q.startsWith("@")) return BuiltInRegistries.ITEM.getKey(item.getItem()).getNamespace().contains(q.substring(1));
        if (q.startsWith("#")) return item.getTags().anyMatch(t -> t.location().toString().contains(q.substring(1)));
        return item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q) || BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().contains(q);
    }
    public void cycleView() { view = (view + 1) % 3; refresh.run(); }
    public Component viewName() { return text("view." + view); }
    public void fabricate(ItemStack item) {
        producers = producers(item); if (producers.isEmpty()) return;
        panel = 1; producer = 0; scroll = 0; error = ""; previewQuantity = 0; previewRoot = null;
        geometry(); widgets.clear();
        quantity = new EditBox(new NoShadowFontWrapper(mc.font), x + 142, y + 52, 88, 18, text("quantity"));
        quantity.setMaxLength(5); quantity.setFilter(s -> s.isEmpty() || s.matches("[0-9]+")); quantity.setValue("1");
        quantity.setTextColor(TEXT_COLOR); quantity.setFocused(true); quantity.setHighlightPos(0); widgets.add(quantity);
        quantityScroll = new ScrollInput(quantity.getX(), quantity.getY(), quantity.getWidth(), quantity.getHeight())
                .withRange(1, 65537).withShiftStep(16).setState(1).titled(text("quantity").copy())
                .calling(value -> quantity.setValue(Integer.toString(value)));
        widgets.add(quantityScroll);
        producerLabel = null;
        // A single producer needs no selector. Multiple producers use Create's native selection control.
        if (producers.size() > 1) {
            List<Component> options = new ArrayList<>();
            for (int i = 0; i < producers.size(); i++) options.add(text("producer_option", i + 1, producers.get(i).getString("Location")));
            producerLabel = new Label(x + INSET, y + 81, Component.empty()).colored(TEXT_COLOR);
            ScrollInput selector = new SelectionScrollInput(x + INSET - 2, y + 77, contentWidth() + 4, 18)
                    .forOptions(options).titled(text("producer_select").copy()).writingTo(producerLabel).setState(0)
                    .calling(value -> { producer = value; scroll = 0; previewQuantity = 0; requestPreview(); });
            widgets.add(selector); widgets.add(producerLabel);
        }
        button(x + 22, footerY(), AllIcons.I_CONFIG_BACK, "back", this::close);
        submit = button(x + FRAME.getWidth() - 42, footerY(), AllIcons.I_CONFIRM, "submit", () -> {
            if (!ready()) return;
            send("submit", selection());
            previewQuantity = 0; submit.active = false;
        });
        requestPreview();
    }
    public void showOrders() {
        panel = 2; error = ""; scroll = 0; geometry(); widgets.clear();
        button(x + 22, footerY(), AllIcons.I_CONFIG_BACK, "back", this::close);
        button(x + FRAME.getWidth() - 90, footerY(), AllIcons.I_CONFIG_PREV, "previous", () -> { orderSelection = Math.max(0, orderSelection - 1); scroll = 0; });
        button(x + FRAME.getWidth() - 66, footerY(), AllIcons.I_CONFIG_NEXT, "next", () -> { orderSelection++; scroll = 0; });
        delete = button(x + FRAME.getWidth() - 42, footerY(), AllIcons.I_TRASH, "cancel", () -> {
            UUID id = renderedOrder;
            if (id == null || deleting.contains(id)) return;
            boolean present = false;
            for (Tag value : snapshot.getList("Orders", Tag.TAG_COMPOUND)) if (((CompoundTag) value).getUUID("Id").equals(id)) present = true;
            if (!present) return; // A return may have removed the displayed order since the last frame.
            deleting.add(id); delete.active = false;
            CompoundTag tag = new CompoundTag(); tag.putUUID("Order", id); send("cancel", tag);
        });
    }
    private CompoundTag selectedOrder() {
        ListTag orders = snapshot.getList("Orders", Tag.TAG_COMPOUND);
        if (orders.isEmpty()) return null;
        orderSelection = Math.min(orderSelection, orders.size() - 1); return orders.getCompound(orders.size() - 1 - orderSelection);
    }
    private void close() { panel = 0; widgets.clear(); quantity = null; quantityScroll = null; producerLabel = null; error = ""; }
    private void geometry() { h = Math.min(FRAME.getHeight(), host.height - 16); x = (host.width - FRAME.getWidth()) / 2; y = (host.height - h) / 2; }
    private int contentWidth() { return FRAME.getWidth() - INSET * 2; }
    private int footerY() { return y + h - 30; }
    private IconButton button(int bx, int by, AllIcons icon, String label, Runnable action) {
        IconButton button = new IconButton(bx, by, icon); button.setToolTip(text(label)); button.withCallback(action); widgets.add(button); return button;
    }
    private int amount() {
        try { int value = Integer.parseInt(quantity.getValue()); return value >= 1 && value <= 65536 ? value : 0; }
        catch (NumberFormatException e) { return 0; }
    }
    private CompoundTag selection() { CompoundTag tag = new CompoundTag(); tag.putUUID("Root", producers.get(producer).getUUID("Id")); tag.putInt("Quantity", amount()); return tag; }
    private void requestPreview() {
        if (producers.isEmpty() || amount() < 1) return;
        preview = new CompoundTag(); previewRoot = null; scroll = 0; send("preview", selection());
    }
    private boolean ready() {
        return amount() > 0 && amount() == previewQuantity && previewRoot != null && previewRoot.equals(producers.get(producer).getUUID("Id"))
                && preview.getString("Error").isEmpty();
    }
    private void send(String action, CompoundTag tag) { tag.putString("Action", action); tag.putLong("Pos", ticker.getBlockPos().asLong()); DemandNetwork.send(tag); }
    /** Slice the original Create frame vertically; keep its border scale and footer intact. */
    private void renderFrame(GuiGraphics g) {
        int header = 16, footer = 36, body = FRAME.getHeight() - header - footer;
        g.blit(FRAME.location, x, y, FRAME.getStartX(), FRAME.getStartY(), FRAME.getWidth(), header);
        for (int row = header; row < h - footer; row += body) {
            int slice = Math.min(body, h - footer - row);
            g.blit(FRAME.location, x, y + row, FRAME.getStartX(), FRAME.getStartY() + header, FRAME.getWidth(), slice);
        }
        g.blit(FRAME.location, x, y + h - footer, FRAME.getStartX(), FRAME.getStartY() + FRAME.getHeight() - footer, FRAME.getWidth(), footer);
    }
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (!open()) return;
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.fill(0, 0, host.width, host.height, 0xB0000000);
        renderFrame(g);
        Component title = text(panel == 1 ? "title" : "orders");
        // Match Create's normal window title font, without a drop shadow.
        g.drawString(mc.font, title, x + (FRAME.getWidth() - mc.font.width(title)) / 2, y + 4, TITLE_COLOR, false);
        List<ContentRow> lines = new ArrayList<>();
        boolean hasMaterials = false;
        ItemStack product = ItemStack.EMPTY;
        int contentTop;
        if (panel == 1) {
            CompoundTag recipe = producers.get(producer);
            product = stack(recipe.getCompound("Output").getString("Item"));
            g.drawString(mc.font, text("quantity"), x + INSET, y + 57, TEXT_COLOR, false);
            contentTop = y + (producers.size() > 1 ? 106 : 84);
            if (amount() == 0) lines.add(new ContentRow(text("quantity_range")));
            else if (previewRoot == null || previewQuantity != amount()) lines.add(new ContentRow(text("calculating")));
            else {
                ListTag missing = preview.getList("Missing", Tag.TAG_COMPOUND);
                ListTag materials = !missing.isEmpty() ? missing : preview.getList("ToCraft", Tag.TAG_COMPOUND);
                if (!materials.isEmpty()) {
                    hasMaterials = true;
                    lines.add(new ContentRow(text(!missing.isEmpty() ? "missing" : "to_craft")));
                    for (Tag v : materials) {
                        CompoundTag material = (CompoundTag) v;
                        ItemStack item = stack(material.getString("Item"));
                        lines.add(new ContentRow(Component.literal(material.getInt("Count") + " \u00d7 ")
                                .append(item.getHoverName()), item));
                    }
                }
                if (!preview.getString("Error").isEmpty()) {
                    lines.add(new ContentRow(text("error." + preview.getString("Error"))));
                    for (Tag v : preview.getList("Cycle", Tag.TAG_COMPOUND))
                        lines.add(new ContentRow(Component.literal("\u2192 ").append(stack(((CompoundTag) v).getString("Item")).getHoverName())));
                }
            }
            submit.active = ready();
            if (producerLabel != null) producerLabel.setTextAndTrim(text("producer_option", producer + 1, recipe.getString("Location")), false, contentWidth());
        } else {
            contentTop = y + 57;
            CompoundTag order = selectedOrder();
            renderedOrder = order == null ? null : order.getUUID("Id");
            delete.active = renderedOrder != null && !deleting.contains(renderedOrder);
            if (order == null) lines.add(new ContentRow(text("no_orders")));
            else {
                for (Tag v : order.getList("Graph", Tag.TAG_COMPOUND)) {
                    CompoundTag recipe = (CompoundTag) v;
                    if (recipe.getUUID("Id").equals(order.getUUID("Root"))) product = stack(recipe.getCompound("Output").getString("Item"));
                }
                lines.add(new ContentRow(text("progress", order.getInt("Received"), order.getInt("Quantity"), order.getInt("Sent"))));
                lines.add(new ContentRow(text("state." + order.getString("State").toLowerCase(Locale.ROOT))));
                if (!order.getString("Reason").isEmpty()) lines.add(new ContentRow(text("reason." + order.getString("Reason"))));
                lines.add(new ContentRow(text("order_number", orderSelection + 1, snapshot.getList("Orders", Tag.TAG_COMPOUND).size())));
            }
        }
        // Clip the content and widgets to the frame interior, separately from footer controls.
        g.enableScissor(x + INSET - 2, y + 22, x + FRAME.getWidth() - INSET + 2, y + h - 40);
        if (hasMaterials)
            g.fill(x + INSET, contentTop - 7, scrollbarX() - 4, contentTop - 6, 0x20D0D0D0);
        if (!product.isEmpty()) {
            g.renderItem(product, x + INSET, y + 28);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(product.getHoverName().getString(), contentWidth() - 22), x + INSET + 22, y + 32, TEXT_COLOR, false);
        }
        if (!error.isEmpty()) lines.add(new ContentRow(text("request_error", text("error." + error))));
        ItemStack hoveredMaterial = renderContent(g, lines, contentTop, y + h - 40, mx, my);
        for (AbstractWidget widget : widgets) if (!(widget instanceof IconButton)) widget.render(g, mx, my, partial);
        g.disableScissor();
        for (AbstractWidget widget : widgets) if (widget instanceof IconButton) widget.render(g, mx, my, partial);
        if (!hoveredMaterial.isEmpty()) g.renderTooltip(mc.font, hoveredMaterial, mx, my);
        for (AbstractWidget widget : widgets) if (widget instanceof AbstractSimiWidget simi && widget.isMouseOver(mx, my) && !simi.getToolTip().isEmpty())
            g.renderComponentTooltip(mc.font, simi.getToolTip(), mx, my);
        g.pose().popPose();
    }
    /** Pixel scrolling keeps wrapped item names and their 16px icons in the same table row. */
    private ItemStack renderContent(GuiGraphics g, List<ContentRow> rows, int top, int bottom, int mx, int my) {
        listTop = top; listBottom = Math.max(top, bottom);
        int viewport = listBottom - listTop;
        int left = x + INSET, right = scrollbarX() - 4;
        List<List<net.minecraft.util.FormattedCharSequence>> labels = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        int total = 0;
        for (ContentRow row : rows) {
            boolean material = !row.item().isEmpty();
            var wrapped = mc.font.split(row.label(), Math.max(1, right - left - (material ? 22 : 0)));
            int height = Math.max(material ? 22 : 11, wrapped.size() * 11 + (material ? 6 : 0));
            labels.add(wrapped); heights.add(height); total += height;
        }
        maxScroll = Math.max(0, total - viewport);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (viewport == 0) return ItemStack.EMPTY;
        ItemStack hovered = ItemStack.EMPTY;
        g.enableScissor(left, listTop, right, listBottom);
        int rowY = listTop - scroll;
        for (int i = 0; i < rows.size(); i++) {
            ContentRow row = rows.get(i);
            int height = heights.get(i);
            if (rowY + height > listTop && rowY < listBottom) {
                boolean material = !row.item().isEmpty();
                int textX = left + (material ? 22 : 0);
                // Center the visible text, excluding interline spacing below the last line.
                int textHeight = (labels.get(i).size() - 1) * 11 + mc.font.lineHeight;
                int textY = rowY + (height - textHeight) / 2 + 1;
                if (material) {
                    g.renderItem(row.item(), left, rowY + (height - 16) / 2);
                    if (mx >= left && mx < right && my >= Math.max(rowY, listTop) && my < Math.min(rowY + height, listBottom)) hovered = row.item();
                }
                for (int line = 0; line < labels.get(i).size(); line++)
                    g.drawString(mc.font, labels.get(i).get(line), textX, textY + line * 11, TEXT_COLOR, false);
                if (material && i + 1 < rows.size() && !rows.get(i + 1).item().isEmpty())
                    g.fill(left, rowY + height - 1, right, rowY + height, 0x40D0D0D0);
            }
            rowY += height;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            scrollThumbHeight = Math.min(viewport, Math.max(8, viewport * viewport / total));
            scrollThumbTop = listTop + (int) ((long) scroll * (viewport - scrollThumbHeight) / maxScroll);
            g.fill(scrollbarX(), listTop, scrollbarX() + 4, listBottom, 0x30303030);
            g.fill(scrollbarX(), scrollThumbTop, scrollbarX() + 4, scrollThumbTop + scrollThumbHeight, 0xFFAFAFAF);
        }
        return hovered;
    }
    private int scrollbarX() { return x + FRAME.getWidth() - INSET - 4; }
    private void scrollTo(double mouseY) {
        int travel = listBottom - listTop - scrollThumbHeight;
        if (travel > 0) scroll = (int) Math.max(0, Math.min(maxScroll, (mouseY - listTop - scrollGrab) * maxScroll / travel));
    }
    public boolean drag(double mx, double my, int button) {
        if (button == 0 && scrollGrab >= 0 && maxScroll > 0) scrollTo(my);
        return true;
    }
    public boolean click(double mx, double my, int button) {
        scrollGrab = -1;
        if (button == 0 && maxScroll > 0 && mx >= scrollbarX() - 2 && mx < scrollbarX() + 6 && my >= listTop && my < listBottom) {
            scrollGrab = my >= scrollThumbTop && my < scrollThumbTop + scrollThumbHeight ? (int) my - scrollThumbTop : scrollThumbHeight / 2;
            scrollTo(my); return true;
        }
        for (AbstractWidget widget : widgets) if (widget.mouseClicked(mx, my, button)) {
            for (AbstractWidget other : widgets) other.setFocused(other == widget); return true;
        }
        return true;
    }
    public boolean key(int key, int scan, int modifiers) {
        if (key == 256) { close(); return true; }
        if (key == 257 && panel == 1 && ready()) { submit.mouseClicked(submit.getX() + 1, submit.getY() + 1, 0); return true; }
        if (key == 258 && !widgets.isEmpty()) {
            int focused = -1;
            for (int i = 0; i < widgets.size(); i++) if (widgets.get(i).isFocused()) focused = i;
            int next = Math.floorMod(focused + (Screen.hasShiftDown() ? -1 : 1), widgets.size());
            for (int i = 0; i < widgets.size(); i++) widgets.get(i).setFocused(i == next);
            return true;
        }
        for (AbstractWidget widget : widgets) if (widget.isFocused() && widget.keyPressed(key, scan, modifiers)) return true;
        return true;
    }
    public boolean character(char c, int mods) { for (AbstractWidget widget : widgets) if (widget.isFocused()) widget.charTyped(c, mods); return true; }
    public boolean scroll(double mx, double my, double delta) {
        if (quantityScroll != null && panel == 1 && quantityScroll.isMouseOver(mx, my)) {
            quantityScroll.setState(Math.max(1, amount())); quantityScroll.mouseScrolled(mx, my, delta); return true;
        }
        for (AbstractWidget widget : widgets) if (widget instanceof SelectionScrollInput && widget.isMouseOver(mx, my)) {
            widget.mouseScrolled(mx, my, delta); return true;
        }
        if (delta != 0 && mx >= x + INSET && mx < x + FRAME.getWidth() - INSET && my >= listTop && my < listBottom)
            scroll = Math.max(0, Math.min(maxScroll, scroll + (delta > 0 ? -22 : 22)));
        return true;
    }
}
