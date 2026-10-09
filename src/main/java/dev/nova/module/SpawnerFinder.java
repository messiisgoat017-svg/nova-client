package dev.nova.module;

import dev.nova.setting.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;

public class SpawnerFinder extends EspModule {
    public final BoolSetting spawners      = add(new BoolSetting("Mob Spawners", true));
    public final BoolSetting trialSpawners = add(new BoolSetting("Trial Spawners", false));
    public final ColorSetting trialColor   = add(new ColorSetting("Trial Spawner Color", 0x4DD0E1));

    public SpawnerFinder() {
        super("Spawner Finder", "Highlights mob spawners (and optionally trial spawners).", 0xFF4D6D);
        // sensible defaults for this module
        fillOpacity.set(35.0);
        pulse.set(true);
        trialColor.visibleWhen(() -> colorMode.is("Per Type") && trialSpawners.get());
    }

    @Override protected String classify(BlockEntity be) {
        Block b = be.getCachedState().getBlock();
        if (b == Blocks.SPAWNER && spawners.get()) return "spawner";
        if (b == Blocks.TRIAL_SPAWNER && trialSpawners.get()) return "trial";
        return null;
    }

    @Override protected int typeColor(String type) {
        return type.equals("trial") ? trialColor.get() : color.get();
    }
}
