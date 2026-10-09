package dev.nova.module;

import dev.nova.setting.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Shared logic + customisation for every block-entity highlighter. */
public abstract class EspModule extends Module {
    public record Target(Box box, String type, Vec3d center) {}

    // ---- look ----
    public final ModeSetting renderMode   = add(new ModeSetting("Render Mode", "Both", "Outline", "Filled", "Both"));
    public final NumberSetting outlineOpacity = add(new NumberSetting("Outline Opacity", 100, 0, 100, 1, "%"));
    public final NumberSetting fillOpacity    = add(new NumberSetting("Fill Opacity", 25, 0, 100, 1, "%"));
    public final NumberSetting lineWidth      = add(new NumberSetting("Line Width", 2.0, 0.5, 6, 0.5, "px"));
    public final NumberSetting expand         = add(new NumberSetting("Box Expand", 0.0, -0.1, 0.5, 0.01, ""));
    // ---- colour ----
    public final ModeSetting colorMode    = add(new ModeSetting("Color Mode", "Single", "Single", "Per Type", "Rainbow", "Distance"));
    public final ColorSetting color;
    public final NumberSetting rainbowSpeed = add(new NumberSetting("Rainbow Speed", 3, 1, 20, 1, ""));
    // ---- behaviour ----
    public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", true));
    public final ModeSetting tracers      = add(new ModeSetting("Tracers", "Off", "Off", "Crosshair", "Bottom"));
    public final NumberSetting range      = add(new NumberSetting("Range", 128, 16, 512, 8, " blk"));
    public final BoolSetting fade         = add(new BoolSetting("Fade With Distance", false));
    public final BoolSetting pulse        = add(new BoolSetting("Pulse", false));
    public final NumberSetting scanInterval = add(new NumberSetting("Scan Interval", 20, 2, 100, 1, " tk"));

    private List<Target> targets = List.of();
    private int ticks = 9999;

    protected EspModule(String name, String description, int defaultColor) {
        super(name, description);
        color = new ColorSetting("Color", defaultColor);
        // insert colour right after colorMode for a nicer layout
        settings().remove(color);
        settings().add(settings().indexOf(colorMode) + 1, color);

        outlineOpacity.visibleWhen(() -> !renderMode.is("Filled"));
        lineWidth.visibleWhen(() -> !renderMode.is("Filled"));
        fillOpacity.visibleWhen(() -> !renderMode.is("Outline"));
        color.visibleWhen(() -> colorMode.is("Single"));
        rainbowSpeed.visibleWhen(() -> colorMode.is("Rainbow"));
    }

    /** Return a type id for this block entity, or null to ignore it. */
    protected abstract String classify(BlockEntity be);
    /** Colour (0xRRGGBB) used when Color Mode = Per Type. */
    protected abstract int typeColor(String type);

    public List<Target> targets() { return targets; }

    @Override protected void onEnable() { ticks = 9999; }
    @Override protected void onDisable() { targets = List.of(); }

    public int colorFor(Target t, double dist, long ms) {
        switch (colorMode.get()) {
            case "Per Type": return typeColor(t.type());
            case "Rainbow": {
                float hue = (float) ((ms * rainbowSpeed.get() / 10000.0) % 1.0);
                return Color.HSBtoRGB(hue, 0.75f, 1f) & 0xFFFFFF;
            }
            case "Distance": {
                float f = (float) MathHelper.clamp(dist / range.get(), 0, 1);
                return Color.HSBtoRGB((1f - f) * 0.33f, 0.85f, 1f) & 0xFFFFFF; // green near -> red far
            }
            default: return color.get();
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (++ticks < scanInterval.i()) return;
        ticks = 0;
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return;

        List<Target> out = new ArrayList<>();
        double r = range.get(), r2 = r * r;
        int cr = (int) Math.min(mc.options.getViewDistance().getValue(), Math.ceil(r / 16.0) + 1);
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;
        Vec3d pp = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    if (Vec3d.ofCenter(pos).squaredDistanceTo(pp) > r2) continue;
                    String type = classify(be);
                    if (type == null) continue;
                    Box box = boxFor(w, pos, be.getCachedState());
                    if (box == null) continue;
                    out.add(new Target(box, type, box.getCenter()));
                }
            }
        }
        targets = out;
    }

    private static Box shapeBox(ClientWorld w, BlockPos pos) {
        VoxelShape s = w.getBlockState(pos).getOutlineShape(w, pos);
        return s.isEmpty() ? new Box(pos) : s.getBoundingBox().offset(pos);
    }

    /** Tight box around the block, merging double chests into one box. */
    private static Box boxFor(ClientWorld w, BlockPos pos, BlockState st) {
        Box box = shapeBox(w, pos);
        if (st.getBlock() instanceof ChestBlock && st.contains(ChestBlock.CHEST_TYPE)) {
            ChestType ct = st.get(ChestBlock.CHEST_TYPE);
            if (ct == ChestType.RIGHT) return null;             // the LEFT half draws the merged box
            if (ct == ChestType.LEFT) {
                Direction d = ChestBlock.getFacing(st);
                box = box.union(shapeBox(w, pos.offset(d)));
            }
        }
        return box;
    }
}
