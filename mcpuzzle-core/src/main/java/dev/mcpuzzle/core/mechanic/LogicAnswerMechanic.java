package dev.mcpuzzle.core.mechanic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A text-answer terminal whose accepted aliases are compared after Unicode-safe normalization. */
public final class LogicAnswerMechanic extends AbstractRoomMechanic {
    private final Set<String> acceptedAnswers;

    public LogicAnswerMechanic(MechanicId id, RoomAttemptId attempt, Collection<String> acceptedAnswers) {
        super(id, MechanicType.LOGIC_ANSWER, attempt);
        Objects.requireNonNull(acceptedAnswers, "acceptedAnswers");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String answer : acceptedAnswers) {
            String value = LogicAnswerNormalizer.normalize(answer);
            if (value.isEmpty()) throw new IllegalArgumentException("Logic answer must contain a letter or digit");
            normalized.add(value);
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("At least one logic answer is required");
        this.acceptedAnswers = Set.copyOf(normalized);
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof AnswerSubmitted submitted)) {
            return noChange("logic_answer.unsupported_event");
        }
        return accepts(submitted.answer())
                ? complete("logic_answer.correct")
                : noChange("logic_answer.incorrect");
    }

    @Override
    protected void onReset() {
        // The accepted answer is immutable; no transient mechanic state needs resetting.
    }

    public boolean accepts(String answer) {
        return acceptedAnswers.contains(LogicAnswerNormalizer.normalize(answer));
    }

    public Set<String> acceptedAnswers() {
        return acceptedAnswers;
    }

    public record AnswerSubmitted(String answer) implements MechanicEvent {
        public AnswerSubmitted {
            Objects.requireNonNull(answer, "answer");
        }
    }
}
