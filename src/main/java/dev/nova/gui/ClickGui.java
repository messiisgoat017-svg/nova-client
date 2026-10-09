package dev.nova.gui;

import dev.nova.NovaClient;
import dev.nova.config.Config;
import dev.nova.module.Module;
import dev.nova.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.*;

/**
 * Sidebar (modules) + settings pane. Input is polled from GLFW inside render(), so the screen does not
 * depend on the mouse/key event signatures that changed in 1.21.9+.
 */
public class ClickGui extends Screen {
    private static final int W = 540, H = 340, SIDE = 134;

    private static final int BG = 0xF2101114, CARD = 0xFF17181D, ROW = 0xFF1D1F26, ROW_HOVER = 0xFF23252E;
    private static final int ACCENT = 0xFF8B6CFF, ACCENT2 = 0xFF4FC3F7;
    private static final int TEXT = 0xFFE8E8EE, DIM = 0xFF8A8D99, OFF = 0xFF353742, TRACK = 0xFF2A2C35;

    private enum Kind { MODULE_SELECT, MODULE_TOGGLE, BOOL, SLIDER, MODE, COLOR_TOGGLE, COLOR_SB, COLOR_HUE }
    private static final class Hit {
        int x, y, w, h, idx; Kind kind; Object ref;
        Hit(Kind k, int x, int y, int w, int h, Object ref) { kind = k; this.x = x; this.y = y; this.w = w; this.h = h; this.ref = ref; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Object, Float> anims = new HashMap<>();
    private final Set<ColorSetting> expanded = new HashSet<>();
    private Module selected = NovaClient.modules.all().get(0);
    private Hit drag;
    private boolean prevLeft;
    private float dt;
    private long lastNs = System.nanoTime();
    private int scroll, contentH, areaTop, areaBot;

    public ClickGui() { super(Text.literal("Nova")); }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.save(NovaClient.modules); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, contentH - (areaBot - areaTop));
        scroll = Math.max(0, Math.min(max, scroll - (int) (v * 20)));
        return true;
    }

    private float anim(Object key, float target) {
        float c = anims.getOrDefault(key, target);
        c += (target - c) * Math.min(1f, dt * 14f);
        anims.put(key, c);
        return c;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        hits.clear();

        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        Gfx.round(ctx, x0 - 3, y0 - 3, W + 6, H + 6, 13, 0x30000000);
        Gfx.round(ctx, x0, y0, W, H, 10, BG);
        Gfx.hgrad(ctx, x0 + 14, y0, W - 28, 2, ACCENT, ACCENT2);

        drawSidebar(ctx, x0, y0, mx, my);
        drawContent(ctx, x0, y0, mx, my);
        poll(mx, my);
    }

    private void drawSwitch(DrawContext ctx, Object key, boolean on, int x, int y, int w, int h) {
        float t = anim(key, on ? 1f : 0f);
        Gfx.round(ctx, x, y, w, h, h / 2, Gfx.lerp(OFF, ACCENT, t));
        int k = h - 4;
        int kx = x + 2 + (int) ((w - k - 4) * t);
        Gfx.round(ctx, kx, y + 2, k, k, k / 2, 0xFFFFFFFF);
    }

    private void drawSidebar(DrawContext ctx, int x0, int y0, int mx, int my) {
        int sx = x0 + 8, sy = y0 + 10, sw = SIDE, sh = H - 20;
        Gfx.round(ctx, sx, sy, sw, sh, 8, CARD);
        ctx.drawText(textRenderer, Text.literal("NOVA").formatted(Formatting.BOLD), sx + 12, sy + 12, ACCENT, true);
        ctx.drawText(textRenderer, "client", sx + 46, sy + 12, DIM, false);
        ctx.drawText(textRenderer, "RENDER", sx + 12, sy + 34, DIM, false);

        int ry = sy + 48;
        for (Module m : NovaClient.modules.all()) {
            int rx = sx + 6, rw = sw - 12, rh = 24;
            boolean hover = Gfx.in(mx, my, rx, ry, rw, rh);
            float hv = anim(m.name() + "_h", hover ? 1f : 0f);
            float sel = anim(m.name() + "_s", selected == m ? 1f : 0f);
            Gfx.round(ctx, rx, ry, rw, rh, 6, Gfx.lerp(CARD, ROW_HOVER, hv));
            if (sel > 0.01f) {
                Gfx.round(ctx, rx, ry, rw, rh, 6, Gfx.alpha(ACCENT, 0.20f * sel));
                Gfx.round(ctx, rx, ry + 5, 2, rh - 10, 1, Gfx.alpha(ACCENT, sel));
            }
            int tc = Gfx.lerp(DIM, TEXT, Math.max(sel, m.isEnabled() ? 0.8f : hv * 0.6f));
            ctx.drawText(textRenderer, m.name(), rx + 10, ry + 8, tc, false);
            int swx = rx + rw - 26, swy = ry + 7;
            drawSwitch(ctx, m.name() + "_sw", m.isEnabled(), swx, swy, 20, 10);

            hits.add(new Hit(Kind.MODULE_TOGGLE, swx - 2, swy - 3, 24, 16, m));
            hits.add(new Hit(Kind.MODULE_SELECT, rx, ry, rw, rh, m));
            ry += 28;
        }
        ctx.drawText(textRenderer, "LAlt  toggle GUI", sx + 12, sy + sh - 16, 0xFF565967, false);
    }

