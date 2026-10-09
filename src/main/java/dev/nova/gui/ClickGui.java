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
 * Category sidebar + module list (name, description, switch). Left-click a row to toggle the module,
 * right-click it (or click the arrow) to expand its settings.
 * Input is polled from GLFW inside render(), so this doesn't depend on the mouse/key event signatures.
 */
public class ClickGui extends Screen {
    private static final int SIDE = 112;
    private int W = 420, H = 270;

    // palette (dark warm + orange accent)
    private static final int BG = 0xFF0E0D0C, SIDEBG = 0xFF12100E;
    private static final int MROW = 0xFF1A1816, MROW_HOVER = 0xFF211E1B, MROW_ON = 0xFF1E1A15;
    private static final int ROW = 0xFF151311, ROW_HOVER = 0xFF1D1A17;
    private static final int ACCENT = 0xFFF0892B, ACCENT2 = 0xFFFFB454;
    private static final int TEXT = 0xFFECE8E2, DIM = 0xFF8C857B, OFF = 0xFF3A3631, TRACK = 0xFF2B2824;

    private enum Kind { CAT, SAVE, MODULE_TOGGLE, MODULE_EXPAND, BOOL, SLIDER, MODE, COLOR_TOGGLE, COLOR_SB, COLOR_HUE }
    private static final class Hit {
        int x, y, w, h, idx; Kind kind; Object ref;
        Hit(Kind k, int x, int y, int w, int h, Object ref) { kind = k; this.x = x; this.y = y; this.w = w; this.h = h; this.ref = ref; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Object, Float> anims = new HashMap<>();
    private final Set<ColorSetting> expanded = new HashSet<>();
    private final Set<Module> openModules = new HashSet<>();
    private Module.Category cat = Module.Category.RENDER;
    private Hit drag;
    private boolean prevLeft, prevRight;
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

    private String ellipsize(String s, int max) {
        if (textRenderer.getWidth(s) <= max) return s;
        String t = s;
        while (t.length() > 1 && textRenderer.getWidth(t + "...") > max) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        W = Math.min(420, width - 16);
        H = Math.min(270, height - 16);
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        hits.clear();

        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        Gfx.round(ctx, x0 - 1, y0 - 1, W + 2, H + 2, 9, 0xFF2A2622);
        Gfx.round(ctx, x0, y0, W, H, 8, BG);

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
        Gfx.round(ctx, x0, y0, SIDE, H, 8, SIDEBG);
        ctx.fill(x0 + SIDE - 9, y0, x0 + SIDE, y0 + H, SIDEBG);   // square off the right edge
        ctx.fill(x0 + SIDE, y0, x0 + SIDE + 1, y0 + H, 0xFF221F1B);

        // title
        ctx.fill(x0 + 11, y0 + 13, x0 + 13, y0 + 33, ACCENT);
        Text title = Text.literal("NOVA").formatted(Formatting.BOLD);
        ctx.drawText(textRenderer, title, x0 + 19, y0 + 13, TEXT, true);
        ctx.drawText(textRenderer, "b1.0", x0 + 19 + textRenderer.getWidth(title) + 4, y0 + 13, DIM, false);
        ctx.drawText(textRenderer, "client", x0 + 19, y0 + 24, 0xFF6F695F, false);

        int ry = y0 + 46;
        for (Module.Category c : Module.Category.values()) {
            int rx = x0 + 7, rw = SIDE - 14, rh = 22;
            boolean hover = Gfx.in(mx, my, rx, ry, rw, rh);
            float hv = anim(c.name() + "_h", hover ? 1f : 0f);
            float sel = anim(c.name() + "_s", cat == c ? 1f : 0f);
            if (sel > 0.01f) Gfx.round(ctx, rx, ry, rw, rh, 6, Gfx.lerp(SIDEBG, 0xFFB5681F, sel));
            Gfx.round(ctx, rx + 1, ry + 1, rw - 2, rh - 2, 5,
                      Gfx.lerp(Gfx.lerp(SIDEBG, 0xFF1A1714, hv), 0xFF1E1A16, sel));
            if (sel > 0.01f) Gfx.round(ctx, rx + 3, ry + 5, 2, rh - 10, 1, Gfx.alpha(ACCENT, sel));
            Gfx.round(ctx, rx + 10, ry + 8, 6, 6, 3, Gfx.lerp(0xFF4A453E, ACCENT, sel));
            ctx.drawText(textRenderer, c.label, rx + 23, ry + 7, Gfx.lerp(DIM, TEXT, Math.max(sel, hv * 0.7f)), false);
            hits.add(new Hit(Kind.CAT, rx, ry, rw, rh, c));
            ry += 25;
        }

        // save button
        int bx = x0 + 7, by = y0 + H - 27, bw = SIDE - 14, bh = 20;
        boolean bh2 = Gfx.in(mx, my, bx, by, bw, bh);
        float bt = anim("save_h", bh2 ? 1f : 0f);
        Gfx.round(ctx, bx, by, bw, bh, 5, Gfx.lerp(0xFF1E1B18, 0xFF2A251F, bt));
        String lbl = "SAVE CONFIG";
        ctx.drawText(textRenderer, lbl, bx + (bw - textRenderer.getWidth(lbl)) / 2, by + 6, Gfx.lerp(DIM, TEXT, bt), false);
        hits.add(new Hit(Kind.SAVE, bx, by, bw, bh, null));
    }

    private void drawContent(DrawContext ctx, int x0, int y0, int mx, int my) {
        int cx = x0 + SIDE + 12, cw = W - SIDE - 24;
        areaTop = y0 + 10;
        areaBot = y0 + H - 10;

        ctx.enableScissor(cx - 2, areaTop, cx + cw + 2, areaBot);
        int y = areaTop - scroll;
        boolean any = false;
        for (Module m : NovaClient.modules.all()) {
            if (m.category() != cat) continue;
            any = true;
            y = drawModule(ctx, m, cx, y, cw, mx, my) + 6;
        }
        if (!any) {
            String s = "No " + cat.label + " modules yet";
            ctx.drawText(textRenderer, s, cx + (cw - textRenderer.getWidth(s)) / 2, y0 + H / 2 - 4, 0xFF5F5A52, false);
        }
        contentH = y + scroll - areaTop;
        ctx.disableScissor();

        int max = Math.max(0, contentH - (areaBot - areaTop));
        scroll = Math.max(0, Math.min(max, scroll));
        if (max > 0) {
            int th = areaBot - areaTop;
            int bh = Math.max(20, th * th / contentH);
            int by = areaTop + (int) ((th - bh) * (scroll / (float) max));
            Gfx.round(ctx, cx + cw + 5, by, 3, bh, 1, Gfx.alpha(ACCENT, 0.7f));
        }
    }

    private int drawModule(DrawContext ctx, Module m, int cx, int y, int cw, int mx, int my) {
        int h = 34;
        boolean on = m.isEnabled();
        boolean hover = Gfx.in(mx, my, cx, y, cw, h) && my >= areaTop && my < areaBot;
        float t = anim(m.name() + "_on", on ? 1f : 0f);
        float hv = anim(m.name() + "_h", hover ? 1f : 0f);

        Gfx.round(ctx, cx, y, cw, h, 6, Gfx.lerp(0xFF25221E, 0xFFB5681F, t));
        Gfx.round(ctx, cx + 1, y + 1, cw - 2, h - 2, 5, Gfx.lerp(Gfx.lerp(MROW, MROW_HOVER, hv), MROW_ON, t));
        if (t > 0.01f) Gfx.round(ctx, cx + 3, y + 8, 2, h - 16, 1, Gfx.alpha(ACCENT, t));

        ctx.drawText(textRenderer, m.name(), cx + 13, y + 8, Gfx.lerp(0xFF9A938A, TEXT, t), false);
        ctx.drawText(textRenderer, ellipsize(m.description(), cw - 13 - 76), cx + 13, y + 20,
                     Gfx.lerp(0xFF5F5A52, 0xFF857E74, t), false);

        drawSwitch(ctx, m.name() + "_sw", on, cx + cw - 40, y + 10, 30, 14);

        boolean open = openModules.contains(m);
        ctx.drawText(textRenderer, open ? "v" : ">", cx + cw - 55, y + 13, DIM, false);
        hits.add(new Hit(Kind.MODULE_EXPAND, cx + cw - 62, y, 20, h, m));
        hits.add(new Hit(Kind.MODULE_TOGGLE, cx, y, cw, h, m));

        y += h;
        if (!open) return y;

        int ny = y + 5, nx = cx + 10, nw = cw - 10, startY = ny;
        for (Setting<?> s : m.settings()) {
            if (!s.isVisible()) continue;
            ny = drawSetting(ctx, s, nx, ny, nw, mx, my) + 4;
        }
        ctx.fill(cx + 4, startY, cx + 5, ny - 4, Gfx.alpha(ACCENT, 0.35f));
        return ny - 4;
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
            hits.add(new Hit(Kind.SLIDER, tx, y + 16, tw, 18, n));
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
                ctx.drawText(textRenderer, label, cx + 7, chy + 4, Gfx.lerp(DIM, 0xFF1A0F05, Math.max(t, 0f)) , false);
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
        long win = client.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (left && !prevLeft) press(mx, my, 0);
        else if (left && drag != null) update(drag, mx, my);
        else if (!left) drag = null;
        if (right && !prevRight) press(mx, my, 1);
        prevLeft = left;
        prevRight = right;
    }

    private void press(int mx, int my, int button) {
        for (Hit h : hits) {
            if (!Gfx.in(mx, my, h.x, h.y, h.w, h.h)) continue;
            boolean unclipped = h.kind == Kind.CAT || h.kind == Kind.SAVE;
            if (!unclipped && (my < areaTop || my >= areaBot)) continue;

            if (button == 1) {                       // right click: expand / collapse module settings
                if (h.kind == Kind.MODULE_TOGGLE || h.kind == Kind.MODULE_EXPAND) { toggleOpen((Module) h.ref); return; }
                continue;
            }
            switch (h.kind) {
                case CAT -> { cat = (Module.Category) h.ref; scroll = 0; }
                case SAVE -> Config.save(NovaClient.modules);
                case MODULE_EXPAND -> toggleOpen((Module) h.ref);
                case MODULE_TOGGLE -> ((Module) h.ref).toggle();
                case BOOL -> ((BoolSetting) h.ref).toggle();
                case MODE -> ((ModeSetting) h.ref).setIndex(h.idx);
                case COLOR_TOGGLE -> { ColorSetting c = (ColorSetting) h.ref; if (!expanded.remove(c)) expanded.add(c); }
                case SLIDER, COLOR_SB, COLOR_HUE -> { drag = h; update(h, mx, my); }
            }
            return;
        }
    }

    private void toggleOpen(Module m) { if (!openModules.remove(m)) openModules.add(m); }

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
