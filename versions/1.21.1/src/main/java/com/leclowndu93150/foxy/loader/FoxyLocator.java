package com.leclowndu93150.foxy.loader;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class FoxyLocator implements IModFileCandidateLocator {

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        readdSelf(pipeline);

        try {
            Path voxyJar = findVoxyJar();
            if (voxyJar != null && !context.isLocated(voxyJar)) {
                pipeline.addPath(voxyJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY);
            }
        } catch (Exception e) {
            throw new RuntimeException("Foxy: failed to locate Voxy jar", e);
        }
    }

    private static void readdSelf(IDiscoveryPipeline pipeline) {
        List<Path> ownPaths = ownModPaths();
        if (ownPaths.isEmpty()) {
            return;
        }
        try {
            pipeline.addJarContent(JarContents.of(ownPaths), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        } catch (Exception ignored) {}
    }

    private static List<Path> ownModPaths() {
        String modFolders = System.getenv("MOD_CLASSES");
        if (modFolders == null) {
            modFolders = System.getProperty("fml.modFolders", "");
        }
        List<Path> paths = new ArrayList<>();
        for (String entry : modFolders.split(File.pathSeparator)) {
            int split = entry.indexOf("%%");
            String id = split != -1 ? entry.substring(0, split) : "";
            String path = split != -1 ? entry.substring(split + 2) : entry;
            if (id.equals("foxy") && !path.isEmpty()) {
                paths.add(new File(path).toPath());
            }
        }
        if (paths.isEmpty()) {
            try {
                URI self = FoxyLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                paths.add(Path.of(self));
            } catch (Exception ignored) {}
        }
        return paths;
    }

    private static Path findVoxyJar() throws URISyntaxException, IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> urls = cl.getResources("fabric.mod.json");
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String urlStr = url.toString();
            if (urlStr.contains("voxy")) {
                String jarPath = urlStr.substring("jar:".length(), urlStr.indexOf("!/"));
                return Path.of(new URI(jarPath));
            }
        }
        String cp = System.getProperty("java.class.path", "");
        for (String entry : cp.split(System.getProperty("path.separator"))) {
            if (entry.contains("voxy") && entry.endsWith(".jar")) {
                return Path.of(entry);
            }
        }
        return null;
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY + 1;
    }
}
