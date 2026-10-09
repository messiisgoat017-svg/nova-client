package dev.nova.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nova.module.Module;
import dev.nova.module.ModuleManager;
import dev.nova.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nova.json"); }

    public static void save(ModuleManager mm) {
        Map<String, String> out = new TreeMap<>();
        for (Module m : mm.all()) {
            out.put(m.name() + ".enabled", String.valueOf(m.isEnabled()));
            for (Setting<?> s : m.settings()) out.put(m.name() + "." + s.name, s.save());
        }
        try (Writer w = Files.newBufferedWriter(file())) { GSON.toJson(out, w); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public static void load(ModuleManager mm) {
        if (!Files.exists(file())) return;
        try (Reader r = Files.newBufferedReader(file())) {
            Map<String, String> in = GSON.fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
            if (in == null) return;
            for (Module m : mm.all()) {
                for (Setting<?> s : m.settings()) {
                    String v = in.get(m.name() + "." + s.name);
                    if (v != null) s.load(v);
                }
                if ("true".equals(in.get(m.name() + ".enabled"))) m.setEnabled(true);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
