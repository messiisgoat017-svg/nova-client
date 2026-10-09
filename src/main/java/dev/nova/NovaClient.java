package dev.nova;

import dev.nova.config.Config;
import dev.nova.gui.ClickGui;
import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.render.EspRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class NovaClient implements ClientModInitializer {
    public static final int GUI_KEY = GLFW.GLFW_KEY_LEFT_ALT;
    public static ModuleManager modules;
    private boolean prevKey;

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        Config.load(modules);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // If this event name differs in your Fabric API build, try END_MAIN / BEFORE_DEBUG_RENDER.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> EspRenderer.render(ctx.matrices(), modules));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Config.save(modules));
    }

    private void tick(MinecraftClient mc) {
        // Polling GLFW keeps this independent of KeyBinding API changes between versions.
        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), GUI_KEY) == GLFW.GLFW_PRESS;
        if (down && !prevKey && mc.currentScreen == null && mc.player != null) mc.setScreen(new ClickGui());
        prevKey = down;

        if (mc.world == null || mc.player == null) return;
        for (Module m : modules.all()) if (m.isEnabled()) m.onTick(mc);
    }
}
