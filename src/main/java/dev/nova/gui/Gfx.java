package dev.nova.gui;

import net.minecraft.client.gui.DrawContext;

final class Gfx {
    static int alpha(int c, float a) {
        int al = (int) (((c >>> 24) & 255) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
             | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Anti-alias-free rounded rectangle built from row fills. */
    static void round(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, color); return; }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** Horizontal gradient (2px strips). */
    static void hgrad(DrawContext c, int x, int y, int w, int h, int a, int b) {
        for (int i = 0; i < w; i += 2) {
            c.fill(x + i, y, x + Math.min(i + 2, w), y + h, lerp(a, b, w <= 1 ? 0 : i / (float) (w - 1)));
        }
    }
}
