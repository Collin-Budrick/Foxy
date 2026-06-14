package com.leclowndu93150.foxy.loader;

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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class FoxyFabricLoaderImpl implements FabricLoader {
    public static final FoxyFabricLoaderImpl INSTANCE = new FoxyFabricLoaderImpl();

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.dist.isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public Optional<ModContainer> getModContainer(String modId) {
        return ModList.get().getModContainerById(modId).map(NeoModContainer::new);
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
}
