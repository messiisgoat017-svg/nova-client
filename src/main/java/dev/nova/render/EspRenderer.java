package dev.nova.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nova.module.EspModule;
import dev.nova.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Draws everything as camera-facing translucent quads (fills = box faces, outlines/tracers = thin
 * billboarded ribbons). That needs only ONE simple pipeline (POSITION_COLOR quads) and avoids the
 * line pipeline whose vertex format changed in 1.21.11.
 *
 * If a name/signature here doesn't match your mappings build, this is the only file that should need
 * touching (pipeline + RenderLayer creation, and the camera position getter).
 */
public final class EspRenderer {
    private static RenderLayer through, depth;

    private static RenderLayer layer(boolean throughWalls) {
        if (through == null) {
            through = makeLayer("through", DepthTestFunction.NO_DEPTH_TEST);
            depth   = makeLayer("depth", DepthTestFunction.LEQUAL_DEPTH_TEST);
        }
        return throughWalls ? through : depth;
    }

    private static RenderLayer makeLayer(String id, DepthTestFunction test) {
        RenderPipeline p = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of("nova", "pipeline/esp_" + id))
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(test)
                .withDepthWrite(false)
                .build());
        // 1.21.11 uses RenderSetup. If your build still has the older API use:
        // RenderLayer.of("nova_esp_" + id, 1536, false, true, p, RenderLayer.MultiPhaseParameters.builder().build(false));
        return RenderLayer.of("nova_esp_" + id, RenderSetup.builder(p).translucent().build());
    }

    public static void render(MatrixStack matrices, ModuleManager mm) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();   // older mappings: getPos()
        Vec3d look = mc.player.getRotationVec(1f);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        long now = System.currentTimeMillis();
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();

        for (EspModule m : mm.esp()) {
            if (!m.isEnabled() || m.targets().isEmpty()) continue;

            RenderLayer rl = layer(m.throughWalls.get());
            VertexConsumer vc = imm.getBuffer(rl);

            boolean fill = !m.renderMode.is("Outline");
            boolean line = !m.renderMode.is("Filled");
            double range = m.range.get();
            float pulse = m.pulse.get() ? (float) (0.65 + 0.35 * Math.sin(now / 300.0)) : 1f;

            for (EspModule.Target t : m.targets()) {
                Vec3d c = t.center();
                double dist = c.distanceTo(cam);
                if (dist > range) continue;

                int rgb = m.colorFor(t, dist, now);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                float fade = m.fade.get() ? (float) MathHelper.clamp(1.0 - dist / range * 0.8, 0.2, 1.0) : 1f;
                float outA = m.outlineOpacity.f() / 100f * fade * pulse;
                float fillA = m.fillOpacity.f() / 100f * fade * pulse;

                Box box = t.box().expand(m.expand.get());
                float x0 = (float) (box.minX - cam.x), y0 = (float) (box.minY - cam.y), z0 = (float) (box.minZ - cam.z);
                float x1 = (float) (box.maxX - cam.x), y1 = (float) (box.maxY - cam.y), z1 = (float) (box.maxZ - cam.z);

                if (fill && fillA > 0.001f) fillBox(vc, mat, x0, y0, z0, x1, y1, z1, r, g, b, a(fillA));
                if (line && outA > 0.001f) {
                    float hw = (float) Math.max(0.002, m.lineWidth.get() * dist * 0.0007);
                    edges(vc, mat, x0, y0, z0, x1, y1, z1, hw, r, g, b, a(outA));
                }
                if (!m.tracers.is("Off")) {
                    float ta = Math.max(outA, 0.35f * fade);
                    Vec3d start = look.multiply(0.5);
                    if (m.tracers.is("Bottom")) start = start.add(0, -0.35, 0);
                    ribbon(vc, mat, (float) start.x, (float) start.y, (float) start.z,
                           (float) (c.x - cam.x), (float) (c.y - cam.y), (float) (c.z - cam.z),
                           (float) Math.max(0.002, 1.2 * dist * 0.0007), r, g, b, a(ta));
                }
            }
            imm.draw(rl);
        }
    }

    private static int a(float f) { return MathHelper.clamp((int) (f * 255), 0, 255); }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int al) {
        vc.vertex(m, ax, ay, az).color(r, g, b, al);
        vc.vertex(m, bx, by, bz).color(r, g, b, al);
        vc.vertex(m, cx, cy, cz).color(r, g, b, al);
        vc.vertex(m, dx, dy, dz).color(r, g, b, al);
    }

    private static void fillBox(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                                int r, int g, int b, int al) {
        quad(vc, m, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,b,al); // bottom
        quad(vc, m, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, r,g,b,al); // top
        quad(vc, m, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,b,al); // north
        quad(vc, m, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b,al); // south
        quad(vc, m, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b,al); // west
        quad(vc, m, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,b,al); // east
    }

    private static void edges(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                              float hw, int r, int g, int b, int al) {
        // 4 bottom, 4 top, 4 vertical
        ribbon(vc,m,x0,y0,z0,x1,y0,z0,hw,r,g,b,al); ribbon(vc,m,x1,y0,z0,x1,y0,z1,hw,r,g,b,al);
        ribbon(vc,m,x1,y0,z1,x0,y0,z1,hw,r,g,b,al); ribbon(vc,m,x0,y0,z1,x0,y0,z0,hw,r,g,b,al);
        ribbon(vc,m,x0,y1,z0,x1,y1,z0,hw,r,g,b,al); ribbon(vc,m,x1,y1,z0,x1,y1,z1,hw,r,g,b,al);
        ribbon(vc,m,x1,y1,z1,x0,y1,z1,hw,r,g,b,al); ribbon(vc,m,x0,y1,z1,x0,y1,z0,hw,r,g,b,al);
        ribbon(vc,m,x0,y0,z0,x0,y1,z0,hw,r,g,b,al); ribbon(vc,m,x1,y0,z0,x1,y1,z0,hw,r,g,b,al);
        ribbon(vc,m,x1,y0,z1,x1,y1,z1,hw,r,g,b,al); ribbon(vc,m,x0,y0,z1,x0,y1,z1,hw,r,g,b,al);
    }

    /** Camera-facing thin quad between two camera-relative points. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                               float hw, int r, int g, int b, int al) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f; // vector camera -> midpoint
        // side = dir x view
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6f) return;
        float k = hw / len;
        sx *= k; sy *= k; sz *= k;
        quad(vc, m, ax + sx, ay + sy, az + sz, ax - sx, ay - sy, az - sz,
                    bx - sx, by - sy, bz - sz, bx + sx, by + sy, bz + sz, r, g, b, al);
    }
}
