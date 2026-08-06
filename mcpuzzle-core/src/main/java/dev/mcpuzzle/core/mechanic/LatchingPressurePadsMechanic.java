package dev.mcpuzzle.core.mechanic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class LatchingPressurePadsMechanic extends AbstractRoomMechanic {
    private final Set<String> requiredPads;
    private final Set<String> latchedPads = new LinkedHashSet<>();

    public LatchingPressurePadsMechanic(
            MechanicId id,
            RoomAttemptId attempt,
            Collection<String> requiredPads
    ) {
        super(id, MechanicType.LATCHING_PRESSURE_PADS, attempt);
        this.requiredPads = normalizedIds(requiredPads, "pressure pads");
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof PadPressed pressed) || !requiredPads.contains(pressed.padId())) {
            return noChange("pressure_pad.unknown");
        }
        if (!latchedPads.add(pressed.padId())) {
            return noChange("pressure_pad.already_latched");
        }
        return latchedPads.containsAll(requiredPads)
                ? complete("pressure_pad.all_latched")
                : progressed("pressure_pad.latched");
    }

    @Override
    protected void onReset() {
        latchedPads.clear();
    }

    public synchronized Set<String> latchedPads() {
        return Set.copyOf(latchedPads);
    }

    public record PadPressed(String padId) implements MechanicEvent {
        public PadPressed {
            padId = requireId(padId, "padId");
        }
    }

    private static Set<String> normalizedIds(Collection<String> values, String name) {
        Objects.requireNonNull(values, name);
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (!result.add(requireId(value, name))) {
                throw new IllegalArgumentException(name + " must be unique");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Set.copyOf(result);
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
