package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class WorldInstanceFileStore {
    static final String MARKER_FILE = ".mcpuzzle-instance.properties";
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of("playerdata", "stats", "advancements");
    private static final Set<String> EXCLUDED_FILES = Set.of("uid.dat", "session.lock");

    private final Path templatesRoot;
    private final Path worldContainer;
    private final Clock clock;

    WorldInstanceFileStore(Path templatesRoot, Path worldContainer, Clock clock) {
        this.templatesRoot = absolute(templatesRoot);
        this.worldContainer = absolute(worldContainer);
        this.clock = clock;
    }

    String worldName(SessionId sessionId) {
        return "mcpuzzle_" + sessionId.value().toString().replace("-", "");
    }

    Path provision(SessionId sessionId, String mazeId, MapVersion mapVersion, PartyRoster roster) throws IOException {
        validateSegment(mazeId, "mazeId");
        validateSegment(mapVersion.value(), "mapVersion");
        Files.createDirectories(templatesRoot);
        Files.createDirectories(worldContainer);
        Path realTemplatesRoot = templatesRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path realWorldContainer = worldContainer.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path template = confined(realTemplatesRoot, realTemplatesRoot.resolve(mazeId).resolve(mapVersion.value()))
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!template.startsWith(realTemplatesRoot) || !Files.isDirectory(template, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World template does not exist: " + mazeId + "/" + mapVersion);
        }
        String worldName = worldName(sessionId);
        Path target = confined(realWorldContainer, realWorldContainer.resolve(worldName));
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Instance folder already exists: " + target);
        }
        try {
            copyTemplate(template, target);
            writeMarker(target, new InstanceMarker(sessionId, mazeId, mapVersion, roster, clock.instant()));
            return target;
        } catch (Throwable failure) {
            deleteTreeInternal(target);
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Failed to provision world instance", failure);
        }
    }

    void markGenerated(SessionId sessionId, String mazeId, MapVersion mapVersion, PartyRoster roster) throws IOException {
        validateSegment(mazeId, "mazeId");
        validateSegment(mapVersion.value(), "mapVersion");
        Files.createDirectories(worldContainer);
        Path realWorldContainer = worldContainer.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path target = confined(realWorldContainer, realWorldContainer.resolve(worldName(sessionId)));
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IOException("Generated instance folder is missing or unsafe: " + target);
        }
        Path marker = target.resolve(MARKER_FILE);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generated instance is already marked: " + target);
        }
        writeMarker(target, new InstanceMarker(sessionId, mazeId, mapVersion, roster, clock.instant()));
    }

    void deleteMarkedInstance(SessionId sessionId, String worldName) throws IOException {
        validateWorldName(sessionId, worldName);
        Path realContainer = worldContainer.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path target = confined(realContainer, realContainer.resolve(worldName));
        InstanceMarker marker = readMarker(target);
        if (!marker.sessionId().equals(sessionId)) {
            throw new IOException("Instance marker belongs to another session: " + target);
        }
        deleteTreeInternal(target);
    }

    void deleteFailedGeneratedInstance(SessionId sessionId, String worldName) throws IOException {
        validateWorldName(sessionId, worldName);
        Path realContainer = worldContainer.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path target = confined(realContainer, realContainer.resolve(worldName));
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Failed generated instance path is unsafe: " + target);
        }
        Path marker = target.resolve(MARKER_FILE);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            InstanceMarker parsed = readMarker(target);
            if (!parsed.sessionId().equals(sessionId)) throw new IOException("Generated marker owner mismatch");
        }
        deleteTreeInternal(target);
    }

    List<OrphanedWorldInstance> discoverOrphans(Set<String> activeWorldNames) throws IOException {
        if (!Files.isDirectory(worldContainer, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        Path realContainer = worldContainer.toRealPath(LinkOption.NOFOLLOW_LINKS);
        List<OrphanedWorldInstance> orphans = new ArrayList<>();
        try (var entries = Files.list(realContainer)) {
            for (Path entry : entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                String worldName = entry.getFileName().toString();
                if (!worldName.startsWith("mcpuzzle_") || activeWorldNames.contains(worldName)) {
                    continue;
                }
                try {
                    InstanceMarker marker = readMarker(entry);
                    if (worldName(sessionId(marker)).equals(worldName)) {
                        orphans.add(new OrphanedWorldInstance(marker.sessionId(), worldName, entry, marker.createdAt()));
                    }
                } catch (IllegalArgumentException | IOException ignored) {
                    // Unknown folders and corrupt markers are deliberately never treated as deletable orphans.
                }
            }
        }
        return List.copyOf(orphans);
    }

    private SessionId sessionId(InstanceMarker marker) {
        return marker.sessionId();
    }

    private void validateWorldName(SessionId sessionId, String worldName) throws IOException {
        if (!worldName(sessionId).equals(worldName)) {
            throw new IOException("Unexpected instance name for session " + sessionId);
        }
    }

    private void copyTemplate(Path template, Path target) throws IOException {
        Files.walkFileTree(template, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symbolic links are not allowed in templates: " + directory);
                }
                Path relative = template.relativize(directory);
                if (isExcluded(relative, true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symbolic links are not allowed in templates: " + file);
                }
                Path relative = template.relativize(file);
                if (!isExcluded(relative, false)) {
                    Files.copy(file, target.resolve(relative), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isExcluded(Path relative, boolean directory) {
        if (relative.getNameCount() == 0) {
            return false;
        }
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        return directory ? EXCLUDED_DIRECTORIES.contains(name) : EXCLUDED_FILES.contains(name);
    }

    private void writeMarker(Path target, InstanceMarker marker) throws IOException {
        Path markerPath = confined(target, target.resolve(MARKER_FILE));
        try (OutputStream output = Files.newOutputStream(markerPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            marker.toProperties().store(output, "MCPuzzle temporary instance; do not edit");
        }
    }

    private InstanceMarker readMarker(Path target) throws IOException {
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IOException("Instance folder is missing or unsafe: " + target);
        }
        Path markerPath = confined(target, target.resolve(MARKER_FILE));
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(markerPath, StandardOpenOption.READ)) {
            properties.load(input);
        }
        return InstanceMarker.fromProperties(properties);
    }

    private void deleteTreeInternal(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void validateSegment(String value, String label) {
        if (value == null || !SAFE_SEGMENT.matcher(value).matches() || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException(label + " contains unsafe path characters");
        }
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path confined(Path root, Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())) {
            throw new IOException("Path escapes configured root: " + candidate);
        }
        return normalized;
    }
}
