package dev.nova.module;

import dev.nova.setting.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class StorageFinder extends EspModule {
    private record Entry(String id, String label, boolean on, int color) {}

    private static final Entry[] ENTRIES = {
        new Entry("chest",     "Chests",          true,  0xFFB300),
        new Entry("trapped",   "Trapped Chests",  false, 0xFF5252),
        new Entry("ender",     "Ender Chests",    false, 0xB388FF),
        new Entry("barrel",    "Barrels",         false, 0xA1887F),
        new Entry("shulker",   "Shulker Boxes",   false, 0xF48FB1),
        new Entry("hopper",    "Hoppers",         true,  0x40C4FF),
        new Entry("dropper",   "Droppers",        true,  0x69F0AE),
        new Entry("dispenser", "Dispensers",      false, 0xFFD740),
        new Entry("crafter",   "Crafters",        false, 0xE0E0E0),
    };

    private final Map<String, BoolSetting> toggles = new LinkedHashMap<>();
    private final Map<String, ColorSetting> colors = new LinkedHashMap<>();

    public StorageFinder() {
        super("Storage Finder", "Chests, hoppers, droppers & more", 0xFFB300);
        for (Entry e : ENTRIES) {
            BoolSetting t = add(new BoolSetting(e.label(), e.on()));
            ColorSetting c = add(new ColorSetting(e.label() + " Color", e.color()));
            c.visibleWhen(() -> colorMode.is("Per Type") && t.get());
            toggles.put(e.id(), t);
            colors.put(e.id(), c);
        }
    }

    @Override
    protected String classify(BlockEntity be) {
        Block b = be.getCachedState().getBlock();
        String id = null;
        if (b == Blocks.CHEST) id = "chest";
        else if (b == Blocks.TRAPPED_CHEST) id = "trapped";
        else if (b == Blocks.ENDER_CHEST) id = "ender";
        else if (b == Blocks.BARREL) id = "barrel";
        else if (b instanceof ShulkerBoxBlock) id = "shulker";
        else if (b == Blocks.HOPPER) id = "hopper";
        else if (b == Blocks.DROPPER) id = "dropper";
        else if (b == Blocks.DISPENSER) id = "dispenser";
        else if (b == Blocks.CRAFTER) id = "crafter";
        return id != null && toggles.get(id).get() ? id : null;
    }

    @Override protected int typeColor(String type) {
        ColorSetting c = colors.get(type);
        return c == null ? 0xFFFFFF : c.get();
    }
}
