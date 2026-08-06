package dev.mcpuzzle.paper.isolation;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class PlayerSnapshotCodec {
    byte[] encode(PlayerSnapshot snapshot) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            snapshot.writeTo(output);
        }
        return bytes.toByteArray();
    }

    PlayerSnapshot decode(byte[] bytes) throws IOException {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            PlayerSnapshot snapshot = PlayerSnapshot.readFrom(input);
            if (input.read() != -1) {
                throw new IOException("Trailing player snapshot data");
            }
            return snapshot;
        } catch (ClassNotFoundException failure) {
            throw new IOException("Player snapshot contains an unavailable Bukkit type", failure);
        }
    }
}
