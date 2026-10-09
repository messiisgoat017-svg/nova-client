package dev.nova.setting;

import java.awt.Color;

/** Stores colour as HSB so the GUI picker is lossless. get() returns 0xRRGGBB. */
public class ColorSetting extends Setting<Integer> {
    public float h, s, b;

    public ColorSetting(String name, int rgb) { super(name, rgb & 0xFFFFFF); setRgb(rgb); }

    public void setRgb(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        h = hsb[0]; s = hsb[1]; b = hsb[2];
        value = rgb & 0xFFFFFF;
    }

    public void setHsb(float h, float s, float b) {
        this.h = clamp(h); this.s = clamp(s); this.b = clamp(b);
        value = Color.HSBtoRGB(this.h, this.s, this.b) & 0xFFFFFF;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override public String save() { return h + "," + s + "," + b; }
    @Override public void load(String str) {
        try { String[] p = str.split(","); setHsb(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])); }
        catch (Exception ignored) {}
    }
}
