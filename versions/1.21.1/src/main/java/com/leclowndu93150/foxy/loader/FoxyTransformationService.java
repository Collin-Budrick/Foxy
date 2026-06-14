package com.leclowndu93150.foxy.loader;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;
import java.util.Set;

public class FoxyTransformationService implements ITransformationService {

    @Override
    public String name() {
        return "foxy_loader";
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        LoadingModList.get().getModLoadingIssues()
            .removeIf(issue -> {
                String key = issue.translationKey();
                return key != null && key.startsWith("fml.modloadingissue.brokenfile.")
                    && isVoxyPath(issue.affectedPath());
            });
        return List.of();
    }

    private static boolean isVoxyPath(java.nio.file.Path path) {
        if (path == null) return false;
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        return name.startsWith("voxy");
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of(new FoxyAccessWidener());
    }
}
