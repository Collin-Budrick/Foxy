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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

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
        List<String> jars = readNestedJarPaths(contents);
        if (jars.isEmpty()) {
            return;
        }
        for (String file : jars) {
            if (shouldSkipNestedJar(file)) {
                continue;
            }
            try {
                Path extracted = extract(contents, file);
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

    private static boolean shouldSkipNestedJar(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        return name.startsWith("lz4-java-")
                || name.startsWith("xz-")
                || name.contains("-natives-linux");
    }

    private static List<String> readNestedJarPaths(JarContents contents) {
        List<String> result = new ArrayList<>();
        try (InputStream is = openFile(contents, "fabric.mod.json")) {
            if (is == null) return List.of();
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement jars = obj.get("jars");
            if (jars != null && jars.isJsonArray()) {
                JsonArray entries = jars.getAsJsonArray();
                for (JsonElement entry : entries) {
                    if (!entry.isJsonObject()) continue;
                    JsonElement file = entry.getAsJsonObject().get("file");
                    if (file != null && file.isJsonPrimitive()) {
                        result.add(file.getAsString());
                    }
                }
            }
        } catch (IOException | IllegalStateException e) {
            return List.of();
        }
        if (!result.isEmpty()) {
            return result;
        }
        Path jarPath = contents.getPrimaryPath();
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return List.of();
        }
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith("META-INF/jars/") && name.endsWith(".jar")) {
                    result.add(name);
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return result;
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
