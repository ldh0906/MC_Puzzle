package dev.mcpuzzle.paper.isolation;

import org.bukkit.Location;
import org.bukkit.Server;

import java.util.Optional;

@FunctionalInterface
public interface LobbyDestinationResolver {
    Optional<Location> resolve(Server server);
}
