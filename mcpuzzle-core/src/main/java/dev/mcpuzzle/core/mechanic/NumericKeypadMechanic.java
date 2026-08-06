package dev.mcpuzzle.core.mechanic;

import java.util.Objects;

public final class NumericKeypadMechanic extends AbstractRoomMechanic {
    private final String answer;
    private final int maxDigits;
    private final StringBuilder buffer = new StringBuilder();

    public NumericKeypadMechanic(
            MechanicId id,
            RoomAttemptId attempt,
            String answer,
            int maxDigits
    ) {
        super(id, MechanicType.NUMERIC_KEYPAD, attempt);
        Objects.requireNonNull(answer, "answer");
        if (!answer.matches("[0-9]+")) {
            throw new IllegalArgumentException("Numeric keypad answer must contain digits only");
        }
        if (maxDigits < answer.length()) {
            throw new IllegalArgumentException("Maximum digits cannot be shorter than the answer");
        }
        this.answer = answer;
        this.maxDigits = maxDigits;
    }

    public NumericKeypadMechanic(MechanicId id, RoomAttemptId attempt, String answer) {
        this(id, attempt, answer, Math.max(12, Objects.requireNonNull(answer, "answer").length()));
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (event instanceof DigitPressed digit) {
            if (buffer.length() >= maxDigits) {
                return noChange("numeric_keypad.buffer_full");
            }
            buffer.append(digit.digit());
            return progressed("numeric_keypad.digit_added");
        }
        if (event instanceof ClearPressed) {
            if (buffer.isEmpty()) {
                return noChange("numeric_keypad.already_clear");
            }
            buffer.setLength(0);
            return progressed("numeric_keypad.cleared");
        }
        if (event instanceof SubmitPressed) {
            if (buffer.toString().equals(answer)) {
                return complete("numeric_keypad.correct");
            }
            buffer.setLength(0);
            return fail("numeric_keypad.wrong_answer");
        }
        return noChange("numeric_keypad.unsupported_event");
    }

    @Override
    protected void onReset() {
        buffer.setLength(0);
    }

    public synchronized String buffer() {
        return buffer.toString();
    }

    public record DigitPressed(int digit) implements MechanicEvent {
        public DigitPressed {
            if (digit < 0 || digit > 9) {
                throw new IllegalArgumentException("Keypad digit must be between 0 and 9");
            }
        }
    }

    public record ClearPressed() implements MechanicEvent {
    }

    public record SubmitPressed() implements MechanicEvent {
    }
}
