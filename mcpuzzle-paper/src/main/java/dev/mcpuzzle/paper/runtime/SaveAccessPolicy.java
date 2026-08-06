package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.SaveGame;

import java.util.Objects;
import java.util.UUID;

public final class SaveAccessPolicy {
    public boolean canManage(UUID actorId, boolean operator, SaveGame save) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(save, "save");
        return operator || save.slot().ownerId().equals(actorId)
                || save.snapshot().roster().leaderId().equals(actorId);
    }
}
