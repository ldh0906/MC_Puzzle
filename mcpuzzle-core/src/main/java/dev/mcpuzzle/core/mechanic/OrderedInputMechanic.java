package dev.mcpuzzle.core.mechanic;

import java.util.List;
import java.util.Objects;

/** Ordered environmental input. A wrong control clears only the current input buffer. */
public final class OrderedInputMechanic extends AbstractRoomMechanic {
    private final List<String> expectedControls;
    private int cursor;

    public OrderedInputMechanic(MechanicId id, RoomAttemptId attempt, List<String> expectedControls) {
        super(id, MechanicType.ORDERED_INPUT, attempt);
        Objects.requireNonNull(expectedControls, "expectedControls");
        if (expectedControls.isEmpty()) throw new IllegalArgumentException("Ordered input requires at least one step");
        this.expectedControls = expectedControls.stream().map(value -> {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Expected control id must not be blank");
            return value;
        }).toList();
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (event instanceof ClearPressed) {
            cursor = 0;
            return progressed("ordered_input.cleared");
        }
        if (!(event instanceof ControlEntered entered)) return noChange("ordered_input.unsupported_event");
        if (!expectedControls.get(cursor).equals(entered.controlId())) {
            cursor = 0;
            return noChange("ordered_input.wrong_reset");
        }
        cursor++;
        return cursor == expectedControls.size()
                ? complete("ordered_input.completed")
                : progressed("ordered_input.progressed");
    }

    @Override
    protected void onReset() {
        cursor = 0;
    }

    public synchronized int cursor() { return cursor; }

    public List<String> expectedControls() { return expectedControls; }

    public record ControlEntered(String controlId) implements MechanicEvent {
        public ControlEntered { Objects.requireNonNull(controlId, "controlId"); }
    }

    public record ClearPressed() implements MechanicEvent { }
}
