package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FoxyFabricModReader implements IModFileReader {
    private static final String MODS_TOML = "META-INF/neoforge.mods.toml";

    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (!containsFile(jar, "fabric.mod.json") || containsFile(jar, MODS_TOML)) {
            return null;
        }

        JsonObject fmj = readFabricModJson(jar);
        if (fmj == null) {
            return null;
        }

        try {
            Path patched = patchJar(jar.getPrimaryPath(), fmj);
            return JarModsDotTomlModFileReader.createModFile(JarContents.of(patched), attributes);
        } catch (IOException e) {
            throw new RuntimeException("Foxy: failed to patch Voxy jar", e);
        }
    }

    private static Path patchJar(Path original, JsonObject fmj) throws IOException {
        String modId = string(fmj, "id", "voxy");
        Path patched = Files.createTempFile("foxy-patched-" + modId + "-", ".jar");
        patched.toFile().deleteOnExit();

        try (ZipFile in = new ZipFile(original.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(patched))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(MODS_TOML)) continue;
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream is = in.getInputStream(entry)) {
                        is.transferTo(out);
                    }
                }
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry(MODS_TOML));
            out.write(buildModsToml(fmj).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return patched;
    }

    private static String buildModsToml(JsonObject fmj) {
        String modId = string(fmj, "id", "voxy");
        String version = string(fmj, "version", "0.0.0");
        String name = string(fmj, "name", modId);
        String description = string(fmj, "description", "");
        String icon = string(fmj, "icon", null);
        String authors = joinAuthors(fmj);
        String commit = readCommit(fmj);

        StringBuilder sb = new StringBuilder();
        sb.append("modLoader = \"javafml\"\n");
        sb.append("loaderVersion = \"[4,)\"\n");
        sb.append("license = \"").append(esc(string(fmj, "license", "All-Rights-Reserved"))).append("\"\n");
        if (!authors.isEmpty()) {
            sb.append("authors = \"").append(esc(authors)).append("\"\n");
        }
        sb.append("[[mods]]\n");
        sb.append("modId = \"").append(esc(modId)).append("\"\n");
        sb.append("version = \"").append(esc(version)).append("\"\n");
        sb.append("displayName = \"").append(esc(name)).append("\"\n");
        if (!description.isEmpty()) {
            sb.append("description = \"").append(esc(description)).append("\"\n");
        }
        if (icon != null) {
            sb.append("logoFile = \"").append(esc(icon)).append("\"\n");
        }
        sb.append("[modproperties.").append(modId).append("]\n");
        sb.append("commit = \"").append(esc(commit)).append("\"\n");
        sb.append("[[dependencies.").append(modId).append("]]\n");
        sb.append("modId = \"neoforge\"\ntype = \"required\"\nversionRange = \"[4,)\"\nordering = \"NONE\"\nside = \"BOTH\"\n");
        sb.append("[[dependencies.").append(modId).append("]]\n");
        sb.append("modId = \"sodium\"\ntype = \"required\"\nversionRange = \"[0.6.13,0.7)\"\nordering = \"NONE\"\nside = \"CLIENT\"\n");
        for (String mixin : readMixins(fmj)) {
            sb.append("[[mixins]]\nconfig = \"").append(esc(mixin)).append("\"\n");
        }
        return sb.toString();
    }

    private static List<String> readMixins(JsonObject fmj) {
        List<String> result = new ArrayList<>();
        JsonElement mixins = fmj.get("mixins");
        if (mixins != null && mixins.isJsonArray()) {
            for (JsonElement el : mixins.getAsJsonArray()) {
                if (el.isJsonPrimitive()) {
                    result.add(el.getAsString());
                } else if (el.isJsonObject() && el.getAsJsonObject().has("config")) {
                    result.add(el.getAsJsonObject().get("config").getAsString());
                }
            }
        }
        return result;
    }

    private static String readCommit(JsonObject fmj) {
        JsonElement custom = fmj.get("custom");
        if (custom != null && custom.isJsonObject()) {
            JsonElement commit = custom.getAsJsonObject().get("commit");
            if (commit != null && commit.isJsonPrimitive()) {
                String c = commit.getAsString();
                if (!c.startsWith("$")) return c;
            }
        }
        return "foxycompat0000000000000000000000000000000";
    }

    private static String joinAuthors(JsonObject fmj) {
        JsonElement authors = fmj.get("authors");
        if (authors == null || !authors.isJsonArray()) return "";
        StringJoiner sj = new StringJoiner(", ");
        for (JsonElement el : authors.getAsJsonArray()) {
            if (el.isJsonPrimitive()) {
                sj.add(el.getAsString());
            } else if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                sj.add(el.getAsJsonObject().get("name").getAsString());
            }
        }
        return sj.toString();
    }

    @Nullable
    private static JsonObject readFabricModJson(JarContents jar) {
        try (InputStream is = openFile(jar, "fabric.mod.json")) {
            if (is == null) return null;
            return JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static String string(JsonObject obj, String key, String fallback) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return fallback;
        String s = el.getAsString();
        return s.startsWith("$") ? fallback : s;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean containsFile(JarContents contents, String path) {
        return contents.findFile(path).isPresent();
    }

    private static InputStream openFile(JarContents contents, String path) throws IOException {
        var uri = contents.findFile(path);
        return uri.isPresent() ? uri.get().toURL().openStream() : null;
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }
}
