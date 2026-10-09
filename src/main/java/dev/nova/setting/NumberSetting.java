package dev.nova.setting;

public class NumberSetting extends Setting<Double> {
    public final double min, max, step;
    public final String suffix;

    public NumberSetting(String name, double def, double min, double max, double step, String suffix) {
        super(name, def);
        this.min = min; this.max = max; this.step = step; this.suffix = suffix;
    }

    @Override public void set(Double v) {
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = Math.round((c - min) / step) * step + min;
        value = Math.max(min, Math.min(max, c));
    }

    public void setFraction(double t) { set(min + Math.max(0, Math.min(1, t)) * (max - min)); }
    public double fraction() { return (value - min) / (max - min); }
    public float f() { return value.floatValue(); }
    public int i() { return (int) Math.round(value); }

    @Override public String save() { return String.valueOf(value); }
    @Override public void load(String s) { try { set(Double.parseDouble(s)); } catch (Exception ignored) {} }
}
