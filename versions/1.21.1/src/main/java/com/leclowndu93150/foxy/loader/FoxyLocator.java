package com.leclowndu93150.foxy.loader;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;

public class FoxyLocator implements IModFileCandidateLocator {

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        try {
            Path voxyJar = findVoxyJar();
            if (voxyJar != null && !context.isLocated(voxyJar)) {
                pipeline.addPath(voxyJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY);
            }
        } catch (Exception e) {
            throw new RuntimeException("Foxy: failed to locate Voxy jar", e);
        }
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
