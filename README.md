# Foxy

Loads [Voxy](https://modrinth.com/mod/voxy) on NeoForge.

Voxy is a Fabric LoD rendering mod. Foxy makes it run on NeoForge without modifying
Voxy itself: it reads the unmodified Voxy jar at runtime and bridges the Fabric APIs
it uses over to NeoForge equivalents.

## How it works

- Reads `fabric.mod.json` from the Voxy jar and generates a synthetic `neoforge.mods.toml`
  (name, description, authors, icon, mixin configs, version) so FML loads it.
- Extracts Voxy's bundled `META-INF/jars` (RocksDB, LWJGL zstd/lmdb, lz4, xz, jedis) as
  game libraries.
- Applies Voxy's `voxy.accesswidener` live as a class processor, plus a small supplement
  for NeoForge-only access gaps.
- Ships minimal `net.fabricmc.*` stubs so Voxy's bytecode links; `FabricLoader` delegates
  to `ModList` / `FMLEnvironment`.
- Reimplements the `/voxy` command against NeoForge's command system.
- Adds a Chunky auto-ingest mixin targeting NeoForge's `NeoForgeWorld`.

Nothing about Voxy is hardcoded. Metadata, mixins, access wideners, and bundled jars are
all read from whatever Voxy jar is present, so Voxy updates do not require Foxy changes.

## Requirements

- Minecraft 26.1.2
- NeoForge
- Sodium
- Voxy 0.2.16-beta (placed in the mods folder)

## Building

```
./gradlew :26.1.2:build
```

## License

MIT. This does not relicense Voxy; Voxy remains All Rights Reserved and is not redistributed.
