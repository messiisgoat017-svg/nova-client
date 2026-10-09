package dev.nova.setting;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, boolean def) { super(name, def); }
    public void toggle() { value = !value; }
    @Override public String save() { return String.valueOf(value); }
    @Override public void load(String s) { value = Boolean.parseBoolean(s); }
}
