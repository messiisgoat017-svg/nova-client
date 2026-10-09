package dev.nova.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    public final String name;
    protected T value;
    private BooleanSupplier visible = () -> true;

    protected Setting(String name, T def) { this.name = name; this.value = def; }

    public T get() { return value; }
    public void set(T v) { this.value = v; }

    public Setting<T> visibleWhen(BooleanSupplier s) { this.visible = s; return this; }
    public boolean isVisible() { return visible.getAsBoolean(); }

    public abstract String save();
    public abstract void load(String s);
}
