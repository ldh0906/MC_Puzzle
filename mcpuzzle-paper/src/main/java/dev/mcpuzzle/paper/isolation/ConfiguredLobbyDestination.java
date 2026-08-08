package dev.mcpuzzle.paper.isolation;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

public record ConfiguredLobbyDestination(
        String worldName
) implements LobbyDestinationResolver {
    public ConfiguredLobbyDestination {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
    }

    @Override
    public Optional<Location> resolve(Server server) {
        World world = server.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(world.getSpawnLocation());
    }
}
