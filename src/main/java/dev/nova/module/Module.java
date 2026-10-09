package dev.nova.module;

import dev.nova.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name, description;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();

    public enum Category {
        COMBAT("Combat"), MOVEMENT("Movement"), RENDER("Render"), PLAYER("Player"), MISC("Misc"), HUD("HUD");
        public final String label;
        Category(String label) { this.label = label; }
    }

    private final Category category;

    protected Module(String name, String description) { this(name, description, Category.RENDER); }

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    public Category category() { return category; }

    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }

    public String name() { return name; }
    public String description() { return description; }
    public List<Setting<?>> settings() { return settings; }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }
    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(MinecraftClient mc) {}
    /** Runs at the START of every client tick (before the game reads input). */
    public void onPreTick(MinecraftClient mc) {}
    /** Runs once per rendered frame. */
    public void onFrame(MinecraftClient mc) {}
}
