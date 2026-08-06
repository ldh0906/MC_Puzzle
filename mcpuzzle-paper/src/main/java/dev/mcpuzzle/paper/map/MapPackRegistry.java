package dev.mcpuzzle.paper.map;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically replaces the visible map only after a full parse and validation succeeds. */
public final class MapPackRegistry {
    private final JsoncMapPackLoader loader;
    private final Path source;
    private final AtomicReference<MapPack> current = new AtomicReference<>();

    public MapPackRegistry(JsoncMapPackLoader loader, Path source) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.source = Objects.requireNonNull(source, "source");
    }

    public MapPack reload() throws MapPackLoadException {
        MapPack replacement = loader.load(source);
        current.set(replacement);
        return replacement;
    }

    public Optional<MapPack> current() {
        return Optional.ofNullable(current.get());
    }

    public Path source() {
        return source;
    }
}
