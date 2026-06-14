package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AccessWidenerData {
    public final Set<String> classes = new HashSet<>();
    public final Map<String, Set<String>> fields = new HashMap<>();
    public final Map<String, Set<String>> methods = new HashMap<>();

    private static AccessWidenerData instance;

    public static synchronized AccessWidenerData get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AccessWidenerData load() {
        AccessWidenerData data = new AccessWidenerData();
        for (Path jar : candidateJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                if (zip.getEntry("fabric.mod.json") == null) continue;
                String awName = readAwName(zip);
                if (awName == null) continue;
                ZipEntry awEntry = zip.getEntry(awName);
                if (awEntry == null) continue;
                try (InputStream is = zip.getInputStream(awEntry)) {
                    data.parse(is);
                }
            } catch (IOException ignored) {}
        }
        try (InputStream extra = Thread.currentThread().getContextClassLoader().getResourceAsStream("foxy-extra.accesswidener")) {
            if (extra != null) {
                data.parse(extra);
            }
        } catch (IOException ignored) {}
        return data;
    }

    private static List<Path> candidateJars() {
        List<Path> jars = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(System.getProperty("path.separator"))) {
            if (entry.endsWith(".jar")) {
                jars.add(Path.of(entry));
            }
        }
        try {
            Path mods = FMLPaths.MODSDIR.get();
            if (mods != null && Files.isDirectory(mods)) {
                try (Stream<Path> walk = Files.list(mods)) {
                    walk.filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(jars::add);
                }
            }
        } catch (Throwable ignored) {}
        return jars;
    }

    private static String readAwName(ZipFile zip) throws IOException {
        ZipEntry fmj = zip.getEntry("fabric.mod.json");
        try (InputStream is = zip.getInputStream(fmj)) {
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement aw = obj.get("accessWidener");
            return aw != null && aw.isJsonPrimitive() ? aw.getAsString() : null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private void parse(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.strip();
                if (line.isEmpty()) continue;
                if (header) {
                    header = false;
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;
                String access = parts[0];
                if (!access.equals("accessible") && !access.equals("extendable") && !access.equals("mutable")) {
                    continue;
                }
                String type = parts[1];
                switch (type) {
                    case "class" -> classes.add(parts[2].replace('/', '.'));
                    case "field" -> {
                        if (parts.length >= 5) {
                            fields.computeIfAbsent(parts[2].replace('/', '.'), k -> new HashSet<>()).add(parts[3]);
                        }
                    }
                    case "method" -> {
                        if (parts.length >= 5) {
                            methods.computeIfAbsent(parts[2].replace('/', '.'), k -> new HashSet<>()).add(parts[3] + parts[4]);
                        }
                    }
                    default -> {}
                }
            }
        }
    }

    public Set<String> allTargets() {
        Set<String> all = new HashSet<>(classes);
        all.addAll(fields.keySet());
        all.addAll(methods.keySet());
        return all;
    }
}
