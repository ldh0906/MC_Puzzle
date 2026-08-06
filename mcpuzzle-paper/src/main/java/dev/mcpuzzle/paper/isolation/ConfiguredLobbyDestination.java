package dev.mcpuzzle.paper.isolation;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

public record ConfiguredLobbyDestination(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) implements LobbyDestinationResolver {
    public ConfiguredLobbyDestination {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Lobby coordinates must be finite");
        }
    }

    @Override
    public Optional<Location> resolve(Server server) {
        World world = server.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(new Location(world, x, y, z, yaw, pitch));
    }
}
