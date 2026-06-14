package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class FoxyDependencyLocator implements IDependencyLocator {

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        for (IModFile mod : loadedMods) {
            JarContents contents = JarContents.of(mod.getFilePath());
            if (!containsFile(contents, "fabric.mod.json")) {
                continue;
            }
            extractNestedJars(contents, pipeline);
        }
    }

    private static void extractNestedJars(JarContents contents, IDiscoveryPipeline pipeline) {
        JsonArray jars = readJarsArray(contents);
        if (jars == null) {
            return;
        }
        for (JsonElement entry : jars) {
            if (!entry.isJsonObject()) continue;
            JsonElement file = entry.getAsJsonObject().get("file");
            if (file == null || !file.isJsonPrimitive()) continue;
            try {
                Path extracted = extract(contents, file.getAsString());
                if (extracted != null) {
                    IModFile lib = IModFile.create(
                            SecureJar.from(JarContents.of(extracted)),
                            JarModsDotTomlModFileReader::manifestParser,
                            IModFile.Type.GAMELIBRARY,
                            ModFileDiscoveryAttributes.DEFAULT);
                    pipeline.addModFile(lib);
                }
            } catch (Exception ignored) {}
        }
    }

    private static JsonArray readJarsArray(JarContents contents) {
        try (InputStream is = openFile(contents, "fabric.mod.json")) {
            if (is == null) return null;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement jars = obj.get("jars");
            return jars != null && jars.isJsonArray() ? jars.getAsJsonArray() : null;
        } catch (IOException | IllegalStateException e) {
            return null;
        }
    }

    private static Path extract(JarContents contents, String innerPath) throws IOException {
        if (!containsFile(contents, innerPath)) {
            return null;
        }
        String name = innerPath.substring(innerPath.lastIndexOf('/') + 1);
        Path out = Files.createTempFile("foxy-jij-", "-" + name);
        out.toFile().deleteOnExit();
        try (InputStream is = openFile(contents, innerPath)) {
            Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
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
        return LOWEST_SYSTEM_PRIORITY;
    }
}
