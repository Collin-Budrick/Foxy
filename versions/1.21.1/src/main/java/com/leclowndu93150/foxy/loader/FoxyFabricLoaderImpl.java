package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;

public class FoxyFabricLoaderImpl implements FabricLoader {
    public static final FoxyFabricLoaderImpl INSTANCE = new FoxyFabricLoaderImpl();

    @Override
    public boolean isModLoaded(String modId) {
        ModList modList = currentModList();
        if (modList != null) {
            return modList.isLoaded(modId);
        }
        return findEarlyModData(modId).isPresent() || hasLikelyModJar(modId);
    }

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.dist.isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public Optional<ModContainer> getModContainer(String modId) {
        ModList modList = currentModList();
        if (modList != null) {
            return modList.getModContainerById(modId).map(NeoModContainer::new);
        }
        return findEarlyModData(modId).map(EarlyModContainer::new);
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return List.of();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    private record NeoModContainer(net.neoforged.fml.ModContainer delegate) implements ModContainer {
        @Override
        public ModMetadata getMetadata() {
            return new NeoModMetadata(delegate.getModInfo());
        }

        @Override
        public List<Path> getRootPaths() {
            Path file = delegate.getModInfo().getOwningFile().getFile().getFilePath();
            if (Files.isRegularFile(file)) {
                try {
                    FileSystem fs = FileSystems.newFileSystem(file);
                    return List.of(fs.getRootDirectories().iterator().next());
                } catch (IOException ignored) {}
            }
            return List.of(file);
        }
    }

    private record EarlyModData(String version, String commit, Path jarPath) {}

    private record EarlyModContainer(EarlyModData data) implements ModContainer {
        @Override
        public ModMetadata getMetadata() {
            return new EarlyModMetadata(data);
        }

        @Override
        public List<Path> getRootPaths() {
            if (Files.isRegularFile(data.jarPath())) {
                try {
                    return List.of(jarRoot(data.jarPath()));
                } catch (IOException ignored) {}
            }
            return List.of(data.jarPath());
        }
    }

    private record EarlyModMetadata(EarlyModData data) implements ModMetadata {
        @Override
        public Version getVersion() {
            String v = data.version();
            return () -> v;
        }

        @Override
        public CustomValue getCustomValue(String key) {
            if ("commit".equals(key)) {
                String commit = data.commit();
                return () -> commit;
            }
            return null;
        }
    }

    private record NeoModMetadata(IModInfo info) implements ModMetadata {
        @Override
        public Version getVersion() {
            String v = info.getVersion().toString();
            return () -> v;
        }

        @Override
        public CustomValue getCustomValue(String key) {
            Object val = info.getModProperties().get(key);
            if (val == null) return null;
            String s = val.toString();
            return () -> s;
        }
    }

    private static ModList currentModList() {
        try {
            return ModList.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Optional<EarlyModData> findEarlyModData(String modId) {
        Path modsDir = FMLPaths.MODSDIR.get();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return Optional.empty();
        }
        try (var files = Files.list(modsDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(path -> readFabricModData(path, modId))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<EarlyModData> readFabricModData(Path jarPath, String modId) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return Optional.empty();
            }
            try (var reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!modId.equals(string(json, "id", ""))) {
                    return Optional.empty();
                }
                String commit = readCommit(json);
                return Optional.of(new EarlyModData(string(json, "version", "0.0.0"), commit, jarPath));
            }
        } catch (IOException | IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasLikelyModJar(String modId) {
        Path modsDir = FMLPaths.MODSDIR.get();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return false;
        }
        String needle = modId.replace('_', '-');
        try (var files = Files.list(modsDir)) {
            return files
                    .map(path -> path.getFileName().toString().toLowerCase())
                    .anyMatch(name -> name.endsWith(".jar") && name.contains(needle));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path jarRoot(Path jarPath) throws IOException {
        var uri = java.net.URI.create("jar:" + jarPath.toUri());
        FileSystem fs;
        try {
            fs = FileSystems.newFileSystem(uri, Map.of());
        } catch (FileSystemAlreadyExistsException e) {
            fs = FileSystems.getFileSystem(uri);
        }
        return fs.getRootDirectories().iterator().next();
    }

    private static String readCommit(JsonObject json) {
        JsonElement custom = json.get("custom");
        if (custom != null && custom.isJsonObject()) {
            JsonElement commit = custom.getAsJsonObject().get("commit");
            if (commit != null && commit.isJsonPrimitive()) {
                String value = commit.getAsString();
                if (!value.startsWith("$")) {
                    return value;
                }
            }
        }
        return "foxycompat0000000000000000000000000000000";
    }

    private static String string(JsonObject obj, String key, String fallback) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
}
