package dev.mcpuzzle.core.mechanic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Toggle-set input with explicit submit and soft reset on a wrong pattern. */
public final class ToggleInputMechanic extends AbstractRoomMechanic {
    private final Set<String> expectedActive;
    private final Set<String> knownControls;
    private final int maxSelections;
    private final Set<String> selected = new LinkedHashSet<>();

    public ToggleInputMechanic(MechanicId id, RoomAttemptId attempt, Collection<String> knownControls,
                               Collection<String> expectedActive, int maxSelections) {
        super(id, MechanicType.TOGGLE_INPUT, attempt);
        this.knownControls = validatedSet(knownControls, "knownControls");
        this.expectedActive = validatedSet(expectedActive, "expectedActive");
        if (!this.knownControls.containsAll(this.expectedActive)) {
            throw new IllegalArgumentException("Expected toggles must be known controls");
        }
        if (maxSelections < this.expectedActive.size() || maxSelections > this.knownControls.size()) {
            throw new IllegalArgumentException("maxSelections must fit expected and known controls");
        }
        this.maxSelections = maxSelections;
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (event instanceof ClearPressed) {
            selected.clear();
            return progressed("toggle_input.cleared");
        }
        if (event instanceof SubmitPressed) {
            if (selected.equals(expectedActive)) return complete("toggle_input.completed");
            selected.clear();
            return noChange("toggle_input.wrong_reset");
        }
        if (!(event instanceof TogglePressed pressed) || !knownControls.contains(pressed.controlId())) {
            return noChange("toggle_input.unknown_control");
        }
        if (!selected.remove(pressed.controlId())) {
            if (selected.size() >= maxSelections) {
                selected.clear();
                return noChange("toggle_input.selection_limit_reset");
            }
            selected.add(pressed.controlId());
        }
        return progressed("toggle_input.changed");
    }

    @Override
    protected void onReset() { selected.clear(); }

    public synchronized Set<String> selected() { return Set.copyOf(selected); }

    private static Set<String> validatedSet(Collection<String> values, String name) {
        Objects.requireNonNull(values, name);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " contains a blank id");
            if (!result.add(value)) throw new IllegalArgumentException(name + " contains duplicate " + value);
        }
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return Set.copyOf(result);
    }

    public record TogglePressed(String controlId) implements MechanicEvent {
        public TogglePressed { Objects.requireNonNull(controlId, "controlId"); }
    }
    public record SubmitPressed() implements MechanicEvent { }
    public record ClearPressed() implements MechanicEvent { }
}