    private void drawContent(DrawContext ctx, int x0, int y0, int mx, int my) {
        Module m = selected;
        int cx = x0 + SIDE + 24, cw = W - SIDE - 34;

        ctx.drawText(textRenderer, Text.literal(m.name()).formatted(Formatting.BOLD), cx, y0 + 18, TEXT, true);
        ctx.drawText(textRenderer, m.description(), cx, y0 + 32, DIM, false);
        int hx = cx + cw - 34, hy = y0 + 20;
        drawSwitch(ctx, m.name() + "_hsw", m.isEnabled(), hx, hy, 30, 16);
        hits.add(new Hit(Kind.MODULE_TOGGLE, hx - 2, hy - 2, 34, 20, m));
        ctx.fill(cx, y0 + 50, cx + cw, y0 + 51, 0xFF23252E);

        areaTop = y0 + 58;
        areaBot = y0 + H - 12;
        ctx.enableScissor(cx - 4, areaTop, cx + cw + 4, areaBot);
        int y = areaTop - scroll;
        for (Setting<?> s : m.settings()) {
            if (!s.isVisible()) continue;
            y = drawSetting(ctx, s, cx, y, cw, mx, my) + 6;
        }
        contentH = y + scroll - areaTop;
        ctx.disableScissor();

        int max = Math.max(0, contentH - (areaBot - areaTop));
        scroll = Math.max(0, Math.min(max, scroll));
        if (max > 0) {
            int th = areaBot - areaTop;
            int bh = Math.max(20, th * th / contentH);
            int by = areaTop + (int) ((th - bh) * (scroll / (float) max));
            Gfx.round(ctx, cx + cw + 6, by, 3, bh, 1, Gfx.alpha(ACCENT, 0.7f));
        }
    }

