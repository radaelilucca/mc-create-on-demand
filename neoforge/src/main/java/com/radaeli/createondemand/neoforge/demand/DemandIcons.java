package com.radaeli.createondemand.neoforge.demand;

import com.radaeli.createondemand.client.DemandIcon;
import com.radaeli.createondemand.demand.DemandEngine.Mode;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

/** Create IconButton elements with a neutral highlight and a one-pixel shadow. */
public final class DemandIcons {
    private static final ScreenElement[] ICONS = new ScreenElement[DemandIcon.values().length];
    static {
        for (DemandIcon icon : DemandIcon.values())
            ICONS[icon.ordinal()] = (graphics, x, y) -> {
                draw(graphics, icon, x + 1, y + 1, 0xFF53535B);
                draw(graphics, icon, x, y, 0xFFF0F0F0);
            };
    }
    private DemandIcons() {}

    public static ScreenElement view(int view) { return ICONS[DemandIcon.view(view).ordinal()]; }
    public static ScreenElement mode(Mode mode) { return ICONS[DemandIcon.mode(mode).ordinal()]; }

    private static void draw(GuiGraphics graphics, DemandIcon icon, int x, int y, int color) {
        for (DemandIcon.Rect rect : icon.rectangles())
            graphics.fill(x + rect.x(), y + rect.y(), x + rect.x() + rect.width(), y + rect.y() + rect.height(), color);
    }
}
