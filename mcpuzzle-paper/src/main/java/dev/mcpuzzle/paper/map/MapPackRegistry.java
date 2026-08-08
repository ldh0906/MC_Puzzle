package dev.mcpuzzle.paper.map;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically replaces the visible catalog only after every pack parses and validates. */
public final class MapPackRegistry {
    private final JsoncMapPackLoader loader;
    private final List<Path> sources;
    private final AtomicReference<Map<String, MapPack>> current = new AtomicReference<>(Map.of());

    public MapPackRegistry(JsoncMapPackLoader loader, List<Path> sources) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (this.sources.isEmpty()) throw new IllegalArgumentException("At least one map source is required");
    }

    public Map<String, MapPack> reload() throws MapPackLoadException {
        LinkedHashMap<String, MapPack> replacement = new LinkedHashMap<>();
        for (Path source : sources) {
            MapPack pack = loader.load(source);
            if (replacement.putIfAbsent(pack.mazeId(), pack) != null) {
                throw new MapPackLoadException("Duplicate mazeId in catalog: " + pack.mazeId());
            }
        }
        Map<String, MapPack> immutable = Map.copyOf(replacement);
        current.set(immutable);
        return immutable;
    }

    public Optional<MapPack> current(String mazeId) {
        return Optional.ofNullable(current.get().get(mazeId));
    }

    public Map<String, MapPack> currentAll() {
        return current.get();
    }

    public List<Path> sources() {
        return sources;
    }
}
