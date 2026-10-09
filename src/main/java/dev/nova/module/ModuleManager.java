package dev.nova.module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        modules.add(new StorageFinder());
        modules.add(new SpawnerFinder());
    }

    public List<Module> all() { return modules; }

    public List<EspModule> esp() {
        List<EspModule> out = new ArrayList<>();
        for (Module m : modules) if (m instanceof EspModule e) out.add(e);
        return out;
    }
}
