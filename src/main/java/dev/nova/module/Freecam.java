package dev.nova.module;

import com.mojang.authlib.GameProfile;
import dev.nova.setting.BoolSetting;
import dev.nova.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side only free camera.
 *
 * How it stays client-side:
 *  - the camera is a separate dummy entity that is never added to the world / never sent to the server
 *  - the real player's input is replaced with an empty one, so it stands still (no movement packets)
 *  - mouse movement that the game applies to the real player is moved onto the camera each frame and
 *    the player's own yaw/pitch is put back, so no rotation packets change either
 *  - attack / use / pick-block presses are swallowed so you can't interact from the camera
 */
public class Freecam extends Module {
    public final NumberSetting speed = add(new NumberSetting("Speed", 0.5, 0.05, 3.0, 0.05, " b/t"));
    public final NumberSetting boost = add(new NumberSetting("Sprint Boost", 2.0, 1.0, 5.0, 0.5, "x"));
    public final BoolSetting disableOnDamage = add(new BoolSetting("Disable On Damage", true));

    private CamEntity cam;
    private ClientPlayerEntity tracked;
    private Input savedInput;
    private Vec3d pos = Vec3d.ZERO;
    private float camYaw, camPitch, savedYaw, savedPitch;
    private long lastNs;
    private double lastCx, lastCy;
    private boolean prevGrabbed;

    /**
     * Dummy camera entity. It ignores anything the game tries to do to its rotation (mouse look) and
     * reports its exact yaw/pitch/position (no tick interpolation), which removes camera jitter.
     */
    private static final class CamEntity extends OtherClientPlayerEntity {
        CamEntity(ClientWorld world, GameProfile profile) { super(world, profile); }
        public void changeLookDirection(double dx, double dy) { /* ignored */ }
        public float getYaw(float tickDelta) { return getYaw(); }
        public float getPitch(float tickDelta) { return getPitch(); }
    }

    public Freecam() {
        super("Freecam", "Detach the camera and fly around", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) { setEnabled(false); return; }

        tracked = mc.player;
        savedYaw = camYaw = mc.player.getYaw();
        savedPitch = camPitch = mc.player.getPitch();
        pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        cam = new CamEntity(mc.world, mc.player.getGameProfile());
        cam.refreshPositionAndAngles(pos.x, pos.y, pos.z, camYaw, camPitch);

        savedInput = mc.player.input;
        mc.player.input = new Input() {};      // empty input -> real player does not move
        mc.setCameraEntity(cam);
        lastNs = System.nanoTime();
        prevGrabbed = false;
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            if (mc.player == tracked) {
                if (savedInput != null) mc.player.input = savedInput;
                mc.player.setYaw(savedYaw);
                mc.player.setPitch(savedPitch);
            }
            mc.setCameraEntity(mc.player);
        }
        cam = null;
        tracked = null;
        savedInput = null;
    }

    /** Swallow interaction keys before the game processes them this tick. */
    @Override
    public void onPreTick(MinecraftClient mc) {
        if (cam == null) return;
        for (KeyBinding k : new KeyBinding[] { mc.options.attackKey, mc.options.useKey, mc.options.pickItemKey }) {
            while (k.wasPressed()) { /* drain */ }
            k.setPressed(false);
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.player != tracked) { setEnabled(false); return; }
        if (disableOnDamage.get() && mc.player.hurtTime > 0) setEnabled(false);
    }

    /** Runs every frame: apply mouse look + movement to the camera. */
    @Override
    public void onFrame(MinecraftClient mc) {
        if (cam == null || mc.player == null || mc.player != tracked) return;

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        // mouse look: read the raw cursor movement ourselves (same maths as vanilla), so it works no matter
        // which entity the game applies its own mouse rotation to
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cx, cy);
        boolean grabbed = mc.currentScreen == null && mc.mouse.isCursorLocked();
        if (grabbed && prevGrabbed) {
            double d = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double f = d * d * d * 8.0 * 0.15;
            camYaw += (float) ((cx[0] - lastCx) * f);
            camPitch = MathHelper.clamp(camPitch + (float) ((cy[0] - lastCy) * f), -90f, 90f);
        }
        lastCx = cx[0];
        lastCy = cy[0];
        prevGrabbed = grabbed;

        // undo whatever the game did to the real player's rotation so nothing is sent to the server
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);

        // movement
        double yaw = Math.toRadians(camYaw), pitch = Math.toRadians(camPitch);
        Vec3d look = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));

        double fwd = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double str = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
        double up = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

        Vec3d move = look.multiply(fwd).add(right.multiply(str)).add(0, up, 0);
        if (move.lengthSquared() > 1e-6) {
            double perSecond = speed.get() * 20.0 * (mc.options.sprintKey.isPressed() ? boost.get() : 1.0);
            pos = pos.add(move.normalize().multiply(perSecond * dt));
        }
        cam.refreshPositionAndAngles(pos.x, pos.y, pos.z, camYaw, camPitch);
    }
}
