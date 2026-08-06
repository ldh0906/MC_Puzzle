package dev.mcpuzzle.core.mechanic;

import dev.mcpuzzle.core.domain.Party;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DynamicPartyPadsAndTargetMechanic extends AbstractRoomMechanic {
    private final int activeRosterSize;
    private final Set<Integer> latchedRosterPads = new LinkedHashSet<>();
    private boolean targetDestroyed;

    public DynamicPartyPadsAndTargetMechanic(
            MechanicId id,
            RoomAttemptId attempt,
            int activeRosterSize
    ) {
        super(id, MechanicType.DYNAMIC_PARTY_PADS_AND_TARGET, attempt);
        if (activeRosterSize < Party.MIN_SIZE || activeRosterSize > Party.MAX_SIZE) {
            throw new IllegalArgumentException("Dynamic party mechanic requires 1 to 4 roster pads");
        }
        this.activeRosterSize = activeRosterSize;
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (event instanceof RosterPadPressed pad) {
            if (pad.rosterIndex() < 0 || pad.rosterIndex() >= activeRosterSize) {
                return noChange("dynamic_party_pad.inactive_index");
            }
            if (!latchedRosterPads.add(pad.rosterIndex())) {
                return noChange("dynamic_party_pad.already_latched");
            }
            return completedIfReady("dynamic_party_pad.latched");
        }
        if (event instanceof TargetDestroyed) {
            if (targetDestroyed) {
                return noChange("dynamic_party_target.already_destroyed");
            }
            targetDestroyed = true;
            return completedIfReady("dynamic_party_target.destroyed");
        }
        return noChange("dynamic_party.unsupported_event");
    }

    @Override
    protected void onReset() {
        latchedRosterPads.clear();
        targetDestroyed = false;
    }

    public synchronized Set<Integer> latchedRosterPads() {
        return Set.copyOf(latchedRosterPads);
    }

    public synchronized boolean targetDestroyed() {
        return targetDestroyed;
    }

    public int activeRosterSize() {
        return activeRosterSize;
    }

    private MechanicOutcome completedIfReady(String progressKey) {
        return targetDestroyed && latchedRosterPads.size() == activeRosterSize
                ? complete("dynamic_party.completed")
                : progressed(progressKey);
    }

    public record RosterPadPressed(int rosterIndex) implements MechanicEvent {
    }

    public record TargetDestroyed() implements MechanicEvent {
    }
}
