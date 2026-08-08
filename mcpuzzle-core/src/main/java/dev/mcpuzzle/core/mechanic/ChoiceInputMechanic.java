package dev.mcpuzzle.core.mechanic;

import java.util.Objects;

/** Single environmental choice whose wrong options do not fail the room. */
public final class ChoiceInputMechanic extends AbstractRoomMechanic {
    private final String correctControl;

    public ChoiceInputMechanic(MechanicId id, RoomAttemptId attempt, String correctControl) {
        super(id, MechanicType.CHOICE_INPUT, attempt);
        this.correctControl = Objects.requireNonNull(correctControl, "correctControl");
        if (correctControl.isBlank()) throw new IllegalArgumentException("Correct control id must not be blank");
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof ChoiceSelected selected)) return noChange("choice_input.unsupported_event");
        return correctControl.equals(selected.controlId())
                ? complete("choice_input.completed")
                : noChange("choice_input.wrong_reset");
    }

    @Override
    protected void onReset() { }

    public record ChoiceSelected(String controlId) implements MechanicEvent {
        public ChoiceSelected { Objects.requireNonNull(controlId, "controlId"); }
    }
}
