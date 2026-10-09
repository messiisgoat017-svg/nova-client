package dev.nova.module;

import dev.nova.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name, description;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String name, String description) { this.name = name; this.description = description; }

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
}
