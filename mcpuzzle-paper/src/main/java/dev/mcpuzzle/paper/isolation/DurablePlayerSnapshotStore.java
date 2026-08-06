package dev.mcpuzzle.paper.isolation;

import dev.mcpuzzle.core.domain.SessionId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic, checksummed storage for Bukkit-local player snapshots. No Bukkit types cross this boundary.
 */
public final class DurablePlayerSnapshotStore {
    private static final int MAGIC = 0x4D435053; // MCPS
    private static final int ENVELOPE_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    private final Path pendingRoot;
    private final Path quarantineRoot;

    public DurablePlayerSnapshotStore(Path dataRoot) {
        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        this.pendingRoot = normalizedRoot.resolve("pending");
        this.quarantineRoot = normalizedRoot.resolve("quarantine");
    }

    public void saveBatch(Map<SnapshotKey, byte[]> snapshots) throws IOException {
        if (snapshots.isEmpty()) {
            return;
        }
        ensureDirectory(pendingRoot, "pending snapshot root");
        List<Path> targets = new ArrayList<>();
        for (SnapshotKey key : snapshots.keySet()) {
            Path target = pathFor(key);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("A durable snapshot already exists for " + key.playerId());
            }
            targets.add(target);
        }

        List<Path> committed = new ArrayList<>();
        List<Path> temporaryFiles = new ArrayList<>();
        try {
            int index = 0;
            for (Map.Entry<SnapshotKey, byte[]> entry : snapshots.entrySet()) {
                Path target = targets.get(index++);
                ensureDirectory(target.getParent(), "snapshot session directory");
                Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
                temporaryFiles.add(temporary);
                Files.write(temporary, envelope(entry.getValue()), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                atomicMove(temporary, target);
                temporaryFiles.remove(temporary);
                committed.add(target);
            }
        } catch (Throwable failure) {
            IOException cleanupFailure = null;
            for (Path path : temporaryFiles) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    cleanupFailure = append(cleanupFailure, exception);
                }
            }
            for (Path path : committed) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    cleanupFailure = append(cleanupFailure, exception);
                }
            }
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Could not persist player snapshot batch", failure);
        }
    }

    public SnapshotLoadResult loadAll() throws IOException {
        if (!Files.exists(pendingRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new SnapshotLoadResult(Map.of(), List.of());
        }
        ensureDirectory(pendingRoot, "pending snapshot root");
        Map<SnapshotKey, byte[]> loaded = new LinkedHashMap<>();
        List<SnapshotCorruption> corruptions = new ArrayList<>();
        try (var sessions = Files.list(pendingRoot)) {
            for (Path sessionDirectory : sessions.toList()) {
                if (!Files.isDirectory(sessionDirectory, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(sessionDirectory)) {
                    corruptions.add(quarantineUnknown(sessionDirectory, "Unexpected entry in snapshot root"));
                    continue;
                }
                SessionId sessionId;
                try {
                    sessionId = new SessionId(UUID.fromString(sessionDirectory.getFileName().toString()));
                } catch (IllegalArgumentException invalidSession) {
                    corruptions.add(quarantineUnknown(sessionDirectory, "Invalid session directory name"));
                    continue;
                }
                try (var files = Files.list(sessionDirectory)) {
                    for (Path file : files.toList()) {
                        Optional<UUID> playerId = playerIdFrom(file);
                        if (playerId.isEmpty() || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            corruptions.add(quarantineUnknown(file, "Invalid snapshot file name or type"));
                            continue;
                        }
                        SnapshotKey key = new SnapshotKey(sessionId, playerId.get());
                        try {
                            loaded.put(key, payload(Files.readAllBytes(file)));
                        } catch (Exception corruptEnvelope) {
                            Path quarantined = quarantinePath(file);
                            corruptions.add(new SnapshotCorruption(playerId, quarantined,
                                    "Snapshot envelope is corrupt: " + corruptEnvelope.getMessage()));
                        }
                    }
                }
            }
        }
        return new SnapshotLoadResult(loaded, corruptions);
    }

    public void deleteBatch(Iterable<SnapshotKey> keys) throws IOException {
        if (Files.exists(pendingRoot, LinkOption.NOFOLLOW_LINKS)) {
            ensureDirectory(pendingRoot, "pending snapshot root");
        }
        IOException aggregate = null;
        for (SnapshotKey key : keys) {
            Path target = pathFor(key);
            try {
                Files.deleteIfExists(target);
                deleteIfEmpty(target.getParent());
            } catch (IOException failure) {
                aggregate = append(aggregate, failure);
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    public SnapshotCorruption quarantine(SnapshotKey key, String reason) throws IOException {
        Path source = pathFor(key);
        Path quarantined = quarantinePath(source);
        return new SnapshotCorruption(Optional.of(key.playerId()), quarantined, reason);
    }

    Path pathFor(SnapshotKey key) throws IOException {
        Path target = pendingRoot.resolve(key.sessionId().toString()).resolve(key.playerId() + ".snapshot")
                .toAbsolutePath().normalize();
        if (!target.startsWith(pendingRoot.toAbsolutePath().normalize())) {
            throw new IOException("Snapshot path escaped pending root");
        }
        return target;
    }

    private SnapshotCorruption quarantineUnknown(Path source, String reason) throws IOException {
        Optional<UUID> playerId = playerIdFrom(source);
        return new SnapshotCorruption(playerId, quarantinePath(source), reason);
    }

    private Path quarantinePath(Path source) throws IOException {
        ensureDirectory(quarantineRoot, "snapshot quarantine root");
        Path destination = quarantineRoot.resolve(UUID.randomUUID() + "-" + source.getFileName())
                .toAbsolutePath().normalize();
        if (!destination.startsWith(quarantineRoot.toAbsolutePath().normalize())) {
            throw new IOException("Quarantine path escaped root");
        }
        atomicMove(source, destination);
        return destination;
    }

    private Optional<UUID> playerIdFrom(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".snapshot")) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(name.substring(0, name.length() - ".snapshot".length())));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private byte[] envelope(byte[] payload) throws IOException {
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Snapshot payload exceeds maximum size");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(ENVELOPE_VERSION);
            output.writeInt(payload.length);
            output.write(sha256(payload));
            output.write(payload);
        }
        return bytes.toByteArray();
    }

    private byte[] payload(byte[] envelope) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(envelope))) {
            if (input.readInt() != MAGIC || input.readInt() != ENVELOPE_VERSION) {
                throw new IOException("Unknown snapshot format");
            }
            int length = input.readInt();
            if (length < 0 || length > MAX_PAYLOAD_BYTES) {
                throw new IOException("Invalid snapshot payload length");
            }
            byte[] expectedHash = input.readNBytes(32);
            byte[] payload = input.readNBytes(length);
            if (expectedHash.length != 32 || payload.length != length || input.read() != -1) {
                throw new IOException("Truncated or trailing snapshot data");
            }
            if (!MessageDigest.isEqual(expectedHash, sha256(payload))) {
                throw new IOException("Snapshot checksum mismatch");
            }
            return payload;
        }
    }

    private byte[] sha256(byte[] payload) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private void deleteIfEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private void ensureDirectory(Path directory, String label) throws IOException {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a safe directory: " + directory);
        }
    }

    private IOException append(IOException aggregate, IOException failure) {
        if (aggregate == null) {
            return failure;
        }
        aggregate.addSuppressed(failure);
        return aggregate;
    }
}
