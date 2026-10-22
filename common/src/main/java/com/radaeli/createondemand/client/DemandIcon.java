package com.radaeli.createondemand.client;

import com.radaeli.createondemand.demand.DemandEngine.Mode;
import java.util.ArrayList;
import java.util.List;

/** Shared pixel geometry. Each glyph fits the Create button's native 16 by 16 icon area. */
public enum DemandIcon {
    // Crafting cells with an upward priority arrow.
    CRAFTABLE_FIRST(
            1, 3, 2, 2, 4, 3, 2, 2, 7, 3, 2, 2,
            1, 6, 2, 2, 4, 6, 2, 2, 7, 6, 2, 2,
            1, 9, 2, 2, 4, 9, 2, 2, 7, 9, 2, 2,
            12, 2, 1, 10, 11, 3, 3, 1, 10, 4, 5, 1),
    // Storage chest, with a lid and central latch.
    STORED(
            2, 4, 12, 1, 2, 5, 1, 8, 13, 5, 1, 8,
            3, 12, 10, 1, 3, 7, 10, 1, 7, 8, 2, 2),
    // A recipe grid, rather than a generic settings symbol.
    CRAFTABLE(
            2, 2, 12, 1, 2, 13, 12, 1, 2, 3, 1, 10, 13, 3, 1, 10,
            6, 3, 1, 10, 10, 3, 1, 10, 3, 6, 10, 1, 3, 10, 10, 1),
    // Storage with an upward replenishment arrow.
    MAINTAIN_STOCK(
            1, 6, 9, 1, 1, 7, 1, 7, 9, 7, 1, 7,
            2, 13, 7, 1, 2, 9, 7, 1, 5, 10, 1, 2,
            12, 2, 1, 10, 11, 3, 3, 1, 10, 4, 5, 1),
    // A production order accepted with a check mark.
    ACCEPT_ORDERS(
            2, 1, 10, 1, 2, 2, 1, 12, 11, 2, 1, 5, 3, 13, 5, 1,
            4, 4, 5, 1, 4, 6, 4, 1, 4, 8, 2, 1,
            7, 10, 1, 1, 8, 11, 1, 1, 9, 12, 1, 1,
            10, 11, 1, 1, 11, 10, 1, 1, 12, 9, 1, 1, 13, 8, 1, 1);

    public record Rect(int x, int y, int width, int height) {}
    private final List<Rect> rectangles;

    DemandIcon(int... coordinates) {
        List<Rect> result = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 4)
            result.add(new Rect(coordinates[i], coordinates[i + 1], coordinates[i + 2], coordinates[i + 3]));
        rectangles = List.copyOf(result);
    }

    public List<Rect> rectangles() { return rectangles; }

    public static DemandIcon view(int view) {
        return switch (view) {
            case 1 -> STORED;
            case 2 -> CRAFTABLE;
            default -> CRAFTABLE_FIRST;
        };
    }

    public static DemandIcon mode(Mode mode) {
        return switch (mode) {
            case STOCK -> MAINTAIN_STOCK;
            case ORDERS -> ACCEPT_ORDERS;
        };
    }
}
