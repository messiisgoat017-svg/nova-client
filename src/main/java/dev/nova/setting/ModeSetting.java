package dev.nova.setting;

public class ModeSetting extends Setting<String> {
    public final String[] modes;

    public ModeSetting(String name, String def, String... modes) { super(name, def); this.modes = modes; }

    public boolean is(String m) { return value.equals(m); }
    public int index() { for (int i = 0; i < modes.length; i++) if (modes[i].equals(value)) return i; return 0; }
    public void setIndex(int i) { value = modes[Math.floorMod(i, modes.length)]; }

    @Override public String save() { return value; }
    @Override public void load(String s) { for (String m : modes) if (m.equals(s)) value = m; }
}