    private int drawSetting(DrawContext ctx, Setting<?> s, int x, int y, int w, int mx, int my) {
        if (s instanceof BoolSetting b) {
            int h = 24;
            boolean hv = Gfx.in(mx, my, x, y, w, h) && my >= areaTop && my < areaBot;
            Gfx.round(ctx, x, y, w, h, 6, Gfx.lerp(ROW, ROW_HOVER, anim(b, hv ? 1f : 0f)));
            ctx.drawText(textRenderer, b.name, x + 10, y + 8, TEXT, false);
            drawSwitch(ctx, b, b.get(), x + w - 38, y + 5, 28, 14);
            hits.add(new Hit(Kind.BOOL, x, y, w, h, b));
            return y + h;
        }
        if (s instanceof NumberSetting n) {
            int h = 36;
            Gfx.round(ctx, x, y, w, h, 6, ROW);
            ctx.drawText(textRenderer, n.name, x + 10, y + 7, TEXT, false);
            String v = fmt(n);
            ctx.drawText(textRenderer, v, x + w - 10 - textRenderer.getWidth(v), y + 7, DIM, false);
            int tx = x + 10, ty = y + 23, tw = w - 20;
            Gfx.round(ctx, tx, ty, tw, 4, 2, TRACK);
            float f = anim(n, (float) n.fraction());
            int fw = Math.max(4, (int) (tw * f));
            Gfx.hgrad(ctx, tx, ty, fw, 4, ACCENT, Gfx.lerp(ACCENT, ACCENT2, f));
            Gfx.round(ctx, tx + fw - 4, ty - 2, 8, 8, 4, 0xFFFFFFFF);
            Hit hit = new Hit(Kind.SLIDER, tx, y + 16, tw, 18, n);
            hits.add(hit);
            return y + h;
        }
        if (s instanceof ModeSetting ms) {
            int h = 42;
            Gfx.round(ctx, x, y, w, h, 6, ROW);
            ctx.drawText(textRenderer, ms.name, x + 10, y + 7, TEXT, false);
            int cx = x + 10;
            for (int i = 0; i < ms.modes.length; i++) {
                String label = ms.modes[i];
                int cw = textRenderer.getWidth(label) + 14, chy = y + 21;
                boolean on = ms.index() == i;
                boolean hv = Gfx.in(mx, my, cx, chy, cw, 15) && my >= areaTop && my < areaBot;
                float t = anim(ms.name + label, on ? 1f : 0f);
                Gfx.round(ctx, cx, chy, cw, 15, 7, Gfx.lerp(Gfx.lerp(TRACK, ROW_HOVER, hv ? 1f : 0f), ACCENT, t));
                ctx.drawText(textRenderer, label, cx + 7, chy + 4, Gfx.lerp(DIM, 0xFFFFFFFF, Math.max(t, hv ? 0.6f : 0f)), false);
                Hit hit = new Hit(Kind.MODE, cx, chy, cw, 15, ms);
                hit.idx = i;
                hits.add(hit);
                cx += cw + 4;
            }
            return y + h;
        }
        if (s instanceof ColorSetting c) {
            boolean open = expanded.contains(c);
            float t = anim(c.name + "_open", open ? 1f : 0f);
            int full = 24 + 80;
            int h = 24 + (int) (80 * t);
            Gfx.round(ctx, x, y, w, h, 6, ROW);
            ctx.drawText(textRenderer, c.name, x + 10, y + 8, TEXT, false);
            Gfx.round(ctx, x + w - 36, y + 5, 26, 14, 5, 0xFFFFFFFF);
            Gfx.round(ctx, x + w - 35, y + 6, 24, 12, 4, 0xFF000000 | c.get());
            hits.add(new Hit(Kind.COLOR_TOGGLE, x, y, w, 24, c));
            if (t > 0.97f) {
                int px = x + 10, py = y + 30, pw = Math.min(200, w - 20), ph = 44;
                for (int i = 0; i < pw; i += 2) {
                    float sat = i / (float) (pw - 1);
                    int top = Color.HSBtoRGB(c.h, sat, 1f), bot = Color.HSBtoRGB(c.h, sat, 0f);
                    ctx.fillGradient(px + i, py, px + Math.min(i + 2, pw), py + ph, top, bot);
                }
                int mxp = px + (int) (c.s * (pw - 1)), myp = py + (int) ((1f - c.b) * (ph - 1));
                ctx.fill(mxp - 3, myp - 3, mxp + 3, myp + 3, 0xFFFFFFFF);
                ctx.fill(mxp - 2, myp - 2, mxp + 2, myp + 2, 0xFF000000 | c.get());
                hits.add(new Hit(Kind.COLOR_SB, px, py, pw, ph, c));

                int hy = py + ph + 6;
                for (int i = 0; i < pw; i += 2) {
                    ctx.fill(px + i, hy, px + Math.min(i + 2, pw), hy + 8, Color.HSBtoRGB(i / (float) (pw - 1), 1f, 1f));
                }
                int hxp = px + (int) (c.h * (pw - 1));
                ctx.fill(hxp - 1, hy - 2, hxp + 2, hy + 10, 0xFFFFFFFF);
                hits.add(new Hit(Kind.COLOR_HUE, px, hy - 2, pw, 12, c));
            }
            return y + h;
        }
        return y;
    }

    private static String fmt(NumberSetting n) {
        String v = n.step >= 1 ? String.valueOf(n.i()) : String.format(Locale.ROOT, "%.2f", n.get());
        return v + n.suffix;
    }

    // ------------------------------------------------------------------ input
    private void poll(int mx, int my) {
        boolean left = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (left && !prevLeft) press(mx, my);
        else if (left && drag != null) update(drag, mx, my);
        else if (!left) drag = null;
        prevLeft = left;
    }

    private void press(int mx, int my) {
        for (Hit h : hits) {
            if (!Gfx.in(mx, my, h.x, h.y, h.w, h.h)) continue;
            boolean settingHit = h.kind != Kind.MODULE_SELECT && h.kind != Kind.MODULE_TOGGLE;
            if (settingHit && (my < areaTop || my >= areaBot)) continue;
            switch (h.kind) {
                case MODULE_SELECT -> { selected = (Module) h.ref; scroll = 0; }
                case MODULE_TOGGLE -> ((Module) h.ref).toggle();
                case BOOL -> ((BoolSetting) h.ref).toggle();
                case MODE -> ((ModeSetting) h.ref).setIndex(h.idx);
                case COLOR_TOGGLE -> { ColorSetting c = (ColorSetting) h.ref; if (!expanded.remove(c)) expanded.add(c); }
                case SLIDER, COLOR_SB, COLOR_HUE -> { drag = h; update(h, mx, my); }
            }
            return;
        }
    }

    private void update(Hit h, int mx, int my) {
        switch (h.kind) {
            case SLIDER -> ((NumberSetting) h.ref).setFraction((mx - h.x) / (double) h.w);
            case COLOR_SB -> {
                ColorSetting c = (ColorSetting) h.ref;
                c.setHsb(c.h, (mx - h.x) / (float) h.w, 1f - (my - h.y) / (float) h.h);
            }
            case COLOR_HUE -> {
                ColorSetting c = (ColorSetting) h.ref;
                c.setHsb((mx - h.x) / (float) h.w, c.s == 0 ? 0.8f : c.s, c.b == 0 ? 1f : c.b);
            }
            default -> {}
        }
    }
}
